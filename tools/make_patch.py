#!/usr/bin/env python3
"""NNTC patch builder: compare source trees or ZIP snapshots by SHA-256.
No Android SDK, Gradle, or third-party packages are required.
"""
import argparse, hashlib, json, pathlib, zipfile
from datetime import datetime, timezone

IGNORE_DIRS={'.gradle','.idea','.git','.kotlin','build','.cxx','.nntc_patch_backups','release-key','__pycache__'}
IGNORE_FILES={'local.properties','keystore.properties','output-metadata.json'}
IGNORE_SUFFIXES={'.apk','.aab','.jks','.keystore','.p12','.pem','.key','.pyc'}

def allowed(path):
    p=pathlib.PurePosixPath(path)
    return bool(p.parts) and not p.is_absolute() and '..' not in p.parts and not any(x in IGNORE_DIRS for x in p.parts) and p.name not in IGNORE_FILES and p.suffix.lower() not in IGNORE_SUFFIXES

def sha(data):return hashlib.sha256(data).hexdigest()

def snapshot(path):
    p=pathlib.Path(path)
    entries={}
    if p.is_file() and p.suffix.lower()=='.zip':
        with zipfile.ZipFile(p) as z:
            for i in z.infolist():
                if i.is_dir():continue
                name=i.filename.replace('\\','/')
                if name.startswith('Projects/NNTCSchedule/'):
                    name=name[len('Projects/NNTCSchedule/'):]
                elif name.startswith('NNTCSchedule/'):
                    name=name[len('NNTCSchedule/'):]
                else: continue
                if allowed(name):entries[name]=z.read(i)
    elif p.is_dir():
        if (p/'NNTCSchedule'/'app').exists():p=p/'NNTCSchedule'
        for f in p.rglob('*'):
            if not f.is_file():continue
            name=f.relative_to(p).as_posix()
            if allowed(name):entries[name]=f.read_bytes()
    else:raise ValueError('Expected project folder or ZIP snapshot: '+str(path))
    return entries

def main():
    parser=argparse.ArgumentParser(description='Make safe file-by-file NNTC update ZIP')
    parser.add_argument('--base',required=True,help='Previous project directory or ZIP')
    parser.add_argument('--updated',required=True,help='New project directory or ZIP')
    parser.add_argument('--out',required=True,help='Patch ZIP path')
    args=parser.parse_args()
    base=snapshot(args.base)
    updated=snapshot(args.updated)
    removed=sorted(set(base)-set(updated))
    if removed:
        raise SystemExit('Refusing implicit deletions: '+', '.join(removed[:15]))
    changed=[]
    for path,data in sorted(updated.items()):
        before=base.get(path)
        if before!=data:
            changed.append({'path':path,'previous_sha256':sha(before) if before is not None else None,
                            'new_sha256':sha(data)})
    manifest={'format':'nntc-patch-v1','created_utc':datetime.now(timezone.utc).isoformat(),
              'changed_count':len(changed),'changes':changed,'excludes':sorted(IGNORE_DIRS|IGNORE_FILES)}
    out=pathlib.Path(args.out);out.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        z.writestr('NNTC_PATCH_MANIFEST.json',json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
        for rec in changed:z.writestr(rec['path'],updated[rec['path']])
    print('Created',out,'with',len(changed),'changed/new files')
    print('Changed paths:')
    for rec in changed:print(' -',rec['path'])
if __name__=='__main__':main()
