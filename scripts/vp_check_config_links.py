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
