#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check an actually built APK instead of trusting the build configuration.

Checks: signature and certificate, package, versionCode/-Name, SDK levels, permissions (declared
ones present, forbidden ones absent), ABIs, ZIP CRC, 16 KB page alignment of the ZIP entries and of
every native ELF load segment, byte-identical packaged assets, the widget classes in the optimised
DEX and the absence of proprietary Google SDK classes.

The app never asks for location in the background (no ACCESS_BACKGROUND_LOCATION).

GitHub Actions runs it for every preview and release build; locally for example:
  python3 tools/verify_apk.py --apk app/build/outputs/apk/preview/<name>.apk --report-cert
  python3 tools/verify_apk.py --apk <release>.apk --package dev.ambivalenton.openv --abi arm64-v8a --abi armeabi-v7a --abi x86 --abi x86_64 --unsigned
Exit code 1 on any failed check; the JSON summary is printed either way.
"""
from pathlib import Path
import argparse,hashlib,json,os,re,struct,subprocess,sys,zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ANDROID='{http://schemas.android.com/apk/res/android}'
FORBIDDEN_PERMISSIONS={'android.permission.READ_EXTERNAL_STORAGE','android.permission.WRITE_EXTERNAL_STORAGE',
    'android.permission.MANAGE_EXTERNAL_STORAGE','android.permission.READ_CONTACTS','android.permission.CAMERA',
    'android.permission.RECORD_AUDIO','android.permission.ACTIVITY_RECOGNITION','android.permission.BODY_SENSORS',
    'com.google.android.gms.permission.AD_ID'}
FORBIDDEN_CODE=('Lcom/google/android/gms/','Lcom/google/firebase/','Lcom/google/android/play/core/')
WIDGETS={'Ldev/ambivalenton/openv/widget/AppQuickWidget;','Ldev/ambivalenton/openv/widget/DepartureBoardWidget;'}
PAGE=16384

p=argparse.ArgumentParser(description=__doc__.splitlines()[0])
p.add_argument('--apk',type=Path,required=True)
p.add_argument('--package',default='dev.ambivalenton.openv.next',help='expected package (default: the preview package)')
p.add_argument('--version-code',default=re.search(r'versionCode = (\d+)',(ROOT/'app/build.gradle.kts').read_text()).group(1))
p.add_argument('--abi',action='append',help='expected ABI; repeat for several (default arm64-v8a)')
sign=p.add_mutually_exclusive_group(required=True)
sign.add_argument('--cert',help='required signer certificate SHA-256')
sign.add_argument('--report-cert',action='store_true',help='require a valid signature, only report the certificate')
sign.add_argument('--unsigned',action='store_true',help='expect an unsigned (not installable) APK')
p.add_argument('--json',type=Path,help='also write the summary to this file')
a=p.parse_args()
FORBIDDEN_PERMISSIONS.add('android.permission.ACCESS_BACKGROUND_LOCATION')
abis=sorted(a.abi or ['arm64-v8a'])
errors=[]
def check(ok,why):
    if not ok:errors.append(why)
    return ok

def tool(name):
    sdk=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    exe=sdk/'build-tools/36.0.0'/((name+('.bat' if name=='apksigner' else '.exe')) if os.name=='nt' else name)
    if not exe.is_file():sys.exit(f'Missing {exe}: install Build Tools 36.0.0 and set ANDROID_HOME.')
    return str(exe)
def run(*command):return subprocess.run(list(command),capture_output=True,text=True)

apk=a.apk.resolve()
if not apk.is_file():sys.exit(f'No APK at {apk}')
summary={'apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest()}

# Signature and certificate.
signed=run(tool('apksigner'),'verify','--verbose','--print-certs',str(apk))
text=signed.stdout+signed.stderr
cert=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',text)
schemes=sorted(set(re.findall(r'Verified using (v[0-9.]+) scheme \([^)]*\): true',text)))
if a.unsigned:
    check(signed.returncode!=0 and cert is None,'APK unexpectedly carries a valid signature')
    summary['signature']='UNSIGNED (not installable)'
else:
    check(signed.returncode==0,'Signature does not verify')
    summary['signature']='VERIFIED' if signed.returncode==0 else 'FAILED'
    summary['signature_schemes']=schemes
    summary['certificate_sha256']=cert[1] if cert else None
    if a.cert:check(cert is not None and cert[1]==a.cert.lower(),'Signer certificate differs from the expected one')

# Package identity, version, SDK levels, permissions, declared ABIs.
badging=run(tool('aapt'),'dump','badging',str(apk))
check(badging.returncode==0,'aapt cannot read the APK')
b=badging.stdout
package=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'",b)
if check(package is not None,'No package line in badging'):
    summary.update(package=package[1],version_code=int(package[2]),version_name=package[3])
    check(package[1]==a.package,f'Package {package[1]} differs from {a.package}')
    check(package[2]==str(a.version_code),f'versionCode {package[2]} differs from {a.version_code}')
for key,pattern in [('min_sdk',r"sdkVersion:'(\d+)'"),('target_sdk',r"targetSdkVersion:'(\d+)'")]:
    m=re.search(pattern,b);summary[key]=int(m[1]) if m else None
permissions=sorted(set(re.findall(r"uses-permission: name='([^']+)'",b)))
manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
declared=sorted({e.get(ANDROID+'name') for e in manifest.iter('uses-permission')})
check(summary.get('target_sdk')==36,'Unexpected target SDK')
check(summary.get('min_sdk')==26,'Unexpected minimum SDK')
summary['permissions']=permissions
summary['permissions_merged_from_libraries']=[x for x in permissions if x not in declared]
for x in declared:check(x in permissions,f'Declared permission missing in the APK: {x}')
for x in permissions:check(x not in FORBIDDEN_PERMISSIONS,f'Forbidden permission in the APK: {x}')
native_code=re.search(r"native-code: (.+)",b)
summary['native_code']=re.findall(r"'([^']+)'",native_code[1]) if native_code else []

# ZIP integrity and 16 KB alignment of stored entries (zipalign -P 16 checks uncompressed .so pages).
aligned=run(tool('zipalign'),'-c','-P','16','4',str(apk))
check(aligned.returncode==0,'zipalign -c -P 16 4 failed: '+(aligned.stdout+aligned.stderr).strip()[-300:])
summary['zipalign_16k']='PASS' if aligned.returncode==0 else 'FAIL'
native=[];assets=0;widgets={w:0 for w in WIDGETS};forbidden=set();dex_classes=0
with zipfile.ZipFile(apk) as z:
    check(z.testzip() is None,'ZIP CRC error')
    names=z.namelist()
    found=sorted({n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')})
    check(found==abis,f'ABIs {found} differ from {abis}')
    summary['abis']=found
    for name in names:
        if not (name.startswith('lib/') and name.endswith('.so')):continue
        data=z.read(name)
        if not check(data[:4]==b'\x7fELF' and data[5]==1 and data[4] in (1,2),f'{name} is no little-endian ELF'):continue
        wide=data[4]==2
        phoff=struct.unpack_from('<Q' if wide else '<I',data,32 if wide else 28)[0]
        size=struct.unpack_from('<H',data,54 if wide else 42)[0];count=struct.unpack_from('<H',data,56 if wide else 44)[0]
        loads=[]
        for i in range(count):
            o=phoff+size*i
            if struct.unpack_from('<I',data,o)[0]!=1:continue # PT_LOAD
            offset=struct.unpack_from('<Q' if wide else '<I',data,o+(8 if wide else 4))[0]
            address=struct.unpack_from('<Q' if wide else '<I',data,o+(16 if wide else 8))[0]
            align=struct.unpack_from('<Q' if wide else '<I',data,o+(48 if wide else 28))[0]
            loads.append(align)
            if wide:check(align>=PAGE and (address-offset)%PAGE==0,f'{name}: 64-bit load segment not 16 KB aligned ({align})')
        check(bool(loads),f'{name} has no load segment')
        native.append({'library':name,'bits':64 if wide else 32,'load_alignments':sorted(set(loads))})
    # Packaged assets must equal the sources (e.g. the stop catalogue, fonts and legal texts).
    folder=ROOT/'app/src/main/assets'
    sources={path.relative_to(folder):path for path in folder.rglob('*')}
    for rel,source in sorted(sources.items()):
        if not source.is_file() or any(part.startswith('.') for part in rel.parts):continue
        entry='assets/'+rel.as_posix()
        if check(entry in names,f'Asset missing: {entry}'):
            check(z.read(entry)==source.read_bytes(),f'Asset differs from the source: {entry}');assets+=1
    for name in names:
        if not re.fullmatch(r'classes[0-9]*\.dex',name):continue
        data=z.read(name)
        def word(o):return struct.unpack_from('<I',data,o)[0]
        if not check(data[:4]==b'dex\n' and word(40)==0x12345678,f'{name} is no DEX file'):continue
        def descriptor(index):
            o=word(word(60)+word(word(68)+index*4)*4)
            while data[o]&128:o+=1
            o+=1
            return data[o:data.index(0,o)].decode('utf-8',errors='replace')
        for i in range(word(96)):
            o=word(100)+i*32;identity=descriptor(word(o));dex_classes+=1
            if identity in widgets:
                check(word(o+4)&0x400==0 and word(o+24)>0,f'Widget class is abstract or empty: {identity}');widgets[identity]+=1
            if identity.startswith(FORBIDDEN_CODE):forbidden.add(identity.split('/')[2] if identity.count('/')>2 else identity)
for w,n in widgets.items():check(n==1,f'Widget class {w} defined {n} times')
check(not forbidden,f'Proprietary Google SDK classes in the DEX: {sorted(forbidden)}')
summary.update(native_libraries=len(native),native_elf_16k='PASS' if all(min(x['load_alignments'])>=PAGE for x in native if x['bits']==64) else 'FAIL',
    assets_identical=assets,dex_classes=dex_classes,widget_classes='PASS' if all(n==1 for n in widgets.values()) else 'FAIL',
    proprietary_google_sdks='none' if not forbidden else sorted(forbidden),result='PASS' if not errors else 'FAIL',errors=errors)
output=json.dumps(summary,indent=2,ensure_ascii=False)
print(output)
if a.json:a.json.write_text(output+'\n',encoding='utf-8')
sys.exit(1 if errors else 0)

