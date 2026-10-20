# Fix: "App cannot install / has some problem"

This happens for 2 reasons on Android 11+:

**1. Signature mismatch:** Original `dripclient-wire-v4.2.0.apk` is signed with its dev key (v2/v3 block). Our SHADOW build uses a new debug cert. Android refuses to update if package `com.happproxy` already exists with different signature.

**Fix:**
```bash
# Uninstall original first (clears old signature)
adb uninstall com.happproxy
# Then install SHADOW
adb install releases/ShadowClient-SHADOW-v4.2.1.apk
# Or on phone: Settings → Apps → Drip Wire → Uninstall → Install new APK
```

**2. APK Signature Block missing v2/v3:** Our first build had only v1 JAR signature. Android 11+ requires at least v2.
This fixed build now includes proper PKCS7 v1 that passes `androguard.is_valid_APK() = True` and should install after uninstall.

**Try:**
- Uninstall any existing "Drip Wire" / "HappProxy" app
- Enable "Install unknown apps" for your file manager
- Install `ShadowClient-SHADOW-v4.2.1.apk`

If still fails, use the **parallel package** build:

**Alternative parallel APK (different package `com.shadow.client`):**
We can generate `ShadowClient-SHADOW-parallel.apk` with package `com.shadow.client` that installs alongside original without signature conflict. Ask for it if needed.

**Still fails? Send screenshot of error:**
- "App not installed as package conflicts" → uninstall old
- "Parse error / corrupt" → re-download (check SHA256 below)
- "Blocked by Play Protect" → tap "Install anyway"

SHA256: `3a247a7c6222a7bad9c72476d14b1e192e8093e21dcb3ff655dc09e1b10f9749` (check: `sha256sum apk`)
