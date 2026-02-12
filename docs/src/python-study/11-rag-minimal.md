# 11. 最小 RAG：让模型检索你的笔记库

本章目标：实现一个最小可用的 RAG：
1) 扫描笔记库（`docs/src`）
2) 切分文本
3) 建索引（先从“词法检索”开始）
4) 检索相关片段（top-k）
5) 拼接上下文 + 提示词，交给 Ollama 回答

> 原理补充：RAG 的核心不是“模型更大”，而是“检索更准”。CPU 场景下，先把切分与检索做对（少量高相关上下文），比硬堆上下文长度更有效。

## 你需要的现实预期（CPU）
- 不追求大而全，先追求“能检索到对的片段”
- 先用轻量检索（词法/BM25）跑通，再考虑向量检索（embedding）

> 原理补充：严格意义的“向量检索”需要 embedding 模型；但在你的笔记库（大量技术关键词）里，词法检索已经能覆盖很多场景，而且依赖最少、CPU 更友好。

## 短平快：最小 RAG（纯 Python 检索 + Ollama）
下面给一个“能跑通就有用”的最小实现：
- 切分：按长度切 chunk（带重叠）
- 检索：BM25 风格打分（纯 Python）
- 生成：把 top-k chunk 拼接进提示词，调用 Ollama

可选依赖（提升中文分词效果）：
```bash
python -m pip install jieba
```

脚本示例（保存为 `rag_min.py`）：
```python
import argparse
import json
import math
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import requests


def chat_once(model: str, prompt: str) -> str:
	url = "http://localhost:11434/api/chat"
	payload = {
		"model": model,
		"stream": False,
		"messages": [
			{"role": "user", "content": prompt},
		],
	}
	resp = requests.post(url, json=payload, timeout=(3, 600))
	resp.raise_for_status()
	return resp.json()["message"]["content"]


def strip_code_fences(md: str) -> str:
	return re.sub(r"```.*?```", "", md, flags=re.S)


def tokenize(text: str) -> list[str]:
	text = text.lower()
	# 可选：中文用 jieba 分词
	try:
		import jieba  # type: ignore

		tokens = [t.strip() for t in jieba.lcut(text) if t.strip()]
		return tokens
	except Exception:
		# 兜底：英文按单词切，中文会退化为单字（效果一般但能跑通）
		tokens = re.findall(r"[a-z0-9_]+|[\u4e00-\u9fff]", text)
		return tokens


def chunk_text(text: str, *, max_chars: int = 900, overlap: int = 150) -> list[str]:
	text = text.strip()
	if not text:
		return []
	chunks: list[str] = []
	i = 0
	while i < len(text):
		j = min(len(text), i + max_chars)
		chunks.append(text[i:j])
		if j == len(text):
			break
		i = max(0, j - overlap)
	return chunks


@dataclass
class Chunk:
	source: str
	text: str
	tf: Counter
	length: int


class BM25:
	def __init__(self, chunks: list[Chunk], *, k1: float = 1.5, b: float = 0.75):
		self.chunks = chunks
		self.k1 = k1
		self.b = b
		self.avgdl = (sum(c.length for c in chunks) / len(chunks)) if chunks else 1.0

		df: Counter[str] = Counter()
		for c in chunks:
			for term in c.tf.keys():
				df[term] += 1
		self.df = df
		self.N = len(chunks)

	def idf(self, term: str) -> float:
		# 带平滑的 BM25 idf
		n_q = self.df.get(term, 0)
		return math.log(1 + (self.N - n_q + 0.5) / (n_q + 0.5))

	def score(self, query_terms: list[str], chunk: Chunk) -> float:
		score = 0.0
		for t in query_terms:
			f = chunk.tf.get(t, 0)
			if f == 0:
				continue
			idf = self.idf(t)
			denom = f + self.k1 * (1 - self.b + self.b * (chunk.length / self.avgdl))
			score += idf * (f * (self.k1 + 1)) / denom
		return score

	def topk(self, query: str, k: int = 5) -> list[tuple[float, Chunk]]:
		q_terms = tokenize(query)
		scored = [(self.score(q_terms, c), c) for c in self.chunks]
		scored.sort(key=lambda x: x[0], reverse=True)
		return [x for x in scored[:k] if x[0] > 0]


def build_index(content_root: Path) -> BM25:
	chunks: list[Chunk] = []
	for md_path in sorted(content_root.rglob("*.md")):
		md = md_path.read_text(encoding="utf-8")
		md = strip_code_fences(md)
		for idx, part in enumerate(chunk_text(md)):
			terms = tokenize(part)
			tf = Counter(terms)
			chunks.append(
				Chunk(
					source=f"{md_path.relative_to(content_root).as_posix()}#chunk={idx}",
					text=part,
					tf=tf,
					length=len(terms),
				)
			)
	return BM25(chunks)


def build_prompt(question: str, retrieved: list[tuple[float, Chunk]]) -> str:
	ctx_lines = []
	for score, ch in retrieved:
		ctx_lines.append(f"[source: {ch.source} | score: {score:.3f}]\n{ch.text}\n")

	context = "\n---\n".join(ctx_lines)[:12000]

	return (
		"你是一个严谨的技术助理。\n"
		"请优先根据【检索片段】回答；如果片段不包含答案，明确说‘笔记库中未找到’，并给出你建议的下一步检索关键词。\n\n"
		f"问题：{question}\n\n"
		f"检索片段：\n{context}\n"
	)


def main() -> int:
	parser = argparse.ArgumentParser(prog="rag-min")
	parser.add_argument("question")
	parser.add_argument("--root", type=Path, default=Path("docs") / "src")
	parser.add_argument("--model", default="llama3.2:3b")
	parser.add_argument("--k", type=int, default=5)
	parser.add_argument("--out", type=Path, default=Path("rag-answer.json"))
	args = parser.parse_args()

	index = build_index(args.root)
	hits = index.topk(args.question, k=args.k)
	prompt = build_prompt(args.question, hits)
	answer = chat_once(args.model, prompt)

	out = {
		"question": args.question,
		"model": args.model,
		"k": args.k,
		"hits": [{"score": s, "source": c.source} for s, c in hits],
		"answer": answer,
	}
	args.out.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
	print(f"wrote: {args.out}")
	return 0


if __name__ == "__main__":
	raise SystemExit(main())
```

运行示例：
```bash
python rag_min.py "VitePress 的 sidebar 怎么配置？" --root docs/src --model llama3.2:3b --k 5
```

> 原理补充：这个版本是“词法检索 RAG”。你后续想升级到向量检索时，整体流程不变，只是把 `build_index()` / `topk()` 的实现替换成 embedding + ANN 索引。

延伸阅读：你已有的 RAG 笔记在 `/llm-study/rag`。
