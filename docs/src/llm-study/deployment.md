# 部署与性能：吞吐、延迟、成本与可靠性

这一章目标：把“会调模型”变成“能上线一个稳定服务”。你会得到一个可直接复制的最小服务骨架：

- 一个 HTTP API（FastAPI）
- 支持流式输出（Streaming）
- 可切换后端：云端 OpenAI 兼容 / 本地 Ollama
- 带最小缓存、限流、超时与日志

RTX 3050 提醒：部署章节的重点是“工程可靠性”。本地推理可以用 7B 以内 + 4-bit 量化练手，但线上更常见是云端/专用推理机。

---

## 0. 环境准备

你需要：Python 3.10+、一个 venv。

安装依赖：

```bash
pip install -U fastapi uvicorn[standard] pydantic requests openai
```

说明：

- `fastapi + uvicorn`：最小 Web 服务
- `requests`：调 Ollama 本地接口
- `openai`：调云端 OpenAI 兼容接口

---

## 你需要关心的 4 个指标

- **延迟**：用户体感，重点看 P95/P99
- **吞吐**：QPS、并发、batching
- **成本**：单位请求成本、Token 成本、缓存命中率
- **可靠性**：失败率、超时率、降级策略

你可以把上线目标简化成一句话：

> 让用户更快看到答案（低延迟），让系统不会炸（高可靠），让你算得清账（可观测+可控成本）。

---

## 1. 最小服务：一个 /chat 接口（支持 Streaming）

### 1.1 目录结构（建议）

你可以新建一个目录，例如 `llm-service/`：

```text
llm-service/
  app.py
  providers.py
  cache.py
  requirements.txt  (可选)
```

### 1.2 Provider 抽象：云端 vs 本地

新建 `providers.py`：

```python
import os
import json
import time
import requests
from typing import Iterable, List, Dict, Any
from openai import OpenAI


Message = Dict[str, str]


class LLMProvider:
  def chat(self, messages: List[Message]) -> str:
    raise NotImplementedError

  def chat_stream(self, messages: List[Message]) -> Iterable[str]:
    # 默认不支持流式就退化为一次性
    yield self.chat(messages)


class OpenAICompatProvider(LLMProvider):
  def __init__(self):
    self.client = OpenAI(
      api_key=os.getenv("OPENAI_API_KEY"),
      base_url=os.getenv("OPENAI_BASE_URL"),
    )
    self.model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

  def chat(self, messages: List[Message]) -> str:
    resp = self.client.chat.completions.create(
      model=self.model,
      temperature=float(os.getenv("TEMPERATURE", "0")),
      messages=messages,
    )
    return resp.choices[0].message.content


class OllamaProvider(LLMProvider):
  def __init__(self):
    self.base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    self.model = os.getenv("OLLAMA_MODEL", "llama3.1")

  def chat(self, messages: List[Message]) -> str:
    r = requests.post(
      f"{self.base_url}/api/chat",
      json={"model": self.model, "messages": messages, "stream": False},
      timeout=120,
    )
    r.raise_for_status()
    return r.json()["message"]["content"]

  def chat_stream(self, messages: List[Message]):
    # Ollama 流式返回是 NDJSON，每行一个 json
    with requests.post(
      f"{self.base_url}/api/chat",
      json={"model": self.model, "messages": messages, "stream": True},
      stream=True,
      timeout=300,
    ) as r:
      r.raise_for_status()
      for line in r.iter_lines(decode_unicode=True):
        if not line:
          continue
        data = json.loads(line)
        if data.get("done"):
          break
        token = data.get("message", {}).get("content")
        if token:
          yield token


def get_provider() -> LLMProvider:
  backend = os.getenv("LLM_BACKEND", "openai").lower()
  if backend == "ollama":
    return OllamaProvider()
  return OpenAICompatProvider()
```

### 1.3 最小缓存（先做内存 TTL）

新建 `cache.py`：

```python
import time
import hashlib
from typing import Any, Optional


class TTLCache:
  def __init__(self, ttl_seconds: int = 30, max_items: int = 2000):
    self.ttl = ttl_seconds
    self.max_items = max_items
    self._store: dict[str, tuple[float, Any]] = {}

  def _evict(self):
    # 简单淘汰：按过期时间清理 + 超限时粗暴清空一半
    now = time.time()
    expired = [k for k, (exp, _) in self._store.items() if exp <= now]
    for k in expired:
      self._store.pop(k, None)
    if len(self._store) > self.max_items:
      for k in list(self._store.keys())[: self.max_items // 2]:
        self._store.pop(k, None)

  def get(self, key: str) -> Optional[Any]:
    self._evict()
    item = self._store.get(key)
    if not item:
      return None
    exp, val = item
    if exp <= time.time():
      self._store.pop(key, None)
      return None
    return val

  def set(self, key: str, value: Any):
    self._evict()
    self._store[key] = (time.time() + self.ttl, value)


def stable_key(s: str) -> str:
  return hashlib.sha256(s.encode("utf-8")).hexdigest()
```

### 1.4 FastAPI 服务（带 streaming、超时、最小日志）

新建 `app.py`：

