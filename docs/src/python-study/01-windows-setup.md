# 01. Windows 环境与工程起步

本章目标：在 Windows 上建立一个“可长期使用”的 Python 开发方式（不和系统环境打架），并能在 VS Code 里顺畅运行/调试脚本。

> 原理补充：Python 的依赖是“按环境隔离”的；在 Windows 上最容易踩坑的是把全局 Python、系统 PATH、不同项目依赖混在一起。`venv` 的核心价值就是把每个项目的解释器与依赖锁在项目内。

## 0) 你应该先知道的两件事
- 你在终端里看到的 `python` 不一定是你“想要的那个 Python”。Windows 上更稳的入口通常是 `py`（Python Launcher）。
- 你运行 VitePress 站点的命令是 `pnpm docs:dev`（不是 `pnpm run dev`）。

## 1) 安装 Python（建议）
建议安装 Python 3.11+（生态与兼容性较稳）。

验证安装：
```bash
python --version
pip --version
py --version
```

如果 `python`/`pip` 指向不一致，优先用 `py`：
```bash
py -3.11 --version
py -3.11 -m pip --version
```

> 原理补充：`py -3.11 -m pip ...` 这种写法能保证“pip 属于你指定的 Python 版本”，避免装到别的解释器里。

## 2) 虚拟环境（必须掌握）
在你要写脚本/工具的目录里创建并使用 `venv`：

```bash
py -3.11 -m venv .venv
```

激活环境：
```bash
# PowerShell
.\.venv\Scripts\Activate.ps1

# cmd
.\.venv\Scripts\activate.bat
```

安装依赖与导出：
```bash
python -m pip install -U pip
python -m pip install requests
python -m pip freeze > requirements.txt
```

> 原理补充：在虚拟环境激活后，`python -m pip ...` 是最不容易装错环境的一种方式。

## 3) VS Code 建议用法（短平快）
- 选择解释器：打开命令面板，选择 `.venv` 对应的 Python。
- 运行脚本：直接 Run Python File。
- 调试脚本：用 Run and Debug（后面写 CLI 时更方便传参）。

## 4) 项目结构（脚本 vs 小工具包）
你会遇到两类脚本：

### A) 一次性脚本（够用即可）
```
scripts/
  report_md_stats.py
```

### B) 可复用工具（推荐，从第 6 章开始会用到）
```
src/
  md_tools/
    __init__.py
    cli.py
    core.py
```

> 原理补充：当脚本会被重复运行/定时任务调用/分享给别人时，就不要写成“散落的脚本文件”，而要做成一个可安装/可调用的小工具包。

## 练习（5 分钟）
新建 `playground/hello.py`，打印：当前工作目录、Python 版本、`sys.path`。

```python
import sys
from pathlib import Path

print("cwd:", Path.cwd())
print("python:", sys.executable)
print("version:", sys.version)
print("sys.path[0]:", sys.path[0])
```

## 常见问题（Windows）
- PowerShell 执行策略导致无法激活 venv：可临时执行 `Set-ExecutionPolicy -Scope Process RemoteSigned`。
- 路径问题：尽量用 `pathlib.Path`，不要手拼 `\\`。

延伸阅读：如需版本管理，可参考 `/python-study/pyenv`。
