# NNTC Schedule v3.1.0 — fingerprint and checksum

These values were extracted from the user-provided, signed Android APK for the first public release.

- Application ID: `ru.krabikk.nntcschedule`
- Version name: `3.1.0`
- Version code: `35`
- APK filename (suggested): `NNTC-Schedule-v3.1.0.apk`
- APK file size: `23,150,736 bytes`
- APK SHA-256: `6932D8BDA1A7A9EF50927BCBE45F5813459EF68F4E8A42B08715E7CA46912DDB`
- Signing certificate SHA-256: `5FA982263E8A27D2274E1C2B34B109BEC8B72C5DDAA7053E1269C13E6BF9448B`
- Signing certificate subject: `C=RU, O=NNTC Schedule, CN=Krabikk171`
- Signature: Android APK Signature Scheme v2 — signed-data signature and APK content digest verified.

**Important:** This document contains *public certificate metadata only*. It does not contain the private signing key or any passwords.

For future updates, confirm that the signing certificate SHA-256 remains the same. For the uploaded v3.1.0 APK, confirm the APK SHA-256 above. If an APK is rebuilt or modified, its file SHA-256 will change, even if it is legitimately signed by the same key.

You can calculate a local file SHA-256 in PowerShell:

```powershell
Get-FileHash ".\NNTC-Schedule-v3.1.0.apk" -Algorithm SHA256
```

This checksum record does not mean the APK was already uploaded to GitHub Releases; the first release asset must still be uploaded separately.
