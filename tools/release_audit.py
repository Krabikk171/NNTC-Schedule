#!/usr/bin/env python3
"""Basic offline checks for a *distribution source tree*, not an Android build."""
from pathlib import Path
import hashlib, json, re, sys

root=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[1]
failures=[]
def check(ok,why):
    print(('PASS ' if ok else 'FAIL ')+why)
    if not ok:failures.append(why)

def txt(path):return (root/path).read_text(encoding='utf-8')

def match(pattern,string):
    found=re.search(pattern,string)
    return found.group(1) if found else None

build=txt('app/build.gradle.kts')
log=txt('app/src/main/java/ru/krabikk/nntcschedule/Changelog.kt')
vers=match(r'versionName\s*=\s*"([^"]+)"',build)
code=match(r'versionCode\s*=\s*(\d+)',build)
latest=match(r'ChangelogVersion\(\s*version\s*=\s*"([^"]+)"',log)
constant=match(r'const val CURRENT_VERSION\s*=\s*"([^"]+)"',log)
check(vers=='3.1.1' and vers==latest==constant,'Gradle, Changelog and CURRENT_VERSION = 3.1.1')
check(code is not None and int(code)==39,'versionCode = 39')
check(match(r'applicationId\s*=\s*"([^"]+)"',build)=='ru.krabikk.nntcschedule','Application ID unchanged')
check('version = 2' in txt('app/src/main/java/ru/krabikk/nntcschedule/v3/GroupScheduleDatabase.kt') and 'MIGRATION_1_2' in txt('app/src/main/java/ru/krabikk/nntcschedule/v3/GroupScheduleDatabase.kt'),'Room subgroup migration preserved')
styles=txt('app/src/main/java/ru/krabikk/nntcschedule/ExperimentalDesigns.kt')
entries=re.findall(r'Entry\(\s*(?:style\s*=\s*)?InterfaceStyle\.([A-Z_0-9]+)',styles)
check(len(entries)==18 and len(set(entries))==18,'18 unique developer themes registered')
extra=txt('app/src/main/java/ru/krabikk/nntcschedule/ScheduleRemainingConceptUi.kt')
check('RemainingConcepts.isSupported' in txt('app/src/main/java/ru/krabikk/nntcschedule/ScheduleHomeUi.kt'), '10 additional themes connected to ScheduleHomeUi')
check('ModalBottomSheet' in extra and '@OptIn(ExperimentalMaterial3Api::class)' in extra, 'New designs opt-in to Material3 bottom sheet')
for file,fn in [('ScheduleRoute1Ui.kt','AmberRoute1Timeline'),('ScheduleRoute2Ui.kt','Route2Timeline')]:
    code=txt('app/src/main/java/ru/krabikk/nntcschedule/'+file)
    check('dayKey = ScheduleDayStrip.keyFromScheduleDate(day.date)' in code and 'clockMillis = clockMillis' in code, file+' has date/time argument fix')
check('Разработчик: Krabikk171' in txt('app/src/main/java/ru/krabikk/nntcschedule/SettingsScreen.kt'),'Author credit preserved')
banned_dirs={'release-key','.gradle','.idea','.kotlin','.nntc_patch_backups','build','__pycache__'}
banned_names={'local.properties','keystore.properties'}
banned_ext={'.jks','.keystore','.p12','.pem','.apk','.aab'}
unsafe=[]
for p in root.rglob('*'):
    if not p.is_file():continue
    rel=p.relative_to(root)
    # apply_patch.py creates local backups. They are excluded from distribution ZIPs.
    if '.nntc_patch_backups' in rel.parts: continue
    if any(part in banned_dirs for part in rel.parts) or p.name in banned_names or p.suffix.lower() in banned_ext:
        unsafe.append(str(rel))
check(not unsafe,'No keys, caches, private local paths or APK/AAB files in source ZIP')
if unsafe: print('Unsafe paths:',unsafe[:10])
manifest_path=root/'SOURCE_MANIFEST.json'
if manifest_path.exists():
    meta=json.loads(manifest_path.read_text('utf-8'))
    hashes=meta.get('verified_source_file_hashes',{})
    matches=all((root/name).is_file() and hashlib.sha256((root/name).read_bytes()).hexdigest()==digest for name,digest in hashes.items())
    check(matches and meta.get('version')==vers,'SHA-256 source manifest integrity')
print('SUMMARY:',f'{len(failures)} failure(s). Android/Compose build not covered.')
sys.exit(1 if failures else 0)
