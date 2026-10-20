# Decompile Summary — 737 Strings / 73 Classes

## DEX Strings (737 unique)
Saved in `decompiled/strings.txt` — key excerpts:
- `com.dts.freefiremax` / `com.dts.freefireth` (TARGET_APP — game to accelerate)
- `SERVER_SELECT`, `TARGET_APP`, `TIME`, `MODEL`, `Proxy`, etc.
- Go bridge: `Lgolib/Golib`, `Lgo/Seq`, etc.
- LSPosed obfuscator: `DeobfuscatorHelper.getString(J, String[])` + `RandomHelper`

## Classes (73)
```
Landroid/window/OnBackInvokedCallback
Lcom/happproxy/NativeProvider          ← JNI bridge (14 natives, load happcore)
Lcom/happproxy/feature/main/MainActivity ← Login/Dashboard
Lcom/happproxy/ui/ServerActivity       ← Server selector
Lcom/happproxy/service/XRayVpnService  ← VPN + TUN
Lcom/happproxy/service/XRayProxyOnlyService
Lcom/happproxy/internal/* (15 helpers: apply, bind, cancel, close, wrap etc.)
Lcom/happproxy/ui/BackgroundView
Lgo/Seq, Lgo/Universe, etc.
Lgolib/Golib, Lgolib/Session, Lgolib/TestResult
Lorg/lsposed/lsparanoid/DeobfuscatorHelper ← string decryptor
```

## Native Libs
- `lib/arm64-v8a/libgojni.so` — 16.5 MB — Go 1.27.1 build, contains hysteria/quic/gvisor + clientConfig
- `lib/arm64-v8a/libhappcore.so` — 1.1 MB — original C++ helper

## Resources
- `res/*.xml` — binary XML (45 layouts) — decompiled to `decompiled/res_raw/`
- `resources.arsc` — 7960 bytes — colors, drawables, strings (DRIP WIRE theme)
- `AndroidManifest.xml` — 4788 bytes binary → decoded to `decompiled/manifest/AndroidManifest.xml`

## Obfuscation
- DEX strings encrypted via `DeobfuscatorHelper.getString(long, String[])` with rotating key table `onMeasure` (misnamed field holding String[] table)
- Go strings not obfuscated — `libgojni.so` readable for server/auth keys

## Password & Server Locations
- Password: `json:"auth"` / `json:"Auth"` in golib.clientConfig (Go), persisted via `NativeProvider.nStart()` JSON
- Server: `json:"server"` in same struct, preserved via `PasswordManager` merge logic
- No hardcoded IP in APK — runtime config from server list API (see `nGetServers`)

## Patches Applied
- `PasswordManager.java` — merge new auth, keep server, clear obfs
- `NativeProviderPatched.java` — bypass nLogin validation
- `MainActivityPatched.java` — 5-tap logo → password dialog

See `docs/REVERSE_ENGINEERING_REPORT.md` for full flow.
