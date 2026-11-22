#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate the English text catalogue from localization/en.json. No service or network is involved.

Only texts the app actually references (as string literals in app/src/main) enter the catalogue."""
from pathlib import Path
import json, re, sys

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'app/src/main/java/dev/ambivalenton/openv/localization/EnglishCatalog.kt'
data = json.loads((ROOT / 'localization/en.json').read_text())


def literal(s):
    return json.dumps(s, ensure_ascii=False).replace('$', '\\$')


for de, en in data.items():
    a = sorted(re.findall(r'%\d+\$[sdf]', de)); b = sorted(re.findall(r'%\d+\$[sdf]', en))
    assert a == b, (de, en, a, b)
references = '\n'.join(p.read_text() for p in sorted((ROOT / 'app/src/main/java').rglob('*.kt')) if p != TARGET)
selected = sorted((de, en) for de, en in data.items() if literal(de) in references)
text = '''// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.localization

internal val englishStrings: Map<String, String> = buildMap {
''' + '\n'.join(f'    put({literal(de)}, {literal(en)})' for de, en in selected) + '\n}\n'
if '--check' in sys.argv:
    assert TARGET.read_text() == text, 'Run tools/generate_translations.py'
else:
    TARGET.write_text(text)
print(f'{len(data)} text templates, {len(selected)} in the app, placeholder parity verified')
