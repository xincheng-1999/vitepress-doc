import argparse
import json
import re
from pathlib import Path
from urllib.parse import urlsplit


# Exclude image syntax: ![](...)
MD_LINK = re.compile(r"(?<!\!)\[(?P<text>[^\]]+)\]\((?P<link>[^)]+)\)")


def normalize_href(href: str) -> str:
    href = href.strip()
    if href.startswith("<") and href.endswith(">"):
        href = href[1:-1].strip()
    # Strip optional title: (url "title")
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
