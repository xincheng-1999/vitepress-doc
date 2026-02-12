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
