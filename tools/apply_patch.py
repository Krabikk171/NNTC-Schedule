#!/usr/bin/env python3
"""Apply an NNTC patch defensively: verify hashes *before* modifying files."""
import argparse, pathlib, zipfile, json, hashlib, shutil
from datetime import datetime

def digest(data):return hashlib.sha256(data).hexdigest()

def main():
    p=argparse.ArgumentParser(description='Safely apply an NNTC patch ZIP')
    p.add_argument('--patch',required=True)
    p.add_argument('--project',required=True,help='Root containing app/build.gradle.kts')
    args=p.parse_args()
    root=pathlib.Path(args.project).resolve()
    if not (root/'app/build.gradle.kts').exists():raise SystemExit('Invalid Android project directory: '+str(root))
    with zipfile.ZipFile(args.patch) as z:
        manifest=json.loads(z.read('NNTC_PATCH_MANIFEST.json'))
        if manifest.get('format')!='nntc-patch-v1':raise SystemExit('Unsupported patch format')
        todo=[]
        for rec in manifest['changes']:
            name=rec['path'];relative=pathlib.PurePosixPath(name)
            if relative.is_absolute() or '..' in relative.parts:raise SystemExit('Unsafe path: '+name)
            dest=(root/name).resolve()
            if root not in dest.parents:raise SystemExit('Unsafe destination: '+str(dest))
            data=z.read(name)
            if digest(data)!=rec['new_sha256']:raise SystemExit('Patch content hash mismatch: '+name)
            before=digest(dest.read_bytes()) if dest.exists() else None
            if before==rec['new_sha256']:continue
            if before!=rec['previous_sha256']:
                raise SystemExit('CONFLICT: '+name+' differs from patch base. Do not overwrite another chat\'s changes. Merge manually.')
            todo.append((dest,data))
        if not todo:
            print('Patch already applied; nothing to change.')
            return
        backup=root/'.nntc_patch_backups'/datetime.now().strftime('%Y%m%d_%H%M%S')
        for dest,data in todo:
            rel=dest.relative_to(root)
            if dest.exists():
                backup_file=backup/rel;backup_file.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy2(dest,backup_file)
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(data)
        print('Applied',len(todo),'files; backup of replaced files:',backup)
if __name__=='__main__':main()
