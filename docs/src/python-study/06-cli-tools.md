# 06. CLI 工具化（argparse）

本章目标：把脚本做成“像命令一样”的工具：
- 能传参
- 能返回退出码（方便 CI / 任务计划程序判断成功与失败）
- 能输出机器可读 JSON（方便后续汇总、二次处理）

> 原理补充：自动化脚本只靠 `print()` 很难长期维护。CLI 工具的关键是“契约”：
> - 输入（参数）明确
> - 输出（stdout/json 文件）明确
> - 失败（stderr + exit code）明确

## 1) 最小 argparse（认识一下）
```python
import argparse


def main() -> int:
  parser = argparse.ArgumentParser(prog="demo")
  parser.add_argument("path")
  args = parser.parse_args()
  print(args.path)
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
```

> 原理补充：用 `raise SystemExit(main())` 可以把 `main()` 的返回值当作退出码。

## 2) 退出码约定（建议你从一开始就统一）
推荐约定：
- `0`：成功
- `2`：参数错误（一般 argparse 自己会处理）
- `3`：输入为空/没有匹配内容（例如没找到任何 Markdown）
- `4`：外部依赖不可用（网络、Ollama 未启动等）
- `5`：运行时错误（解析失败、写文件失败等）

> 原理补充：退出码是“给机器看的”。Windows 任务计划程序、CI、shell 脚本都靠它判断是否需要重试/报警。

## 3) 子命令（subcommands）是组织复杂脚本的最佳方式
示例：我们做一个 `md-tools`（先用单文件实现，后面再拆包）。

目标子命令：
- `md-tools links <root>`：扫描 `docs/src` 下所有 `.md`，提取站内绝对路由链接（以 `/` 开头）
- `md-tools check-links <root>`：检查这些路由能否映射到文件（`.md` 或 `index.md`）
- `md-tools http-get <url>`：演示如何把“第 05 章的 HTTP 请求”做成命令

## 4) 短平快：一个可直接运行的 CLI（单文件版）
保存为 `scripts/md_tools.py`（放哪里都行，本仓库推荐放 `scripts/`）：

```python
import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from urllib.parse import urlsplit


MD_LINK = re.compile(r"(?<!\!)\[(?P<text>[^\]]+)\]\((?P<link>[^)]+)\)")


def normalize_href(href: str) -> str:
  href = href.strip()
  if href.startswith("<") and href.endswith(">"):
    href = href[1:-1].strip()
  if " " in href:
    href = href.split(" ", 1)[0].strip()
  parts = urlsplit(href)
  return parts.path


def extract_internal_routes(md_text: str) -> list[str]:
  routes: list[str] = []
  for m in MD_LINK.finditer(md_text):
    href = normalize_href(m.group("link"))
    if href.startswith("/") and not href.startswith("//"):
      routes.append(href)
  return routes


def route_candidates(route: str, *, content_root: Path) -> list[Path]:
  r = route.split("#", 1)[0].split("?", 1)[0]
  r = r.lstrip("/")
  if not r:
    return []
  if r.endswith("/"):
    r = r[:-1]
  if r.endswith(".md"):
    r = r[:-3]
  return [
    content_root / f"{r}.md",
    content_root / r / "index.md",
  ]


@dataclass
class LinkItem:
  from_path: str
  route: str


@dataclass
class BrokenItem:
  from_path: str
  route: str
  candidates: list[str]


def cmd_links(root: Path) -> list[LinkItem]:
  md_files = sorted(root.rglob("*.md"))
  if not md_files:
    raise FileNotFoundError(f"no markdown under: {root}")

  items: list[LinkItem] = []
  for p in md_files:
    text = p.read_text(encoding="utf-8")
    for r in extract_internal_routes(text):
      items.append(LinkItem(from_path=p.relative_to(root).as_posix(), route=r))
  return items


def cmd_check_links(root: Path) -> list[BrokenItem]:
  link_items = cmd_links(root)
  broken: list[BrokenItem] = []
  for it in link_items:
    candidates = route_candidates(it.route, content_root=root)
    if candidates and not any(c.exists() for c in candidates):
      broken.append(
        BrokenItem(
          from_path=it.from_path,
          route=it.route,
          candidates=[c.relative_to(root).as_posix() for c in candidates],
        )
      )
  return broken


def write_json(path: Path, obj: object) -> None:
  path.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
  parser = argparse.ArgumentParser(prog="md-tools")
  sub = parser.add_subparsers(dest="cmd", required=True)

  p_links = sub.add_parser("links", help="extract internal routes from markdown")
  p_links.add_argument("root", type=Path, help="content root, e.g. docs/src")
  p_links.add_argument("--out", type=Path, default=Path("links.json"))

  p_check = sub.add_parser("check-links", help="report broken internal routes")
  p_check.add_argument("root", type=Path, help="content root, e.g. docs/src")
  p_check.add_argument("--out", type=Path, default=Path("broken-links.json"))

  p_http = sub.add_parser("http-get", help="demo: GET JSON and write to file")
  p_http.add_argument("url")
  p_http.add_argument("--out", type=Path, default=Path("out.json"))
  p_http.add_argument("--timeout", type=float, default=20.0)

  args = parser.parse_args()

  try:
    if args.cmd == "links":
      items = cmd_links(args.root)
      report = {"count": len(items), "items": [asdict(x) for x in items]}
      write_json(args.out, report)
      print(f"wrote: {args.out} (count={report['count']})")
      return 0

    if args.cmd == "check-links":
      broken = cmd_check_links(args.root)
      report = {"broken": len(broken), "items": [asdict(x) for x in broken]}
      write_json(args.out, report)
      print(f"wrote: {args.out} (broken={report['broken']})")
      return 0 if report["broken"] == 0 else 5

    if args.cmd == "http-get":
      import requests

      resp = requests.get(args.url, timeout=(3, float(args.timeout)))
      resp.raise_for_status()
      data = resp.json()
      write_json(args.out, data)
      print(f"wrote: {args.out}")
      return 0

    raise ValueError(f"unknown cmd: {args.cmd}")

  except FileNotFoundError as e:
    print(f"error: {e}")
    return 3
  except Exception as e:
    print(f"error: {e}")
    return 5


if __name__ == "__main__":
  raise SystemExit(main())
```

运行示例：
```bash
# 1) 提取链接
python scripts/md_tools.py links docs/src --out links.json

# 2) 检查断链（0=无断链，否则返回 5）
python scripts/md_tools.py check-links docs/src --out broken-links.json

# 3) 演示 HTTP GET 并落盘
python scripts/md_tools.py http-get https://httpbin.org/json --out out.json
```

> 原理补充：这里把“对人友好”和“对机器友好”分开了：
> - stdout：只打印一行结果（wrote: ...）
> - JSON 文件：给机器/后续脚本读取

## 5) 小结：你下一章会怎么用它
- 第 07 章：加上 `logging`、并发执行、定时任务运行方式
- 第 12 章：把这些子命令升级成“专门针对 VitePress 仓库”的检查工具
