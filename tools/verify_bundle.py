#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Validate the actual Play bundle, then inspect APKs generated from that exact bundle."""
from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, tempfile, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--bundle', type=Path, required=True)
p.add_argument('--bundletool', type=Path, required=True)
p.add_argument('--signed', action='store_true')
p.add_argument('--json', type=Path, required=True)
a = p.parse_args()

def run(*args):
    return subprocess.run([str(x) for x in args], check=True, capture_output=True, text=True).stdout

def bundletool(*args):
    return run('java', '-jar', a.bundletool, *args)

bundletool('validate', '--bundle=' + str(a.bundle))
manifest = ET.fromstring(bundletool('dump', 'manifest', '--bundle=' + str(a.bundle), '--module=base'))
assert manifest.get('package') == 'dev.ambivalenton.openv'
code = re.search(r'versionCode = (\d+)', (ROOT/'app/build.gradle.kts').read_text())[1]
assert manifest.get(ANDROID+'versionCode') == code
name = re.search(r'versionName = "([^"]+)"', (ROOT/'app/build.gradle.kts').read_text())[1]
assert manifest.get(ANDROID+'versionName') == name
sdk = manifest.find('uses-sdk')
assert sdk.get(ANDROID+'targetSdkVersion') == '36'
assert sdk.get(ANDROID+'minSdkVersion') == '26'
permissions = {x.get(ANDROID+'name') for x in manifest.findall('uses-permission')}
assert 'android.permission.ACCESS_BACKGROUND_LOCATION' not in permissions
assert 'com.google.android.gms.permission.AD_ID' not in permissions
assert manifest.find('application').get(ANDROID+'allowBackup') == 'false'
assert manifest.find('application').get(ANDROID+'usesCleartextTraffic') == 'false'
config = bundletool('dump', 'config', '--bundle=' + str(a.bundle))
assert 'PAGE_ALIGNMENT_16K' in config, 'Bundle must request 16 KB native library alignment'
with zipfile.ZipFile(a.bundle) as z:
    assert z.testzip() is None
    signatures = [n for n in z.namelist() if re.fullmatch(r'META-INF/[^/]+\.(RSA|DSA|EC)', n)]
assert bool(signatures) == a.signed, 'Actual bundle signing differs from configured signing state'
certificate = None
if a.signed:
    run('jarsigner', '-verify', str(a.bundle))
    certificate = re.search(r'SHA256: ([A-F0-9:]+)', run('keytool', '-printcert', '-jarfile', a.bundle))[1].replace(':','').lower()

summary = dict(bundle=a.bundle.name, sha256=hashlib.sha256(a.bundle.read_bytes()).hexdigest(),
    package=manifest.get('package'), version_code=int(code), target_sdk=36,
    signature='SIGNED' if a.signed else 'UNSIGNED — cannot be submitted to Play',
    upload_certificate_sha256=certificate, bundletool_validation='PASS', alignment='PAGE_ALIGNMENT_16K',
    permissions=sorted(permissions), generated_apk='NOT_CHECKED')

# The preview test key is used only for a temporary APK inspection. It never signs the AAB.
review_key = os.environ.get('OPENV_REVIEW_KEYSTORE')
if review_key:
    with tempfile.TemporaryDirectory() as temp:
        apks = Path(temp)/'inspection.apks'
        bundletool('build-apks', '--bundle='+str(a.bundle), '--output='+str(apks), '--mode=universal',
            '--ks='+review_key, '--ks-key-alias=androiddebugkey', '--ks-pass=pass:android', '--key-pass=pass:android')
        apk = Path(temp)/'inspection.apk'
        with zipfile.ZipFile(apks) as z: apk.write_bytes(z.read('universal.apk'))
        result = Path(temp)/'apk.json'
        run('python3', ROOT/'tools/verify_apk.py', '--apk', apk, '--package', 'dev.ambivalenton.openv',
            '--abi', 'arm64-v8a', '--abi', 'armeabi-v7a', '--abi', 'x86', '--abi', 'x86_64',
            '--report-cert', '--json', result)
        summary['generated_apk'] = json.loads(result.read_text())
        summary['generated_apk']['purpose'] = 'Temporary inspection only; signature is not the Play upload or app signing key'

a.json.write_text(json.dumps(summary, indent=2, ensure_ascii=False)+'\n')
print(json.dumps(summary, indent=2, ensure_ascii=False))
