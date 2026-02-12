# 09. 用 Python 调用 Ollama（流式输出 + JSON 约束）

本章目标：把 Ollama 变成你脚本里的一个函数：`ask_llm(prompt) -> str/JSON`。

> 原理补充：工程上最关键的是“可控性”。直接用 HTTP 调用 Ollama（而不是依赖不稳定的第三方 SDK）能把输入、输出、超时、重试、流式处理都牢牢掌控。

## 选择方案
- 最稳通用：直接调用 Ollama 的 HTTP 接口（不依赖特定第三方 SDK）
- 也可以用社区 SDK（但版本兼容要自己维护）

> 我将按“HTTP 调用”写，保证可控、依赖最少。

## 0) 前置：确保 Ollama 可用
```bash
curl http://localhost:11434/api/tags
```

Python 依赖：
```bash
python -m pip install requests
```

## 1) 最小调用（非流式）
这个版本最适合“批处理自动化”（一次请求，一次拿到完整文本）。

```python
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


print(chat_once("llama3.2:3b", "用一句话解释什么是 RAG"))
```

> 原理补充：`timeout=(3, 600)` 的含义是：连接最多 3 秒、读取最多 600 秒。CPU 推理可能慢，所以读取超时要放大。

## 2) 流式输出（边生成边打印）
流式适合交互式体验（你能看到模型在“逐字输出”）。

```python
import json

import requests


def chat_stream(model: str, prompt: str) -> str:
	url = "http://localhost:11434/api/chat"
	payload = {
		"model": model,
		"stream": True,
		"messages": [
			{"role": "user", "content": prompt},
		],
	}

	parts: list[str] = []
	with requests.post(url, json=payload, stream=True, timeout=(3, 600)) as resp:
		resp.raise_for_status()
		for line in resp.iter_lines(decode_unicode=True):
			if not line:
				continue
			obj = json.loads(line)
			chunk = obj.get("message", {}).get("content")
			if chunk:
				print(chunk, end="", flush=True)
				parts.append(chunk)
			if obj.get("done") is True:
				break

	print()
	return "".join(parts)


chat_stream("llama3.2:3b", "请用要点解释 Python 的 pathlib 有什么用。")
```

> 原理补充：流式响应通常是一行一个 JSON（NDJSON 风格）。你要做的是“逐行解析 + 追加 content”。

## 3) 让模型严格输出 JSON（并且校验）
这是“把 LLM 变成自动化工具”的关键。

### 3.1 一个通用的 JSON 约束提示词
```python
def json_prompt(schema: str, task: str, content: str) -> str:
	return (
		"你是一个只输出 JSON 的程序。\n"
		"禁止输出任何解释、Markdown、代码块。\n"
		f"请严格输出一个 JSON 对象，满足如下 schema：\n{schema}\n\n"
		f"任务：{task}\n\n"
		f"输入内容：\n{content}"
	)
```

### 3.2 校验失败就让模型修复 JSON（重试 2-3 次）
```python
import json


def ensure_json(model: str, prompt: str, *, max_attempts: int = 3) -> dict:
	last_text = ""
	for _ in range(max_attempts):
		text = chat_once(model, prompt)
		last_text = text
		try:
			obj = json.loads(text)
			if isinstance(obj, dict):
				return obj
			prompt = (
				"上一次输出不是 JSON 对象（dict）。\n"
				"请只返回修复后的 JSON 对象（不要解释、不要 Markdown、不要代码块）。\n"
				f"原输出：{text}"
			)
		except json.JSONDecodeError:
			prompt = (
				"上一次输出不是合法 JSON。\n"
				"请你只返回修复后的 JSON（不要解释、不要 Markdown、不要代码块）。\n"
				f"原输出：{text}"
			)
	raise ValueError(f"failed to get valid JSON, last output: {last_text}")
```

> 原理补充：LLM 的 JSON 输出常见问题：多了代码块、末尾多了注释、或少了引号。用“修复 JSON”的二次提示几乎总能救回来。

## 4) 小练习：总结 Markdown 为 10 条要点（JSON 输出）
目标：读取一个 `.md` 文件，让模型输出严格 JSON：
```json
{
  "title": "...",
  "bullets": ["...", "..."]
}
```

脚本示例（保存为 `summarize_md.py`）：
```python
import argparse
import json
from pathlib import Path


def main() -> int:
	parser = argparse.ArgumentParser(prog="summarize-md")
	parser.add_argument("path", type=Path)
	parser.add_argument("--model", default="llama3.2:3b")
	parser.add_argument("--out", type=Path, default=Path("summary.json"))
	args = parser.parse_args()

	md = args.path.read_text(encoding="utf-8")
	md = md[:8000]  # CPU 场景：先别喂太长

	schema = """{
  \"title\": string,
  \"bullets\": string[]
}"""
	prompt = json_prompt(
		schema=schema,
		task="请把输入 Markdown 总结为 10 条要点，bullets 必须正好 10 条，短句优先。",
		content=md,
	)

	obj = ensure_json(args.model, prompt)
	# 最小校验
	if not isinstance(obj.get("title"), str) or not isinstance(obj.get("bullets"), list):
		raise ValueError("invalid schema")
	if len(obj["bullets"]) != 10:
		raise ValueError("bullets must be exactly 10")

	args.out.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")
	print(f"wrote: {args.out}")
	return 0


if __name__ == "__main__":
	raise SystemExit(main())
```

运行：
```bash
python summarize_md.py docs/src/llm-study/intro.md --model llama3.2:3b --out summary.json
```

## 练习
- 写一个脚本：读取一个 `.md` 文件，把它总结成 10 条要点
- 输出为 `summary.json`
