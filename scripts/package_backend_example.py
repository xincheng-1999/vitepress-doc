"""Build a deterministic source download, excluding targets, secrets and caches."""
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[1]
EXAMPLE = ROOT / 'examples/backend-task-api'
OUTPUT = ROOT / 'docs/src/public/backend-guide/task-api.zip'


PROJECTS = (
    (ROOT / 'examples/backend-task-api', OUTPUT),
    (ROOT / 'examples/backend-session-lab', ROOT / 'docs/src/public/backend-guide/session-lab.zip'),
)


def source_files(example=EXAMPLE):
    allowed = {'pom.xml', 'README.md', 'Dockerfile', 'compose.yml', '.gitignore'}
    return sorted(
        path for path in example.rglob('*') if path.is_file()
        and (path.relative_to(example).as_posix() in allowed or
             (path.relative_to(example).parts[0] == 'src' and path.suffix in {'.java', '.yml', '.sql'}))
    )


def main():
    for example, output in PROJECTS:
        output.parent.mkdir(parents=True, exist_ok=True)
        files = source_files(example)
        if not files:
            raise SystemExit(f'No teaching source files found: {example}')
        # This generated archive is intentionally refreshed from the observed source.
        with ZipFile(output, 'w', compression=ZIP_DEFLATED) as archive:
            for path in files:
                info = ZipInfo(example.name + '/' + path.relative_to(example).as_posix(), (2026, 1, 1, 0, 0, 0))
                info.compress_type = ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                archive.writestr(info, path.read_bytes())
        print(f'Packaged {len(files)} source files: {output.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
