# 02A. 常用库地图：哪些自带？哪些要安装？

本章目标：你作为 Python 入门，先搞清楚“哪些库是 Python 自带（标准库）”以及“哪些需要 `pip install`”，避免一上来就被依赖与环境绕晕。

> 原理补充：Python 里“库”分两类：
> - **标准库（stdlib）**：随 Python 解释器一起提供，`import xxx` 直接用
> - **第三方库（3rd-party）**：需要用 `pip` 安装到当前环境（建议是项目的 `.venv`）
> 
> 你之所以会遇到“同一台机器里 import 成功/失败不一致”，本质上是：你在用不同的 Python 解释器/虚拟环境。

## 0) 三个最常用的判断/排查命令

### A) 我现在用的是哪个 Python？
```bash
python -c "import sys; print(sys.executable)"
python --version
```

### B) 某个库到底有没有装在当前环境？
```bash
python -m pip show requests
python -m pip list
```

### C) 这个模块来自哪里？（是标准库还是 site-packages？）
```bash
python -c "import json; print(json.__file__)"
python -c "import requests; print(requests.__file__)"
```

> 原理补充：标准库通常在 Python 安装目录下（例如 `.../Lib/...`），第三方库通常在 `.../site-packages/...`。

## 1) 标准库（自带）：自动化最常用的 20%（先学这些）

### 文件与路径
- `pathlib`：跨平台路径与文件操作（强烈推荐）
- `shutil`：复制/移动/压缩（更偏“系统工具”）
- `glob`：简单通配符匹配（你也可以用 `Path.rglob`）

### 数据格式
- `json`：读写 JSON（自动化报告必备）
- `csv`：读写 CSV

### 文本与解析
- `re`：正则（批量替换、提取链接）
- `urllib.parse`：解析 URL（路径、query、fragment）

### 时间
- `datetime`：时间与格式化
- `time`：sleep、时间戳

### 命令行与日志
- `argparse`：CLI 参数解析（第 06 章）
- `logging`：日志（第 07 章）

### 并发
- `concurrent.futures`：线程池/进程池（第 07 章）

### 进程与系统
- `subprocess`：调用外部命令（例如 `pnpm docs:build`、`git`）
- `os` / `sys`：环境变量、解释器信息、退出码

### 数据结构（写脚本时很爽）
- `collections`：`Counter` / `defaultdict` / `deque`
- `dataclasses`：把“数据对象”写得更清晰

### 类型提示（提升可维护性）
- `typing`：类型标注（可选，但推荐在工具脚本里逐步使用）

#### 一段“标准库组合拳”例子
把 JSON 报告写到文件：
```python
import json
from pathlib import Path

report = {"ok": True, "count": 3}
Path("report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
```

## 2) 第三方库（要安装）：按场景给你一张清单

> 原理补充：第三方库要装到“当前环境”。强烈建议：每个项目一个 `.venv`，然后统一用 `python -m pip ...`。

### HTTP 请求
- `requests`：最通用、最省心（第 05 章默认用它）
  - 安装：`python -m pip install requests`
- `httpx`：需要 async 或更现代特性再考虑
  - 安装：`python -m pip install httpx`

### CLI 体验增强（可选）
- `rich`：更好看的终端输出（表格、颜色）
- `typer` 或 `click`：更易写 CLI（入门阶段先用 `argparse` 即可）

### 数据处理（按需）
- `pandas`：表格数据（CSV/Excel）处理很强，但依赖重
- `openpyxl`：读写 Excel（不想引入 pandas 时）

### 解析 HTML/XML（爬取/抓页面才需要）
- `beautifulsoup4`：HTML 解析
- `lxml`：更快更强的解析器（可选）

### 开发质量（建议你尽早用，但不强制）
- `pytest`：测试
- `ruff`：代码检查/格式化（非常推荐，省心）

### 中文分词（RAG/检索用）
- `jieba`：中文分词（第 11 章可选）

### 自动化浏览器（需要网页自动化才装）
- `playwright`：更现代的浏览器自动化

## 3) 安装与锁定依赖（入门最佳实践）

### 安装
```bash
python -m pip install requests
```

### 导出依赖（简单版）
```bash
python -m pip freeze > requirements.txt
```

### 在新机器/新环境安装
```bash
python -m pip install -r requirements.txt
```

> 原理补充：`pip freeze` 会把所有已安装包都写进去（可能包含你没直接用到的）。入门阶段先这样足够；更精细的依赖管理后面再升级。

## 4) 常见误区（你遇到 80% 的“import 报错”都在这里）
- 你装了库，但运行脚本用的是另一个 Python（不同解释器/不同 venv）
- 你在 A 环境里 `pip install`，在 B 环境里 `python xxx.py`
- 你在 Git Bash 里和 PowerShell 里 PATH 不一致

排查一招：永远用同一个入口：
```bash
python -c "import sys; print(sys.executable)"
python -m pip --version
```

## 5) 和本教程的对应关系（该装哪些就够了）
- 到第 05 章：只需要 `requests`
- 到第 09/10 章（Ollama）：继续用 `requests`
- 到第 11 章（最小 RAG）：可选装 `jieba`

如果你愿意，我也可以再加一张“从 0 到能跑：每章所需安装命令”的速查表。
