# 05. 网络请求与接口调用（requests）

本章目标：像前端一样稳健地调用 HTTP 接口（超时、重试、错误处理、落盘），并为后面“调用 Ollama / 做 RAG”打基础。

> 原理补充：自动化脚本的 HTTP 失败是常态（网络抖动、DNS、限流、5xx）。你需要的是：
> 1) **超时**（永远不要无限等）
> 2) **重试**（只对“可重试错误”）
> 3) **可观测**（日志/落盘）
> 4) **可控**（用 Session 复用连接，减少偶发失败）

## 0) 安装依赖
本章使用 `requests`：
```bash
python -m pip install requests
```

## 1) 最小请求（带超时）
```python
import requests

resp = requests.get("https://httpbin.org/get", timeout=10)
resp.raise_for_status()
print(resp.json())
```

> 原理补充：`timeout=10` 是“最多等 10 秒”。更推荐用二元组：`timeout=(connect_timeout, read_timeout)`。

## 2) 你应该默认写上的三件事

### A) 超时用二元组
```python
requests.get("https://httpbin.org/get", timeout=(3, 20))
```

### B) 响应检查：`raise_for_status()`
```python
resp = requests.get("https://httpbin.org/status/404", timeout=(3, 20))
resp.raise_for_status()  # 4xx/5xx 会抛异常
```

### C) JSON 落盘（UTF-8）
```python
import json
from pathlib import Path

data = {"ok": True}
Path("out.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
```

## 3) 用 Session 复用连接（推荐）
```python
import requests


def make_session() -> requests.Session:
	s = requests.Session()
	s.headers.update({
		"User-Agent": "vp-tools/0.1",
		"Accept": "application/json",
	})
	return s


session = make_session()
resp = session.get("https://httpbin.org/get", timeout=(3, 20))
resp.raise_for_status()
print(resp.json())
```

> 原理补充：`Session` 会复用 TCP 连接（keep-alive），多次请求更快也更稳。

## 4) 重试（两种方式）

### 方式 1：最简单的手写重试（建议先学这个）
只对“典型可重试错误”重试：网络异常、5xx、429。

```python
import time

import requests


def get_json_with_retry(url: str, *, attempts: int = 3) -> dict:
	session = requests.Session()

	last_exc: Exception | None = None
	for i in range(attempts):
		try:
			resp = session.get(url, timeout=(3, 20))
			# 对 429/5xx 做重试，其它 4xx 直接失败
			if resp.status_code in (429, 500, 502, 503, 504):
				raise requests.HTTPError(f"retryable status: {resp.status_code}", response=resp)
			resp.raise_for_status()
			return resp.json()
		except (requests.Timeout, requests.ConnectionError, requests.HTTPError) as e:
			last_exc = e
			if i < attempts - 1:
				time.sleep(0.5 * (2**i))  # 指数退避：0.5s, 1s, 2s...
				continue
			raise

	raise RuntimeError(f"unreachable: {last_exc}")
```

> 原理补充：不要对所有错误重试。对 4xx（尤其 401/403/404）重试只会浪费时间；对 429/5xx 和网络错误重试才有意义。

### 方式 2：HTTPAdapter + Retry（更“工程化”）
`requests` 底层用 `urllib3`，可以配置自动重试策略。

```python
import requests
from requests.adapters import HTTPAdapter


def make_retry_session() -> requests.Session:
	# urllib3 是 requests 的依赖，这里不需要额外安装
	from urllib3.util.retry import Retry

	retry = Retry(
		total=3,
		backoff_factor=0.5,
		status_forcelist=[429, 500, 502, 503, 504],
		allowed_methods=["GET", "POST"],
		raise_on_status=False,
	)
	adapter = HTTPAdapter(max_retries=retry)

	s = requests.Session()
	s.mount("http://", adapter)
	s.mount("https://", adapter)
	return s
```

## 5) 大文件下载（流式）
不要 `resp.content` 一次性读进内存。

```python
from pathlib import Path

import requests


def download(url: str, out: Path) -> None:
	with requests.get(url, stream=True, timeout=(3, 120)) as resp:
		resp.raise_for_status()
		with out.open("wb") as f:
			for chunk in resp.iter_content(chunk_size=1024 * 64):
				if chunk:
					f.write(chunk)


download("https://httpbin.org/bytes/1024", Path("demo.bin"))
```

> 原理补充：`stream=True` + `iter_content` 是“边下载边写盘”，更稳也更省内存。

## 6) 练习：拉取一个 JSON 并落盘
目标：请求一个公开 JSON API，并写入 `out.json`。

建议用这个（稳定、无鉴权）：
- `https://httpbin.org/json`

加分项：
- 为请求加 `User-Agent`
- 用 `timeout=(3, 20)`
- 对 5xx/429 加 3 次重试

下一章我们会把这些能力封装成一个 CLI 工具（子命令 + 退出码 + JSON 输出）。
