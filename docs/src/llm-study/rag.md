# RAG 实战要点：切分、召回、重排、引用

RAG（Retrieval-Augmented Generation）是目前最常见的落地方案：

> 用检索把“可信信息”塞进上下文，让模型基于资料回答。

这一章目标：

- 你能解释 RAG 每一步在解决什么问题
- 你能在本地跑通一个最小 RAG（不用 GPU、RTX 3050 也没压力）
- 你知道上线时最容易翻车的点（以及怎么补救）

---

## 0. 本章需要的环境

你可以选两条路线：

- **路线 A（推荐入门）**：云端 Embedding + 云端/本地 LLM（实现最稳、最省事）
- **路线 B（进阶）**：本地 Embedding（CPU）+ 本地 LLM（Ollama）

RTX 3050 提醒：RAG 的“检索部分”基本是 CPU 活，你的显卡不是瓶颈；真正吃显存的是“本地推理/本地微调”。

### 安装依赖（Python）

在 venv 里安装：

```bash
pip install -U openai numpy requests
```

说明：

- `numpy`：用来做余弦相似度检索（我们先不引入向量数据库，降低门槛）
- `openai`：用于 OpenAI 兼容 API（LLM/Embedding）
- `requests`：如果你走 Ollama 路线，用它调用本地 HTTP

---

## 1. 一个标准 RAG 流程（你必须背下来的版本）

## 一个标准 RAG 流程

1. 文档入库：清洗 → 切分（chunking）→ 向量化（embedding）→ 存储
2. 在线查询：query 改写（可选）→ 向量召回 → 关键词召回（可选）
3. 重排（rerank）：把最相关的片段排到前面
4. 组装上下文：拼接片段 + 元数据 + 引用编号
5. 生成回答：要求“引用来源”，并限制胡编

你可以把 RAG 记成一句话：

> 先把资料找出来（检索），再让模型基于资料回答（生成），并强制给出处（引用）。

---

## 2. 立刻动手：最小 RAG Demo（不依赖向量库）

为了让你真正“跑通”，我们先不引入 Chroma / Milvus / Elasticsearch。

这个 Demo 用 `numpy` 在内存里做：

- 切分：把文本切成 chunks
- 向量化：调用 Embedding 接口得到向量
- 检索：余弦相似度 top-k
- 生成：把 top-k chunks 拼进 prompt，并要求引用编号

### 2.1 准备一份小知识库

新建 `kb.txt`（你可以随便写 20~50 行）：

```text
[doc:company-policy]
退货政策：收到货 7 天内可无理由退货；超过 7 天需质量问题证明。
退款路径：原路返回，到账时间 1-5 个工作日。

[doc:shipping]
发货时间：工作日 48 小时内发出。
物流查询：在订单详情页点击“物流信息”。

[doc:faq]
发票：支持电子发票，默认随订单邮件发送。
```

### 2.2 写一个最小切分器（chunking）

新建 `rag_minimal.py`：

```python
import os
import re
import json
import numpy as np
from openai import OpenAI


def chunk_text(text: str, max_chars: int = 200, overlap: int = 40):
	text = re.sub(r"\n{3,}", "\n\n", text.strip())
	chunks = []
	start = 0
	while start < len(text):
		end = min(len(text), start + max_chars)
		chunk = text[start:end]
		chunks.append(chunk)
		start = max(0, end - overlap)
		if end == len(text):
			break
	return chunks


def cosine_top_k(query_vec: np.ndarray, doc_vecs: np.ndarray, k: int = 4):
	# 归一化后点积 = 余弦相似度
	q = query_vec / (np.linalg.norm(query_vec) + 1e-12)
	d = doc_vecs / (np.linalg.norm(doc_vecs, axis=1, keepdims=True) + 1e-12)
	sims = d @ q
	idx = np.argsort(-sims)[:k]
	return idx, sims[idx]


def embed(client: OpenAI, model: str, texts: list[str]) -> np.ndarray:
	resp = client.embeddings.create(model=model, input=texts)
	vecs = [item.embedding for item in resp.data]
	return np.array(vecs, dtype=np.float32)


def build_context(chunks: list[str], indices, scores) -> str:
	lines = []
	for rank, (i, s) in enumerate(zip(indices, scores), start=1):
		lines.append(f"[C{rank}] score={float(s):.4f}\n{chunks[i].strip()}")
	return "\n\n".join(lines)


def answer_with_citations(client: OpenAI, model: str, question: str, context: str) -> str:
	system = """你是一个严谨的客服助理。
规则：
1) 只基于 <context> 回答，不要编造。
2) 每个结论后面都要用 [C1]/[C2] 这种引用标注来源。
3) 如果资料不足，回答：信息不足，并说明缺什么。
"""
	user = f"""问题：{question}

<context>
{context}
</context>
"""
	resp = client.chat.completions.create(
		model=model,
		temperature=0,
		messages=[
			{"role": "system", "content": system},
			{"role": "user", "content": user},
		],
	)
	return resp.choices[0].message.content


if __name__ == "__main__":
	# OpenAI 兼容配置（按你上一章的方式设置环境变量）
	client = OpenAI(
		api_key=os.getenv("OPENAI_API_KEY"),
		base_url=os.getenv("OPENAI_BASE_URL"),
	)
	chat_model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
	emb_model = os.getenv("OPENAI_EMBED_MODEL", "text-embedding-3-small")

	kb_text = open("kb.txt", "r", encoding="utf-8").read()
	chunks = chunk_text(kb_text, max_chars=200, overlap=40)

	doc_vecs = embed(client, emb_model, chunks)

	question = "我的耳机用了 10 天左耳没声音，还能退货吗？多久到账？"
	q_vec = embed(client, emb_model, [question])[0]

	idx, scores = cosine_top_k(q_vec, doc_vecs, k=4)
	context = build_context(chunks, idx, scores)

	print("=== Retrieved Context ===")
	print(context)
	print("\n=== Answer ===")
	print(answer_with_citations(client, chat_model, question, context))
```

