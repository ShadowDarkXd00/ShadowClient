# ShadowClient Rebuilt — DripClient Wire Recovery Project

This is your **recovered source** after phone reset. You lost `/Users/alex/Documents/GitHub/DRIPCLIENT/...` due to auto-reset — this rebuild restores it from the APK you uploaded.

## What you have now

- ✅ Decompiled Java sources (73 classes) in `decompiled/`
- ✅ Rebuildable Android Studio project in `ShadowClient-Rebuilt/app/`
- ✅ Password-change logic without touching server (`PasswordManager.java`)
- ✅ Encryption disabled (`obfs=""`, `ValidateConfig` bypass)
- ✅ Original servers preserved (`libgojni.so` kept, `server` JSON untouched)

## Quick Start (Change Password, Keep Server)

### 1. Change password in code (or via UI)
Edit `app/src/main/java/com/happproxy/PasswordManager.java`:

```java
public static final String NEW_PASSWORD = "MyNewPass123"; // ← put your new password here
```

Leave `SERVER_KEEP = ""` to keep original server. Or set to `"1.2.3.4:443"` to force a specific IP.

### 2. Build

```bash
cd ShadowClient-Rebuilt
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

Or open folder in Android Studio → Run.

### 3. Install (overwrites old app, keeps data if you had backup)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open app → it auto-migrates old `server` value, only updates `auth`.

## Password Change via App UI (No PC needed after first install)

The patched APK adds a hidden setting:

1. Open Drip Wire → tap **Drip Logo 5 times** → opens `PasswordManager` dialog
2. Type new password → Save
3. Reconnect — same server, new password, no encryption

## Server Info (Preserved)

Your server is stored in:

- `NativeProvider.nStart()` → returns JSON `{"server":"YOUR_IP:PORT", "auth":"OLD_PASS", ...}`
- To view it: `adb logcat | grep ShadowClient` after launching patched app, or run:
  ```bash
  python3 ../tools/extract_server.py ../dripclient-wire-v4.2.0.apk
  ```

Do **not** push real IP to public GitHub — use `local.properties`:

```properties
SERVER_IP=38.47.x.x:443
PASSWORD=MyNewPass123
```

`.gitignore` already excludes it.

## No Encryption Mode

As you requested: “NO ENCRYPTION ETC”

- `obfs` removed (was `salamander` gecko)
- `Golib.ValidateConfig` → always true
- QUIC still encrypts transport (TLS 1.3) but no extra Hysteria obfs layer
- Re-enable by setting `PasswordManager.USE_ENCRYPTION = true`

## Project Structure

```
ShadowClient-Rebuilt/
├── app/
│   ├── build.gradle                    # AGP 8.11, compileSdk 36
│   ├── src/main/AndroidManifest.xml    # patched (no sig check)
│   └── src/main/java/com/happproxy/
│       ├── NativeProviderPatched.java  # patched JNI wrapper
│       ├── PasswordManager.java        # NEW: your password UI + logic
│       ├── feature/main/MainActivityPatched.java
│       └── service/XRayVpnServicePatched.java
├── libgojni_patched/                   # optional: Go SO patch offsets
└── gradlew                             # wrapper
```

## If you also need server-side password change

SSH to your VPS:

```bash
sudo nano /etc/hysteria/config.yaml
# change:  auth: oldpass  →  auth: MyNewPass123
# remove:  obfs: salamander
sudo systemctl restart hysteria
```

Client and server now both use plain `MyNewPass123`.

## Need us to bake password for you?

Just tell us: `SET PASSWORD TO: yourpassword` — we will generate a ready-to-install APK file in `releases/`.

## Signing

Uses debug keystore (same as original after decompile). To keep same signature for updates:

```bash
keytool -genkey -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -validity 10000
```

Original cert fingerprint saved in `docs/original_signature.txt`.

---
Questions? See `docs/REVERSE_ENGINEERING_REPORT.md` for full technical deep-dive.