```python
import os
import json
import time
from typing import List, Dict

from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from providers import get_provider
from cache import TTLCache, stable_key


Message = Dict[str, str]


class ChatRequest(BaseModel):
  messages: List[Message]
  stream: bool = True


app = FastAPI(title="LLM Service")

cache = TTLCache(ttl_seconds=int(os.getenv("CACHE_TTL", "30")))


@app.get("/health")
def health():
  return {"ok": True}


@app.post("/chat")
def chat(req: ChatRequest, request: Request):
  provider = get_provider()

  # 最小防护：限制消息条数与长度（避免被塞爆）
  if len(req.messages) > 50:
    raise HTTPException(400, "too many messages")

  joined = json.dumps(req.messages, ensure_ascii=False)
  if len(joined) > 30_000:
    raise HTTPException(400, "payload too large")

  # Prompt cache：相同输入短时间直接复用
  key = stable_key(joined)
  cached = cache.get(key)
  if cached and not req.stream:
    return {"cached": True, "content": cached}

  t0 = time.time()

  if not req.stream:
    content = provider.chat(req.messages)
    cache.set(key, content)
    return {
      "cached": False,
      "elapsed_ms": int((time.time() - t0) * 1000),
      "content": content,
    }

  def event_stream():
    # SSE 风格输出：data: <text>\n\n
    try:
      for token in provider.chat_stream(req.messages):
        yield f"data: {token}\n\n"
      yield f"data: [DONE]\n\n"
    finally:
      # 这里可以记录日志（request_id、耗时等）
      pass

  return StreamingResponse(event_stream(), media_type="text/event-stream")
```

启动服务：

```bash
uvicorn app:app --host 0.0.0.0 --port 8000
```

测试（非流式）：

```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"stream": false, "messages": [{"role":"user","content":"用 3 句话解释 KV Cache"}]}'
```

---

## 2. 选型：云端 vs 本地（RTX 3050 现实版）

### 2.1 云端（推荐做产品原型）

优点：

- 不用折腾 CUDA/驱动
- 模型更强、更稳定
- 容易做扩容、监控与计费

缺点：

- 有成本
- 有网络与合规约束

### 2.2 本地 Ollama（推荐练工程能力）

优点：

- 离线可用、成本可控
- 便于你练：Prompt/RAG/结构化输出/评测

缺点（RTX 3050 常见）：

- 模型越大越慢，长上下文会明显吃显存
- 7B 以内、4-bit 更现实；更大模型不作为默认目标

---

## 常见工程手段

- **流式输出（Streaming）**：提升体感
- **缓存**：
  - Prompt cache / Semantic cache
  - RAG 检索结果缓存
- **限流与降级**：
  - 超时直接降级到简答/模板
  - 模型 fallback（大模型失败切小模型）
- **结构化输出**：更容易校验与重试

补充 3 个你上线后一定会用到的：

- **超时与重试**：超时要快速失败，并且避免“重复扣费/重复动作”
- **分级降级**：复杂模式失败时降级为模板/简答
- **异步化**：长任务放队列（生成报告/批处理）

---

## 3. 性能优化的抓手（按收益排序）

### 3.1 先做 Streaming（体感提升最大）

- 用户不需要等完整答案，看到“在输出”就会更有信心
- 后端可以边生成边发送，减少单次阻塞

### 3.2 再做缓存（省钱、省时）

- Prompt cache：相同输入直接复用（本章代码已给最小版本）
- Semantic cache：语义相似也复用（进阶）
- RAG cache：检索结果缓存（对热点问题很有效）

### 3.3 再控制上下文与输出长度

- 历史对话摘要
- RAG top_k 控制
- `max_tokens` 控制

---

## 4. 限流、熔断与降级（可靠性）

最小原则：

- **限流**：保护后端不被打爆（按 IP / user_id / token 预算）
- **熔断**：下游挂了就快速失败（别把线程卡死）
- **降级**：宁可回答“信息不足/稍后重试”，也不要超时转圈

如果你想做一个最小“令牌桶限流”，我可以在下一节补一个 30 行内存实现（你确认后再加）。

---

## 成本控制小技巧

- 缩短上下文：把历史对话做摘要
- 控制输出长度：max_tokens
- 尽量把“确定性任务”改为抽取/分类（结构化）

再补两个很实用的：

- 把“解释型回答”改成“先给结论 + 再给引用”减少无效 token
- 把大模型当作“控制器”，把确定性工作交给代码（比如：检索、排序、规则校验）

---

## 上线前 checklist

- 失败重试策略是否会导致“重复扣费/重复动作”
- 关键路径是否有超时与熔断
- 日志是否脱敏（避免泄露用户数据）

再加 4 条上线必做：

- 是否记录 `request_id`，能回放一次请求的全链路
- 是否有灰度/回滚（模型版本、prompt 版本、检索参数）
- 是否有基础告警（错误率、超时率、P95、成本）
- 是否有安全防护（提示注入、工具调用参数校验）

---

## 本章验收

你做到下面两条就算通过：

- 能跑通 FastAPI `/chat`（流式或非流式任意一种）
- 能解释：你准备在哪一层做缓存、在哪一层做限流，以及为什么