### 2.3 运行 Demo

（PowerShell 示例）先设置环境变量：

```powershell
$env:OPENAI_API_KEY="你的KEY"
$env:OPENAI_BASE_URL="你的BASE_URL"   # 可选
$env:OPENAI_MODEL="你的模型名"         # 可选
$env:OPENAI_EMBED_MODEL="你的向量模型名" # 可选
python .\rag_minimal.py
```

你应该看到两段输出：

- 检索到的 `[C1]...[C4]` 上下文
- 带引用的回答（例如：结论后面有 `[C1]`）

---

## 3. 这套最小 Demo 对应真实系统的哪里

- `chunk_text`：对应“切分策略”
- `embed`：对应“Embedding 服务”
- `cosine_top_k`：对应“向量库召回”（我们用 numpy 代替了向量库）
- `build_context`：对应“上下文拼接 + 引用编号”
- `answer_with_citations`：对应“生成 + 引用约束”

当你把它换成真实工程组件时，只需要替换：

- 存储：内存数组 → 向量数据库
- 检索：numpy top-k → 向量库 top-k（可再加 rerank）

---

## 4. 切分（Chunking）怎么做：从能用到好用

## 切分（Chunking）怎么做

- 以 **语义边界** 切分：标题、段落、列表优先
- chunk 不要太大：太大影响召回；太小会丢上下文
- 增加 overlap：减少跨段信息断裂

更具体的建议（非常实用）：

- **优先按结构切**：Markdown 标题、段落、列表是天然边界
- **chunk 大小先从 200~500 字符起步**：太大召回不准，太小丢上下文
- **overlap 先从 10%~20% 起步**：减少跨边界信息断裂
- **chunk 带元数据**：来源文件、标题路径、更新时间，用于引用与审计

---

## 5. 召回的坑（以及你能立刻做的补救）

## 召回的坑

- Query 和文档风格不一致：需要 query rewrite 或关键词召回补强
- 只用向量召回会漏“关键字强相关”内容：可做混合检索

你可以先做两个低成本增强：

- **Query Rewrite（轻量）**：让模型把用户问题改写成“更像文档语言”的检索 query
- **混合检索**：向量召回 top_k + 关键词召回 top_k，合并后再去重

---

## 6. 重排的重要性（什么时候该上）

## 重排的重要性

重排往往比“换更贵的向量库”更有效：

- 先召回 50～200 条
- 再用 reranker 选 top_k（例如 5～15）

经验：

- 你觉得“检索到了但没选对”时，上 rerank 往往立竿见影
- 你觉得“根本检索不到”时，先回头看切分与 embedding 模型

---

## 7. 引用与可验证：RAG 能不能上线的分水岭

## 引用与可验证

- 每个 chunk 带上来源信息（文件名、章节、URL）
- 生成时强制：回答中的结论要能对应引用编号

建议你把引用做到“可点击/可回溯”：

- chunk 元数据里存：文件名、章节标题、原文片段范围
- 输出引用时带上 chunk id
- 日志里记录：检索 top_k、重排结果、最终上下文

---

## 8. 最小可用版本（MVP）与进阶路线

## 最小可用版本（MVP）

- 只做：切分 + 向量召回 + 引用
- 先把“回答有证据”跑通，再做 query rewrite / rerank

我建议你的迭代顺序：

1) 切分 + 向量召回 + 引用（本章已覆盖）
2) 加混合检索（关键词补强）
3) 加重排（rerank）
4) 加评测集与可观测性（下一章“评测”会讲）

---

## 本章验收

满足下面两条就算通过：

- 你能跑通 `rag_minimal.py`，并看到回答里带 `[C1]` 这种引用
- 你能解释：如果用户问“10 天能不能退货”，为什么模型必须引用到具体条款
