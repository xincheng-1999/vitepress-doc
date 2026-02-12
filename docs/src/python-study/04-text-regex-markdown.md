# 04. 文本处理与正则（处理 Markdown / 日志）

本章目标：用“可控”的方式处理文本，特别是 Markdown（你的笔记仓库会大量用到）。

> 原理补充：正则适合“局部、明确、可测试”的模式匹配；当规则开始变复杂（例如需要理解 Markdown 结构）时，优先考虑解析器或分阶段处理，避免一次性正则把自己绕晕。

## 1) 正则基础（只讲够用的）
- 捕获组、命名组
- 贪婪 vs 非贪婪
- `re.sub` 替换

```python
import re

pattern = re.compile(r"\[(?P<text>[^\]]+)\]\((?P<link>[^)]+)\)")
```

## 2) Markdown 处理的策略
- 能不用正则就不用：优先结构化解析（后面需要时再引入库）
- 但“批量简单替换”用正则很高效

## 3) 本仓库的链接习惯
- 站内链接常写成绝对路由：`/back-end/database/mysql/installation`
- 很多页面省略 `.md`

> 原理补充：VitePress 的“路由”最终会映射到某个 Markdown 文件。常见映射是：
> - `/a/b` → `docs/src/a/b.md`
> - `/a/b/` 或 `/a/b`（目录页）→ `docs/src/a/b/index.md`
> 你的站点还设置了 `srcDir: "src"`，所以内容根目录是 `docs/src`。

## 练习
- 扫描 `docs/src` 下所有 `.md`：提取所有站内链接（以 `/` 开头的）
- 输出一份 `links.json`，包含：来源文件、链接地址

下面给一份可直接运行的脚本（不依赖第三方库）：

```python
import argparse
import json
import re
from pathlib import Path
from urllib.parse import urlsplit


# 排除图片语法：![](...)
MD_LINK = re.compile(r"(?<!\!)\[(?P<text>[^\]]+)\]\((?P<link>[^)]+)\)")


def strip_link(raw: str) -> str:
	raw = raw.strip()
	if raw.startswith("<") and raw.endswith(">"):
		raw = raw[1:-1].strip()
	# 去掉可选 title： (url "title")
	if " " in raw:
		raw = raw.split(" ", 1)[0].strip()
	return raw


def normalize_href(href: str) -> str:
	href = strip_link(href)
	parts = urlsplit(href)
	# 去掉 query/fragment，只保留 path
	return parts.path


def route_candidates(route: str, *, content_root: Path) -> list[Path]:
	# route: /a/b or /a/b/
	route = route.lstrip("/")
	if not route:
		return []

	if route.endswith("/"):
		route = route[:-1]

	# 1) 直接文件
	direct = content_root / f"{route}.md"

	# 2) 目录页
	index = content_root / route / "index.md"

	return [direct, index]


def extract_internal_routes(md_text: str) -> list[str]:
	links: list[str] = []
	for m in MD_LINK.finditer(md_text):
		href = normalize_href(m.group("link"))
		if href.startswith("/") and not href.startswith("//"):
			links.append(href)
	return links


def main() -> int:
	parser = argparse.ArgumentParser(prog="md-link-check")
	parser.add_argument("root", type=Path, help="content root, e.g. docs/src")
	parser.add_argument("--out", type=Path, default=Path("links.json"))
	args = parser.parse_args()

	content_root: Path = args.root
	md_files = sorted(content_root.rglob("*.md"))

	items = []
	for p in md_files:
		text = p.read_text(encoding="utf-8")
		routes = extract_internal_routes(text)
		if not routes:
			continue

		for r in routes:
			candidates = route_candidates(r, content_root=content_root)
			exists = any(c.exists() for c in candidates)
			items.append(
				{
					"from": p.relative_to(content_root).as_posix(),
					"route": r,
					"exists": exists,
					"candidates": [c.relative_to(content_root).as_posix() for c in candidates],
				}
			)

	report = {
		"content_root": content_root.as_posix(),
		"count": len(items),
		"broken": sum(1 for x in items if not x["exists"]),
		"items": items,
	}
	args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
	print(f"wrote: {args.out} (broken={report['broken']})")
	return 0


if __name__ == "__main__":
	raise SystemExit(main())
```

运行：
```bash
python md_link_check.py docs/src --out links.json
```

## 小项目
- 写一个脚本：检查 sidebar/nav 里配置的链接，对应文件是否存在（先做最小版）

你可以先用“正则提取 `link: "..."`”的方式从配置里拿到路由（足够应付本仓库的写法）。示例：

```python
import re
from pathlib import Path


LINK_FIELD = re.compile(r"\blink\s*:\s*['\"](?P<link>[^'\"]+)['\"]")


def extract_links_from_js(path: Path) -> list[str]:
	text = path.read_text(encoding="utf-8")
	return [m.group("link") for m in LINK_FIELD.finditer(text) if m.group("link").startswith("/")]


sidebar_links = extract_links_from_js(Path("docs/.vitepress/sidebar.js"))
nav_links = extract_links_from_js(Path("docs/.vitepress/nav.js"))
print("sidebar:", len(sidebar_links), "nav:", len(nav_links))
```

然后复用上面的 `route_candidates()` 去检查是否存在。
