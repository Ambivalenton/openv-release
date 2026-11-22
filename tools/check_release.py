#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Source invariants of Öpen-V, run in CI before any build.

Checks versions, licence headers, the manifest (no backup, no cleartext, no background location, no
FileProvider), forbidden proprietary dependencies, store texts, the timetable service, and that every
generated file matches its source (translations, glossary, notices, legal texts, content)."""
from pathlib import Path
import re, subprocess, sys, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []


def check(ok, why):
    if not ok:
        errors.append(why)


build = (ROOT / 'app/build.gradle.kts').read_text()
code = re.search(r'versionCode = (\d+)', build)
name = re.search(r'versionName = "([^"]+)"', build)
check(code is not None and int(code[1]) >= 100, 'Missing or invalid versionCode')
check(name is not None and re.fullmatch(r'\d+\.\d+\.\d+', name[1]) is not None, 'versionName must look like 1.2.3')
version_code = code[1] if code else '0'
check('productFlavors' not in build, 'The app has one edition; product flavours are not used')

# Free software only: no proprietary location, analytics or ad SDKs.
gradle_files = [ROOT / 'app/build.gradle.kts', ROOT / 'build.gradle.kts', ROOT / 'settings.gradle.kts']
for forbidden in ['play-services', 'com.google.firebase', 'com.google.android.gms', 'crashlytics', 'admob']:
    for path in gradle_files:
        check(forbidden not in path.read_text(), f'Proprietary dependency "{forbidden}" in {path.relative_to(ROOT)}')
check('GNU GENERAL PUBLIC LICENSE' in (ROOT / 'LICENSE').read_text(), 'GPL text missing')
for f in sorted((ROOT / 'app/src/main/java').rglob('*.kt')):
    check('SPDX-License-Identifier: GPL-3.0-or-later' in f.read_text(), f'Licence header missing: {f.relative_to(ROOT)}')

ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
application = manifest.find('application')
check(application.get(ns + 'allowBackup') == 'false', 'Backup must remain disabled')
check(application.get(ns + 'usesCleartextTraffic') == 'false', 'Cleartext traffic enabled')
check(not any(n.get(ns + 'name', '').endswith('ACCESS_BACKGROUND_LOCATION') for n in manifest.iter('uses-permission')),
      'The app must not request background location')
check(not any('FileProvider' in n.get(ns + 'name', '') for n in manifest.iter('provider')),
      'No FileProvider: timetable data are not shared as files')

# Timetable service: VRR OpenService by default, attribution and terms link present.
service = (ROOT / 'app/src/main/java/dev/ambivalenton/openv/data/model/TimetableService.kt').read_text()
check('https://openservice.vrr.de/openservice/' in service, 'VRR OpenService endpoint missing')
check('Fahrplandaten: VRR' in service and 'https://www.opendata-oepnv.de/ht/de/api' in service, 'VRR attribution missing')

# Store texts within Play limits; release notes for the current versionCode.
for locale in ['de-DE', 'en-US']:
    folder = ROOT / 'fastlane/metadata/android' / locale
    for filename, limit in [('title.txt', 30), ('short_description.txt', 80), ('full_description.txt', 4000),
                            (f'changelogs/{version_code}.txt', 500)]:
        path = folder / filename
        check(path.is_file() and 0 < len(path.read_text().strip()) <= limit, f'{locale}/{filename}: missing or longer than {limit}')

# Legal texts in the app and on the privacy page; no placeholders left.
for folder in ['legal', 'legal/en']:
    for filename in ['help.txt', 'coverage.txt', 'privacy.txt', 'publisher.txt', 'notices.txt']:
        check((ROOT / 'app/src/main/assets' / folder / filename).is_file(), f'Missing {folder}/{filename}')
for path in ['PRIVACY.md', 'PRIVACY_EN.md', 'docs/legal/publisher.de.md', 'docs/legal/publisher.en.md']:
    text = (ROOT / path).read_text()
    check('oepenvapp@gmail.com' in text, f'Contact missing in {path}')
    check(not re.search(r'\{\{[A-Z_]+\}\}', text), f'Placeholder left in {path}')

for tool in ['generate_translations', 'generate_glossary', 'generate_notices', 'sync_publication',
             'generate_journey_content', 'generate_icon_content', 'generate_fonts']:
    result = subprocess.run([sys.executable, str(ROOT / f'tools/{tool}.py'), '--check'], capture_output=True, text=True)
    check(result.returncode == 0, f'{tool}.py --check failed; run it without --check and commit the result:\n'
          + (result.stdout + result.stderr).strip()[-600:])

if errors:
    print('\n'.join('ERROR: ' + x for x in errors))
    sys.exit(1)
print(f'Öpen-V {name[1]} ({version_code}): source invariants and generated files are consistent.')
