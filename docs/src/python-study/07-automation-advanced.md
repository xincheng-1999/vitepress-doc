# 07. 自动化进阶（并发 / 日志 / 定时任务）

本章目标：让脚本“跑得更稳、更快”，并能在 Windows 上长期执行（任务计划程序/定时跑）。

> 原理补充：自动化最难的是“长期稳定”。你需要把脚本从“能跑一次”升级为：
> - 出错能定位（日志 + 输出文件）
> - 能恢复（幂等、重试、可重复运行）
> - 能被机器调度（退出码、固定工作目录、固定解释器）

## 1) logging：用它替代 print

### 1.1 最小可用的日志初始化（推荐模板）
```python
import logging
from pathlib import Path


def setup_logging(*, log_file: Path | None = None, level: int = logging.INFO) -> None:
	handlers: list[logging.Handler] = [logging.StreamHandler()]
	if log_file is not None:
		log_file.parent.mkdir(parents=True, exist_ok=True)
		handlers.append(logging.FileHandler(log_file, encoding="utf-8"))

	logging.basicConfig(
		level=level,
		format="%(asctime)s %(levelname)s %(name)s: %(message)s",
		handlers=handlers,
	)


logger = logging.getLogger("vp-tools")
```

使用：
```python
from pathlib import Path

setup_logging(log_file=Path("logs") / "job.log")
logger.info("started")
```

> 原理补充：日志不要只写控制台。定时任务跑失败时，你通常只能看日志文件。

## 2) 并发怎么选（够用版）
- I/O 密集：线程（`concurrent.futures.ThreadPoolExecutor`）
- CPU 密集：多进程（Windows 下注意 `if __name__ == "__main__"`）
- asyncio：当你需要海量并发且库支持 async 时再用

> 原理补充：你做的多数自动化（扫文件、发 HTTP、写 JSON）本质是 I/O 密集，线程池就够用了。

### 2.1 ThreadPoolExecutor：并发请求/并发处理文件
下面例子把“对一堆 URL 发请求”并发化，同时保留失败信息。

```python
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests


logger = logging.getLogger("vp-tools")


def fetch_json(url: str) -> dict:
	resp = requests.get(url, timeout=(3, 20))
	resp.raise_for_status()
	return resp.json()


def run(urls: list[str], *, max_workers: int = 8) -> tuple[list[dict], list[tuple[str, str]]]:
	ok: list[dict] = []
	bad: list[tuple[str, str]] = []

	with ThreadPoolExecutor(max_workers=max_workers) as ex:
		fut_to_url = {ex.submit(fetch_json, u): u for u in urls}
		for fut in as_completed(fut_to_url):
			url = fut_to_url[fut]
			try:
				ok.append(fut.result())
			except Exception as e:
				logger.warning("failed: %s (%s)", url, e)
				bad.append((url, str(e)))

	return ok, bad
```

> 原理补充：并发不要等于“无脑开 100 线程”。CPU 机器 + 网络接口都可能限流。先从 4~8 线程开始。

## 3) 重试与幂等（让脚本可重复运行）

### 3.1 幂等的基本思路
- 输出到确定路径（例如 `reports/xxx.json`）
- 支持“已存在则跳过”或“覆盖写入”
- 把失败样本单独落盘（方便后续重跑）

> 原理补充：定时任务最怕“跑一半挂了，留下半成品”。你要么写临时文件再原子替换，要么写结构化状态。

## 4) Windows 任务计划程序（Task Scheduler）运行姿势

### 4.1 三个常见坑（你提前规避）
1) **工作目录不对**：脚本里相对路径会错
2) **解释器不对**：用了系统 Python 而不是你的 venv
3) **看不到输出**：定时任务没有控制台，必须写日志

### 4.2 推荐做法：显式指定解释器 + 显式指定工作目录
假设你在仓库根目录有一个 venv：`.venv`。

建议把任务的“操作”配置成：
- Program/script：`cmd.exe`
- Add arguments：
  ```
  /c "cd /d D:\\myProject\\vitepress-doc && .\\.venv\\Scripts\\python.exe scripts\\md_tools.py check-links docs\\src --out broken-links.json >> logs\\task.out 2>>&1"
  ```

说明：
- `cd /d ...`：切换盘符并进入目录
- `python.exe`：用 venv 的解释器，依赖不会装错
- `>> logs\task.out 2>>&1`：把 stdout/stderr 都追加到日志

> 原理补充：在定时任务里，“能稳定找到对的 Python 解释器”和“能稳定找到对的工作目录”比什么都重要。

## 练习
把第 05 章的请求脚本升级为：
- 有 `--out` 输出 JSON
- 有日志文件 `logs/run.log`
- 对 5xx/429 做 3 次重试
- （可选）对一组 URL 并发请求并汇总报告
