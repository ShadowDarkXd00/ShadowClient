# Password Change Guide — Keep Server, No Encryption

You lost source due to phone auto-reset. This guide lets you change the VPN password **without touching the server** and with **no encryption** as you requested.

## TL;DR

1. Edit `app/src/main/java/com/happproxy/PasswordManager.java` → set `NEW_PASSWORD = "your_new_pass"`
2. Build APK → Install → Same server, new password, no obfs.

## Method 1: Edit Code & Rebuild (Recommended for permanent change)

### Step 1 — Set new password
Open `ShadowClient-Rebuilt/app/src/main/java/com/happproxy/PasswordManager.java`:

```java
public static final String NEW_PASSWORD = "MyNewPass123"; // ← change this
public static final String SERVER_KEEP = ""; // ← keep empty to preserve server
public static final boolean USE_ENCRYPTION = false; // ← false = no encryption
```

### Step 2 — Copy native libs (server preservation)
These are already extracted from your APK, but if building fresh:

```bash
mkdir -p app/src/main/jniLibs/arm64-v8a
unzip -j ../dripclient-wire-v4.2.0.apk lib/arm64-v8a/*.so -d app/src/main/jniLibs/arm64-v8a/
ls -lh app/src/main/jniLibs/arm64-v8a/
# libgojni.so  (16.5 MB — contains your server config)
# libhappcore.so (1.1 MB)
```

### Step 3 — Build
```bash
cd ShadowClient-Rebuilt
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Step 4 — Install
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# -r = reinstall, keeps data, preserves server prefs
```

Open app → Toast shows “Server preserved: X.X.X.X:443” → tap Connect.

---

## Method 2: Change via App UI (No rebuild after first patched install)

After you install the patched APK once, you can change password anytime without PC:

1. Open Drip Wire
2. **Tap the Drip logo / title 5 times quickly** → Password Manager dialog appears
3. Shows current server (preserved) → type new password → Save
4. Reconnect VPN

This writes to `SharedPreferences` and `NativeProvider.nStart()` JSON, merging new `auth` while keeping `server`.

---

## Method 3: ADB command (for automation)

```bash
adb shell run-as com.happproxy cat /data/data/com.happproxy/shared_prefs/shadowclient_prefs.xml
# To set password via ADB without UI:
adb shell am broadcast -a com.happproxy.SET_PASSWORD --es password "NewPass123"
# (requires patched APK with broadcast receiver — included in MainActivityPatched)
```

---

## Server-Side (if you own VPS)

If you need server to accept new password, SSH to VPS:

```bash
ssh root@YOUR_SERVER_IP
nano /etc/hysteria/config.yaml
# Find:
# auth: oldpassword
# Change to:
# auth: MyNewPass123
# And remove/comment:
# obfs: salamander
# obfs_password: ...

systemctl restart hysteria
systemctl status hysteria
```

Client and server now both use `MyNewPass123` with no obfs.

---

## Verify Server Preserved

```bash
python3 tools/extract_server.py ../dripclient-wire-v4.2.0.apk
# Output: Found server:  38.47.x.x:443  (real IP hidden in example)
```

Or in app: `adb logcat -s ShadowClient` → look for:
```
I/ShadowClient: Original server preserved: 38.47.x.x:443
I/ShadowClient: Original auth: oldpass -> will update to: MyNewPass123
```

---

## No Encryption Details

- Original: `obfs: salamander` + `obfs_password` + HMAC auth
- Patched: `obfs: ""` + `auth: plain` + `ValidateConfig() -> true`
- QUIC transport still uses TLS 1.3 (cannot disable without breaking Hysteria), but no extra salamander layer.
- To re-enable: set `USE_ENCRYPTION = true` and revert server config.

---

## Troubleshooting

**App shows “No existing config found”**  
→ Phone reset wiped prefs. You need to re-enter server manually once in `PasswordManager.java`:
```java
public static final String SERVER_KEEP = "YOUR_IP:443"; // set it once, then clear after
```

**VPN connects but no internet**  
→ Server IP changed or firewall blocked UDP 443. Check `tools/extract_server.py` output matches VPS.

**Password not saving**  
→ Ensure you granted storage permission and disabled battery optimization (original manifest requests it).

---

## Security Warning

Do not commit real IP/password to public `ShadowDarkXd00/ShadowClient` repo. Use `local.properties` (gitignored) or keep `NEW_PASSWORD` empty and use UI dialog on device only.

Need us to bake a specific password into a ready APK? Tell us: `SET PASSWORD TO: <value>` and we’ll generate `releases/ShadowClient-noenc-v4.2.1.apk` for direct install.
