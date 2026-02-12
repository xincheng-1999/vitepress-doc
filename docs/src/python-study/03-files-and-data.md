# 03. 文件系统与数据读写（pathlib / JSON / CSV）

本章目标：用 Python 在 Windows 上稳定地“批量处理文件”和“读写结构化数据”。

> 原理补充：在自动化里，稳定性比“写得快”更重要。用 `pathlib.Path` 可以避免 Windows 路径分隔符、相对路径、编码等细节导致的隐性 bug。

## 1) pathlib 三件套（最常用）
- 定位路径：`Path("...")` / `Path.cwd()`
- 遍历文件：`glob` / `rglob`
- 读写文本：`read_text` / `write_text`

```python
from pathlib import Path

root = Path("docs") / "src"
md_files = list(root.rglob("*.md"))
print("markdown files:", len(md_files))
```

> 原理补充：`rglob("*.md")` 会递归遍历目录；它返回的是 `Path` 对象，不是字符串。

## 2) 读写文本（编码与换行）
读取：
```python
from pathlib import Path

p = Path("README.md")
text = p.read_text(encoding="utf-8")
print(text[:200])
```

写入：
```python
from pathlib import Path

out = Path("out.txt")
out.write_text("hello\n", encoding="utf-8")
```

> 原理补充：Windows 上会遇到 UTF-8/BOM、换行（CRLF）等问题。你做“批量替换/生成文件”时，**永远显式写 `encoding="utf-8"`**。

## 3) JSON（写自动化报告必备）
```python
import json
from pathlib import Path

data = {
    "name": "vitepress-doc",
    "count": 3,
    "items": ["a", "b", "c"],
}

Path("report.json").write_text(
    json.dumps(data, ensure_ascii=False, indent=2),
    encoding="utf-8",
)
```

读取 JSON：
```python
import json
from pathlib import Path

obj = json.loads(Path("report.json").read_text(encoding="utf-8"))
print(obj["name"])
```

## 4) CSV（入门版：DictReader / DictWriter）
```python
import csv
from pathlib import Path

rows = [
    {"file": "a.md", "lines": 10},
    {"file": "b.md", "lines": 20},
]

with Path("report.csv").open("w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["file", "lines"])
    writer.writeheader()
    writer.writerows(rows)
```

> 原理补充：写 CSV 时带上 `newline=""` 可以避免 Windows 写出空行问题。

## 练习：为本仓库生成 Markdown 统计报表
目标：扫描 `docs/src` 下所有 `.md` 文件，输出 `md-report.json`。

示例脚本（可直接放到 `scripts/md_report.py` 运行）：
```python
import json
from collections import defaultdict
from pathlib import Path


def count_lines(path: Path) -> int:
    return len(path.read_text(encoding="utf-8").splitlines())


def main() -> None:
    root = Path("docs") / "src"
    files = sorted(root.rglob("*.md"))

    by_dir = defaultdict(int)
    total_lines = 0
    items = []

    for p in files:
        rel = p.relative_to(root).as_posix()
        lines = count_lines(p)
        total_lines += lines
        by_dir[str(p.parent.relative_to(root)).replace("\\\\", "/")] += 1
        items.append({"path": rel, "lines": lines})

    report = {
        "root": root.as_posix(),
        "file_count": len(files),
        "total_lines": total_lines,
        "dirs": dict(sorted(by_dir.items(), key=lambda x: x[0])),
        "items": items,
    }

    Path("md-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
```

## 小项目（为后面铺垫）
- 扫描 `docs/src/public/`：列出所有资源文件
- 扫描 `docs/src/**/*.md`：查找 `/<asset>` 引用
- 输出一份“可能未引用资源”的列表

这会在第 12 章做成一个 CLI 子命令。

## 1) 用 pathlib 代替 os.path
常用对象：`Path`。

```python
from pathlib import Path

root = Path("docs") / "src"
for p in root.rglob("*.md"):
    print(p)
```

## 2) 读取与写入文本（编码）
```python
from pathlib import Path

text = Path("a.txt").read_text(encoding="utf-8")
Path("b.txt").write_text(text, encoding="utf-8")
```

## 3) JSON
```python
import json
from pathlib import Path

data = {"a": 1}
Path("out.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
```

## 4) CSV（入门）
- 读写用 `csv` 标准库即可

## 练习
- 扫描 `docs/src` 下所有 Markdown 文件：统计文件数、总行数、每个目录的文件数
- 输出到 `report.json`

## 小项目（为后面铺垫）
- 写一个脚本：找出 `docs/src/public` 下“可能未被引用”的图片（先只做文件列表 + 手动检查）
