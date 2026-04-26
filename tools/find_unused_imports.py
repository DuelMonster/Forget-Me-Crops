#!/usr/bin/env python3
import os, re, json

root = os.getcwd()
report = {}

import_re = re.compile(r'^\s*import\s+(?!static)([\w\.\$]+)\s*;\s*$', re.MULTILINE)

for dirpath, dirnames, filenames in os.walk(root):
    norm = dirpath.replace('\\','/')
    if '/src/' not in norm:
        continue
    if any(p in norm for p in ['/build/', '/.gradle/', '/.git/']):
        continue
    for fn in filenames:
        if not fn.endswith('.java'):
            continue
        path = os.path.join(dirpath, fn)
        try:
            with open(path, 'r', encoding='utf-8') as f:
                text = f.read()
        except Exception:
            continue
        imports = import_re.findall(text)
        if not imports:
            continue
        body = import_re.sub('', text)
        unused = []
        for imp in imports:
            if imp.endswith('.*'):
                continue
            simple = imp.split('.')[-1]
            # handle inner classes 'Outer$Inner'
            simple = simple.split('$')[-1]
            # search for word boundary occurrences in body
            if not re.search(r'\b' + re.escape(simple) + r'\b', body):
                unused.append(imp)
        if unused:
            report[path] = unused

out = os.path.join(root, 'unused_imports_report.json')
with open(out, 'w', encoding='utf-8') as f:
    json.dump(report, f, indent=2)

print('Wrote report to', out)
print('Files with candidate unused imports:', len(report))
for p, imps in report.items():
    print(p)
    for i in imps:
        print('  ', i)
