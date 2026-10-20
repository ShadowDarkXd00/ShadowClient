# ShadowClient — DripClient Wire Recovery

> **Recovered after phone auto-reset wiped source & server**  
> Original repo only had `dripclient-wire-v4.2.0.apk` — now fully decompiled, rebuildable, password-changeable, **server preserved, no encryption** as requested.

## 🚀 Quick Start — Change Password (Keep Server)

```bash
# 1. Set new password (edit file)
nano ShadowClient-Rebuilt/app/src/main/java/com/happproxy/PasswordManager.java
# change: NEW_PASSWORD = "MyNewPass123"

# 2. Or use helper script
python3 tools/patch_apk.py --password "MyNewPass123" --no-encrypt

# 3. Build
cd ShadowClient-Rebuilt
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

App will toast `Server preserved: X.X.X.X:443` — same server, new password, no obfs.

Or after installing patched APK, **tap logo 5× → password dialog → type new password** — no PC needed.

## 📦 What’s Inside

| Folder | Purpose |
|--------|---------|
| `dripclient-wire-v4.2.0.apk` | Original upload (6.5 MB) |
| `decompiled/` | 73 classes as smali + manifest + 737 strings + resources |
| `docs/REVERSE_ENGINEERING_REPORT.md` | Full technical deep-dive (server/password/encryption) |
| `ShadowClient-Rebuilt/` | Rebuildable Android Studio project (AGP 8.11, SDK 36, ndk arm64-v8a) |
| `tools/extract_server.py` | Recover server IP after reset |
| `tools/patch_apk.py` | Automate password patch |

## 🔐 Password & Server

- **Password** = `auth` field in Hysteria2 JSON config (not hardcoded — via `NativeProvider.nStart()`)
- **Server** = `server` field in same JSON — **preserved** by `PasswordManager` merge logic
- **No Encryption** = `obfs: ""` cleared both client & server, `ValidateConfig` bypassed

See `ShadowClient-Rebuilt/PASSWORD_CHANGE_GUIDE.md` for 3 methods (code / UI / ADB).

## 🛠 Build Requirements

- Android Studio Hedgehog+ / AGP 8.11.1 / Gradle 8.13
- JDK 17
- NDK (for arm64-v8a, but SOs are prebuilt — NDK optional)

## 📖 Docs

- `docs/REVERSE_ENGINEERING_REPORT.md` — where server/password live, how encryption removed
- `docs/DECOMPILE_SUMMARY.md` — 73 classes overview
- `ShadowClient-Rebuilt/README.md` — rebuild steps
- `ShadowClient-Rebuilt/PASSWORD_CHANGE_GUIDE.md` — user-facing guide

## 🔑 Extract Server After Reset

```bash
python3 tools/extract_server.py dripclient-wire-v4.2.0.apk
# and live after install:
adb logcat -s ShadowClient | grep preserved
```

## ⚠️ Security

Do NOT commit real IP/password to this public repo. Use `local.properties` (gitignored). Current `NEW_PASSWORD` is empty placeholder — set locally or via UI dialog.

## Need Ready APK?

Tell us `SET PASSWORD TO: <yourpassword>` — we’ll generate `releases/ShadowClient-noenc-v4.2.1.apk` ready to install.

---
*Decompiled with androguard 4.1.4 + Go symbol analysis. Server IPs preserved, encryption stripped as requested.*
