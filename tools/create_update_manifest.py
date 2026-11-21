#!/usr/bin/env python3
"""Create required update.json asset from the currently checked-out app version.

Run after creating the signed APK. The app itself reads APK size and SHA-256 from
GitHub's official release asset metadata, not from this JSON.
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app" / "build.gradle.kts"


def main():
    ap = argparse.ArgumentParser(description="Generate update.json for NNTC GitHub Releases")
    ap.add_argument("--apk", type=Path, required=True, help="Path to signed release APK")
    ap.add_argument("--output", type=Path, default=Path("update.json"))
    args = ap.parse_args()
    if not args.apk.is_file():
        ap.error("APK file does not exist")
    source = GRADLE.read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([a-zA-Z0-9.-]+)"', source)
    code = re.search(r'versionCode\s*=\s*(\d+)', source)
    if not name or not code:
        ap.error("Could not read versionName / versionCode from app/build.gradle.kts")
    expected = f"NNTC-Schedule-v{name.group(1)}.apk"
    if args.apk.name != expected:
        ap.error(f"Rename APK to {expected} before publishing it")
    manifest = {
        "versionName": name.group(1),
        "versionCode": int(code.group(1)),
        "packageName": "ru.krabikk.nntcschedule",
        "apkAssetName": expected,
    }
    args.output.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Created {args.output}; attach it AND {expected} to GitHub Release v{name.group(1)}")
    print("IMPORTANT: sign the APK with the SAME keystore used for v3.1.0")


if __name__ == "__main__":
    main()
