"""Check backend links/images, sidebar coverage, SVG structure and source ZIP sync.

Run from any directory with Python 3.10+ and Node.js available (no extra packages).
"""
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit
from xml.etree import ElementTree as ET
from zipfile import ZipFile

from package_backend_example import PROJECTS, source_files

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / 'docs/src'
BACKEND = CONTENT / 'back-end'
COURSE = BACKEND / 'frontend-backend-guide'
ASSETS = CONTENT / 'public/backend-guide'
LINK = re.compile(r'!?\[[^\]]*\]\(<?([^\s)>]+)>?(?:\s+"[^"]*")?\)')
NS = {'s': 'http://www.w3.org/2000/svg'}


def without_code(body):
    lines = []
    fence = None
    for line in body.splitlines():
        plain = re.sub(r'^\s*(?:>\s*)+', '', line).lstrip()
        marker = re.match(r'(`{3,}|~{3,})', plain)
        if marker:
            if fence is None:
                fence = marker.group(1)[0]
            elif plain.startswith(fence * 3):
                fence = None
            continue
        if fence is None:
            lines.append(re.sub(r'`+[^`]*`+', '', line))
    return '\n'.join(lines)


def target_for(source, href):
    parts = urlsplit(href)
    if parts.scheme or parts.netloc:
        return None
    path = unquote(parts.path)
    if not path:
        return source
    if path.startswith('/'):
        rel = path.lstrip('/')
        candidates = [CONTENT / rel, CONTENT / 'public' / rel]
    else:
        candidates = [source.parent / path]
    for item in list(candidates):
        if item.suffix in ('.md', '.html'):
            candidates.append(item.with_suffix('.md'))
        elif not item.suffix:
            candidates.extend([item.with_suffix('.md'), item / 'index.md'])
    return next((p.resolve() for p in candidates if p.is_file()), False)


def main():
    errors = []
    references = set()
    count = 0
    for source in sorted(BACKEND.rglob('*.md')):
        body = source.read_text(encoding='utf-8')
        if len(re.findall(r'^```', body, re.M)) % 2:
            errors.append(f'Unbalanced code fences: {source.relative_to(ROOT)}')
        for href in LINK.findall(without_code(body)):
            count += 1
            target = target_for(source, href)
            if target is False:
                errors.append(f'{source.relative_to(ROOT)} -> missing {href}')
                continue
            if target:
                references.add(target)
                fragment = unquote(urlsplit(href).fragment)
                # Stable explicitly named anchors are used by the feature map.
                if fragment and target.suffix == '.md':
                    text = target.read_text(encoding='utf-8')
                    explicit = re.findall(r'\{#([^}]+)\}|\bid=["\']([^"\']+)', text)
                    ids = {a or b for a, b in explicit}
                    if fragment in {'collaboration', 'attachments', 'batch-jobs', 'webhooks', 'failure-exercises'} and fragment not in ids:
                        errors.append(f'{source.relative_to(ROOT)} -> missing anchor {href}')
    svgs = sorted(ASSETS.glob('*.svg'))
    for path in svgs:
        try:
            svg = ET.parse(path).getroot()
            ids = {el.attrib.get('id') for el in svg.iter() if 'id' in el.attrib}
            if svg.find('s:title', NS) is None or svg.find('s:desc', NS) is None:
                errors.append(f'SVG needs title and desc: {path.name}')
            if 'viewBox' not in svg.attrib:
                errors.append(f'SVG needs viewBox: {path.name}')
            for el in svg.iter():
                if el.tag.split('}')[-1] in {'script', 'foreignObject'}:
                    errors.append(f'Unsafe SVG element: {path.name}')
                for value in el.attrib.values():
                    for anchor in re.findall(r'url\(#([^)]+)\)', value):
                        if anchor not in ids:
                            errors.append(f'SVG missing marker {anchor}: {path.name}')
            if path.resolve() not in references:
                errors.append(f'Unreferenced SVG: {path.name}')
        except ET.ParseError as ex:
            errors.append(f'Invalid SVG {path.name}: {ex}')
    script = """
      import { pathToFileURL } from 'node:url';
      const config = (await import(pathToFileURL(process.argv[1]).href)).default;
      const links=[];
      function walk(x) {if(!x||typeof x!=='object')return;if(x.link)links.push(x.link);Object.values(x).forEach(walk)}
      walk(config['/back-end/frontend-backend-guide/']);console.log(JSON.stringify(links));
    """
    links = json.loads(subprocess.run(['node', '--input-type=module', '-e', script,
        str(ROOT / 'docs/.vitepress/sidebar.js')], capture_output=True, text=True, check=True).stdout)
    covered = {target_for(COURSE / 'index.md', link) for link in links}
    for path in COURSE.glob('*.md'):
        if path.resolve() not in covered:
            errors.append(f'Course page missing from sidebar: {path.name}')
    for example, output in PROJECTS:
        if not output.exists():
            errors.append(f'Missing {output.name}: run scripts/package_backend_example.py')
            continue
        with ZipFile(output) as archive:
            expected = {example.name + '/' + p.relative_to(example).as_posix(): p for p in source_files(example)}
            if set(archive.namelist()) != set(expected):
                errors.append(f'{output.name}: ZIP file list differs from source')
            for name, path in expected.items():
                if name in archive.namelist() and archive.read(name) != path.read_bytes():
                    errors.append(f'ZIP stale: {name}')
    print(f'Checked backend links/images={count}, course pages={len(list(COURSE.glob("*.md")))}, SVGs={len(svgs)}, sidebar entries={len(links)}')
    for error in errors:
        print(error, file=sys.stderr)
    print(f'Backend check: {len(errors)} error(s)')
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
