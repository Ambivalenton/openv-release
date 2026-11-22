#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Publish curated source notes and the unchanged upstream licence texts offline.

Inputs: content/dependency-licenses.json (reviewed runtime components), content/licenses-{de,en}.md
(data sources and notes) and the full licence texts in app/src/main/assets/licenses/.
Outputs: the in-app notices (DE/EN) and THIRD_PARTY_NOTICES.md. Run with --check in CI."""
from pathlib import Path
import json, re, sys

ROOT = Path(__file__).resolve().parents[1]
items = json.loads((ROOT / 'content/dependency-licenses.json').read_text())
assert all(x.get('licenses') for x in items), 'Unreviewed runtime component'
inventory = '\n'.join('- ' + item['component'] + ' — ' + '; '.join(x['name'] + ' · ' + x.get('url', '') for x in item['licenses']) for item in items)
license_files = ['GPL-3.0.txt', 'Apache-2.0.txt', 'MapLibre-Native.txt', 'MapLibre-Core.txt', 'MapLibre-Android.txt']
outputs = {}
for language, folder in [('de', 'legal'), ('en', 'legal/en')]:
    header = (ROOT / f'content/licenses-{language}.md').read_text().rstrip()
    appendix = ''
    for name in license_files:
        original = (ROOT / 'app/src/main/assets/licenses' / name).read_text()
        longest = max((len(m.group()) for m in re.finditer(r'^`+', original, re.M)), default=0)
        fence = '`' * max(3, longest + 1)
        appendix += '\n\n## ' + name + '\n\n' + fence + 'text\n' + original.rstrip('\n') + '\n' + fence
    outputs[f'app/src/main/assets/{folder}/notices.txt'] = header + '\n\n' + inventory + appendix + '\n'
outputs['THIRD_PARTY_NOTICES.md'] = '\n\n---\n\n'.join(
    (ROOT / f'content/licenses-{lang}.md').read_text().rstrip() + '\n\n' + inventory for lang in ['de', 'en']
) + '\n\nFull licence texts: app/src/main/assets/licenses/ and the offline app notices.\n'
for name, content in outputs.items():
    path = ROOT / name
    if '--check' in sys.argv:
        assert path.read_text() == content, f'Regenerate {name}'
    else:
        path.write_text(content)
print(f'{len(items)} runtime components; complete software licences bundled in DE/EN')
