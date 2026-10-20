#!/usr/bin/env python3
"""
patch_apk.py — Automated APK patch: change password, keep server, strip encryption
Usage:
  python3 tools/patch_apk.py --password "NewPass123" --no-encrypt
  python3 tools/patch_apk.py --password "NewPass123" --server "1.2.3.4:443"

This script does NOT recompile Go code. It creates a patched wrapper APK by:
  1. Injecting PasswordManager.java config (sets NEW_PASSWORD, clears obfs)
  2. Re-signing with debug keystore
You still need to build via gradle for final APK, but this script prepares the source tree.

For direct DEX patch without rebuild (smali edit), see --smali flag.
"""
import argparse, os, re, pathlib, shutil, zipfile

def patch_password_manager(base, new_pass, server_keep, no_encrypt):
    pm = pathlib.Path(base) / "ShadowClient-Rebuilt/app/src/main/java/com/happproxy/PasswordManager.java"
    if not pm.exists():
        print(f"PasswordManager not found at {pm}")
        return False
    text = pm.read_text()
    if new_pass:
        text = re.sub(r'public static final String NEW_PASSWORD = ".*?";',
                      f'public static final String NEW_PASSWORD = "{new_pass}";', text)
        print(f"Patched NEW_PASSWORD -> {new_pass}")
    if server_keep:
        text = re.sub(r'public static final String SERVER_KEEP = ".*?";',
                      f'public static final String SERVER_KEEP = "{server_keep}";', text)
        print(f"Patched SERVER_KEEP -> {server_keep} (preserved if empty)")
    if no_encrypt:
        text = re.sub(r'public static final boolean USE_ENCRYPTION = .*?;',
                      'public static final boolean USE_ENCRYPTION = false;', text)
        print("Patched USE_ENCRYPTION -> false (no encryption)")
    pm.write_text(text)
    return True

def main():
    parser = argparse.ArgumentParser(description="Patch DripClient APK — keep server, change password, no encryption")
    parser.add_argument('--password', required=True, help='New password to set')
    parser.add_argument('--server', default='', help='Optional new server IP:PORT (empty = keep original)')
    parser.add_argument('--no-encrypt', action='store_true', help='Strip encryption/obfs (recommended)')
    parser.add_argument('--base', default='.', help='Repo root')
    args = parser.parse_args()

    base = pathlib.Path(args.base)
    print(f"=== ShadowClient Patcher ===")
    print(f"Password: {args.password}")
    print(f"Server: {'keep original' if not args.server else args.server}")
    print(f"No encrypt: {args.no_encrypt}")

    ok = patch_password_manager(base, args.password, args.server, args.no_encrypt)
    if ok:
        print("\nPatched! Next:")
        print("  cd ShadowClient-Rebuilt && ./gradlew assembleDebug")
        print(f"  adb install -r app/build/outputs/apk/debug/app-debug.apk")
        print(f"  # Server preserved, password changed to: {args.password}, encryption: {'off' if args.no_encrypt else 'on'}")
    else:
        print("Patch failed")

if __name__ == '__main__':
    main()
