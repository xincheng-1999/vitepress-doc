# 12. 针对本仓库的自动化实战（VitePress）

本章目标：把 Python 自动化直接用在你的笔记站点上，立刻产生收益。

> 原理补充：对“内容仓库”做自动化，最重要的是可回滚、可重复、尽量只读检查先行（先报告问题，再决定是否自动修复），避免脚本误改导致大范围内容损坏。

本章会优先做 3 件“马上能用”的事情：
1) A 断链检查（最常见、最烦、最值得自动化）
2) B 未引用资源扫描（public 里堆积资源很正常，用脚本帮你清理候选）
3) D nav/sidebar 配置一致性检查（避免导航指向不存在页面）

> 短平快建议：先跑脚本生成 JSON 报告，不要一上来就自动修复。

## 0) 约定与前置
- 在仓库根目录运行（能看到 `docs/`、`package.json`）。
- 本仓库内容根目录是 `docs/src`（因为 VitePress `srcDir: "src"`）。
- 静态资源目录是 `docs/src/public`，在 Markdown 里用 `/<asset>` 引用。

> 原理补充：你看到的站内链接大多是路由（`/a/b/c`），它最终会映射到内容文件：`docs/src/a/b/c.md` 或 `docs/src/a/b/c/index.md`。

## 项目 A：检查站内链接是否指向存在的文件
- 输入：扫描 `docs/src/**/*.md`
- 规则：只检查站内绝对路由（以 `/` 开头）
- 输出：`broken-links.json`（来源文件 + 链接 + 推测目标文件）

### A1) 最小脚本（可直接跑）
保存为 `scripts/vp_check_links.py`：

```python
import argparse
import json
import re
from pathlib import Path
from urllib.parse import urlsplit


# 排除图片语法：![](...)
MD_LINK = re.compile(r"(?<!\!)\[(?P<text>[^\]]+)\]\((?P<link>[^)]+)\)")


def normalize_href(href: str) -> str:
	href = href.strip()
	if href.startswith("<") and href.endswith(">"):
		href = href[1:-1].strip()
	# 去掉可选 title： (url "title")
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
	return [
		content_root / f"{r}.md",
		content_root / r / "index.md",
	]


def main() -> int:
	parser = argparse.ArgumentParser(prog="vp-check-links")
	parser.add_argument("--content-root", type=Path, default=Path("docs") / "src")
	parser.add_argument("--out", type=Path, default=Path("broken-links.json"))
	args = parser.parse_args()

	content_root: Path = args.content_root
	md_files = sorted(content_root.rglob("*.md"))

	items = []
	for p in md_files:
		text = p.read_text(encoding="utf-8")
		for route in extract_internal_routes(text):
			candidates = route_candidates(route, content_root=content_root)
			exists = any(c.exists() for c in candidates)
			if not exists:
				items.append(
					{
						"from": p.relative_to(content_root).as_posix(),
						"route": route,
						"candidates": [c.relative_to(content_root).as_posix() for c in candidates],
					}
				)

	report = {
		"content_root": content_root.as_posix(),
		"broken": len(items),
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
python scripts/vp_check_links.py --content-root docs/src --out broken-links.json
```

> 原理补充：这里做的是“静态映射检查”。它不会真的启动 VitePress 去解析路由，但对你这种“绝对路由 + 省略 .md”风格已经非常有效。

## 项目 B：扫描未引用静态资源
- 目标目录：`docs/src/public/`
- 输出：`unused-assets.json`

### B1) 思路
- 把 `docs/src/public` 里的所有文件列出来（排除目录）
- 扫描 `docs/src/**/*.md`：找出所有 `(/xxx)` 引用
- “在 public 里存在，但从未被引用”的，作为候选未引用资源

> 原理补充：静态扫描无法保证 100%（例如某些资源在主题 JS/CSS 里引用），所以我们输出的是“候选列表”，不是自动删除。

### B2) 最小脚本（可直接跑）
保存为 `scripts/vp_unused_assets.py`：

