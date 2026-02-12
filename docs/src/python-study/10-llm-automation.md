# 10. LLM 驱动的自动化脚本（批处理文档）

本章目标：把“跑得起来的模型”变成“能干活的工具”。

> 原理补充：LLM 是不确定性的。想把它用于自动化，必须让输出“可校验”：JSON + schema 约束 + 校验失败就重试/修复，并把失败样本落盘。

## 1) 结构化输出是关键
- 约束输出为 JSON
- 失败处理：重试、校验、最小修复

## 2) 适合 LLM 的自动化任务
- 提纲生成/标题优化
- 术语表抽取
- 文章摘要
- Q&A 生成（用于复习）

## 小项目：批量为文章生成提纲
- 输入：`docs/src/llm-study/*.md` 或你指定目录
- 输出：每篇文章生成一个 `outline.json`

> 下一章会做最小 RAG，让模型“先检索再回答”。

## 短平快：一个可运行的批处理脚本
下面脚本会：
1) 遍历目录下所有 `.md`
2) 调用 Ollama（HTTP）
3) 强制输出 JSON（失败则让模型修复 JSON）
4) 为每篇文章生成 `*.outline.json`

依赖：只需要 `requests`。
```bash
python -m pip install requests
```

脚本示例（保存为 `outline_batch.py`）：
```python
import argparse
import json
import re
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


def ensure_json(model: str, prompt: str, *, max_attempts: int = 3) -> dict:
	last_text = ""
	for _ in range(max_attempts):
		text = chat_once(model, prompt)
		last_text = text
		try:
			return json.loads(text)
		except json.JSONDecodeError:
			prompt = (
				"上一次输出不是合法 JSON。\n"
				"请你只返回修复后的 JSON（不要解释、不要 Markdown、不要代码块）。\n"
				f"原输出：{text}"
			)
	raise ValueError(f"failed to get valid JSON, last output: {last_text}")


def strip_code_fences(md: str) -> str:
	return re.sub(r"```.*?```", "", md, flags=re.S)


def build_prompt(md_text: str) -> str:
	md_text = strip_code_fences(md_text)
	md_text = md_text[:8000]  # CPU 场景不要喂太长，后面做 RAG 会更稳
	return (
		"你是一个只输出 JSON 的程序。\n"
		"请严格输出一个 JSON 对象，禁止输出任何解释、Markdown、代码块。\n"
		"JSON 结构：\n"
		"{\n"
		"  \"title\": string,\n"
		"  \"outline\": string[]\n"
		"}\n"
		"要求：outline 生成 6-12 条，尽量短句。\n\n"
		"下面是文章内容：\n"
		f"{md_text}"
	)


def main() -> int:
	parser = argparse.ArgumentParser(prog="outline-batch")
	parser.add_argument("root", type=Path, help="scan markdown under this directory")
	parser.add_argument("--model", default="llama3.2:3b")
	parser.add_argument("--out-dir", type=Path, default=None, help="write json files here; default next to md")
	args = parser.parse_args()

	root: Path = args.root
	out_dir: Path | None = args.out_dir

	md_files = sorted(root.rglob("*.md"))
	if not md_files:
		print(f"no markdown found under: {root}")
		return 3

	for p in md_files:
		md = p.read_text(encoding="utf-8")
		prompt = build_prompt(md)
		obj = ensure_json(args.model, prompt)

		# 最小校验
		if not isinstance(obj.get("title"), str) or not isinstance(obj.get("outline"), list):
			raise ValueError(f"invalid schema from model for {p}")

		out_name = p.with_suffix(".outline.json").name
		out_path = (out_dir / out_name) if out_dir else p.with_suffix(".outline.json")
		out_path.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")
		print("wrote:", out_path)

	return 0


if __name__ == "__main__":
	raise SystemExit(main())
```

运行示例：
```bash
# 给 llm-study 批量生成提纲
python outline_batch.py docs/src/llm-study --model llama3.2:3b
```
