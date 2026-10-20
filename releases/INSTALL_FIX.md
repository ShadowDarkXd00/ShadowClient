# Install recovery — build 4.2.3

The earlier v4.2.2 recovery APK had only a JAR/v1 signature. This app targets API 36, so some Android package installers reject it. **Do not use v4.2.1 or v4.2.2.**

## Download

`ShadowClient-SHADOW-v4.2.3.apk` — signed with APK Signature Scheme v2 and v1. The v2 signature and signed-content digest were independently verified; ZIP CRCs pass; APK and DEX parse successfully.

Raw branch URL:
https://github.com/ShadowDarkXd00/ShadowClient/raw/arena/4cf1ee66-shadowclient/releases/ShadowClient-SHADOW-v4.2.3.apk

## Install

A fresh signing certificate is used, so this is not an in-place update over the original app or earlier builds. Remove those first (this clears the app's local data):

```bash
adb uninstall com.happproxy
adb install ShadowClient-SHADOW-v4.2.3.apk
```

Or uninstall Drip Wire/HappProxy in Android Settings, enable **Install unknown apps** for the browser/file manager, then install the downloaded APK.

## What this recovery build does

- Restores `classes.dex` byte-for-byte from the original v4.2.0 APK. This keeps DEX string IDs sorted and retains `MainActivity` (73 classes).
- Preserves original native libraries, including `libgojni.so`.
- It is a crash/install recovery build only; it does not change the original DEX password or UI strings.

The APK passed structural and cryptographic signature checks in this environment, but was not tested on a physical Android device. If Android still reports “App not installed,” please share the exact installer message or `adb install` output; that text will identify the remaining cause.