```python
import argparse
import json
import re
from pathlib import Path


ASSET_REF = re.compile(r"\(/(?P<path>[^\s)]+)\)")


def collect_public_files(public_root: Path) -> set[str]:
	files = set()
	for p in public_root.rglob("*"):
		if p.is_file():
			files.add(p.relative_to(public_root).as_posix())
	return files


def collect_asset_refs(content_root: Path) -> set[str]:
	refs = set()
	for p in sorted(content_root.rglob("*.md")):
		text = p.read_text(encoding="utf-8")
		for m in ASSET_REF.finditer(text):
			ref = m.group("path")
			if ref.startswith("http://") or ref.startswith("https://"):
				continue
			ref = ref.split("#", 1)[0].split("?", 1)[0]
			refs.add(ref.lstrip("/"))
	return refs


def main() -> int:
	parser = argparse.ArgumentParser(prog="vp-unused-assets")
	parser.add_argument("--content-root", type=Path, default=Path("docs") / "src")
	parser.add_argument("--public-root", type=Path, default=Path("docs") / "src" / "public")
	parser.add_argument("--out", type=Path, default=Path("unused-assets.json"))
	args = parser.parse_args()

	public_files = collect_public_files(args.public_root)
	refs = collect_asset_refs(args.content_root)
	unused = sorted([f for f in public_files if f not in refs])

	report = {
		"public_root": args.public_root.as_posix(),
		"content_root": args.content_root.as_posix(),
		"public_files": len(public_files),
		"referenced": len(refs),
		"unused_candidates": len(unused),
		"items": unused,
	}
	args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
	print(f"wrote: {args.out} (unused_candidates={report['unused_candidates']})")
	return 0


if __name__ == "__main__":
	raise SystemExit(main())
```

运行：
```bash
python scripts/vp_unused_assets.py --content-root docs/src --public-root docs/src/public --out unused-assets.json
```

## 项目 C：为目录生成“目录页”
- 例如为某个学习目录生成 index（风格参考 `/back-end/database/index`）

> 原理补充：目录页是“内容的导航入口”。最省力的做法是先自动生成一个基础版（标题 + 链接列表），再手工润色。

## 项目 D：侧边栏/导航配置一致性检查
- 读取 `docs/.vitepress/sidebar.js` / `docs/.vitepress/nav.js`
- 检查 link 对应文件是否存在

### D1) 最小脚本（可直接跑）
保存为 `scripts/vp_check_config_links.py`：

```python
import argparse
import json
import re
from pathlib import Path


LINK_FIELD = re.compile(r"\blink\s*:\s*['\"](?P<link>[^'\"]+)['\"]")


def extract_routes_from_js(path: Path) -> list[str]:
	text = path.read_text(encoding="utf-8")
	routes = []
	for m in LINK_FIELD.finditer(text):
		link = m.group("link")
		if link.startswith("/"):
			routes.append(link)
	return routes


def route_candidates(route: str, *, content_root: Path) -> list[Path]:
	r = route.lstrip("/")
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


def main() -> int:
	parser = argparse.ArgumentParser(prog="vp-check-config-links")
	parser.add_argument("--content-root", type=Path, default=Path("docs") / "src")
	parser.add_argument("--sidebar", type=Path, default=Path("docs/.vitepress/sidebar.js"))
	parser.add_argument("--nav", type=Path, default=Path("docs/.vitepress/nav.js"))
	parser.add_argument("--out", type=Path, default=Path("config-broken-links.json"))
	args = parser.parse_args()

	routes = []
	for cfg in [args.sidebar, args.nav]:
		routes.extend(extract_routes_from_js(cfg))

	items = []
	for r in sorted(set(routes)):
		candidates = route_candidates(r, content_root=args.content_root)
		if candidates and not any(c.exists() for c in candidates):
			items.append(
				{
					"route": r,
					"candidates": [c.relative_to(args.content_root).as_posix() for c in candidates],
				}
			)

	report = {
		"content_root": args.content_root.as_posix(),
		"routes": len(set(routes)),
		"broken": len(items),
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
python scripts/vp_check_config_links.py --content-root docs/src --out config-broken-links.json
```

> 原理补充：这是“配置静态检查”，足以避免大多数导航指向错误。更高级的做法是用 JS AST 解析，但对当前仓库来说不需要上难度。

## 你可以怎么把它们串起来
建议按这个顺序跑：
1) `vp_check_links.py`（断链）
2) `vp_check_config_links.py`（nav/sidebar）
3) `vp_unused_assets.py`（未引用资源候选）

每次改完内容后，你可以用 `pnpm docs:build` 快速验证站点是否能构建。

> 小提醒：开发服务器命令是 `pnpm docs:dev`（不是 `pnpm run dev`）。
