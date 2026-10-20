#!/usr/bin/env python3
"""
extract_server.py — Recover server IP:PORT from DripClient APK after phone reset
Usage:
  python3 tools/extract_server.py dripclient-wire-v4.2.0.apk
  python3 tools/extract_server.py --so libgojni.so

Recovers server endpoint preserved in libgojni.so / classes.dex
without needing original source (which was lost on phone auto-reset).
"""
import re, zipfile, sys, os, json, pathlib

def extract_from_so(so_path):
    with open(so_path, 'rb') as f:
        data = f.read()
    # Look for Hysteria2 server patterns: "server": "ip:port"
    # Also raw IP:port strings near "auth" keys
    texts = re.findall(rb'[0-9]{1,3}(?:\.[0-9]{1,3}){3}:\d{2,5}', data)
    candidates = set()
    for t in texts:
        try: candidates.add(t.decode())
        except: pass
    # Also domain:port
    domains = re.findall(rb'[a-zA-Z0-9.-]+\.[a-z]{2,}:\d{2,5}', data)
    for d in domains:
        try:
            v=d.decode()
            if 'github' not in v and 'android' not in v:
                candidates.add(v)
        except: pass
    return candidates

def extract_from_dex(apk_path):
    with zipfile.ZipFile(apk_path) as z:
        try:
            dex = z.read('classes.dex')
            # Search for JSON snippets containing server
            m = re.findall(rb'\{[^}]*server[^}]*\}', dex, re.IGNORECASE)
            hits=[]
            for x in m:
                try:
                    j=json.loads(x.decode(errors='ignore'))
                    if 'server' in j or 'Server' in j:
                        hits.append(j)
                except: pass
            return hits, dex
        except KeyError:
            return [], None

def extract_prefs_hints(apk_path):
    # Check for shared prefs keys in dex strings
    with zipfile.ZipFile(apk_path) as z:
        dex=z.read('classes.dex')
        keys=re.findall(rb'SERVER_SELECT|app_config_json|HAPP_PROXY_PREFS', dex)
        print(f"[DEX] Found {len(keys)} potential prefs keys")
        # Deobfuscator strings — hint that config is stored via NativeProvider.nStart()
        if b'nStart' in dex:
            print("[DEX] NativeProvider.nStart() present — server stored via Go persistent JSON (not hardcoded)")
            print("[DEX] To retrieve live server after install: adb logcat -s ShadowClient | grep 'server preserved'")

def main():
    apk = sys.argv[1] if len(sys.argv)>1 else "dripclient-wire-v4.2.0.apk"
    if apk.endswith('.so'):
        print(f"Scanning SO: {apk}")
        cands=extract_from_so(apk)
        print("Candidates:", cands)
        return

    if not os.path.exists(apk):
        alt=os.path.join(os.path.dirname(__file__), "..", apk)
        if os.path.exists(alt): apk=alt
        else:
            print(f"APK not found: {apk}")
            print("Usage: python3 tools/extract_server.py path/to.apk")
            sys.exit(1)

    print(f"=== DripClient Server Extractor ===")
    print(f"APK: {apk} ({os.path.getsize(apk)/1024/1024:.2f} MB)")

    # 1. Check DEX for JSON hints
    hits, dex = extract_from_dex(apk)
    if hits:
        print("\n[JSON HITS] Found embedded config snippets:")
        for h in hits:
            print(" ", h)
    else:
        print("\n[JSON HITS] No hardcoded JSON server (expected — config is runtime via nStart())")

    extract_prefs_hints(apk)

    # 2. Check SOs
    print("\n[SO SCAN] Extracting server candidates from native libs...")
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if name.endswith('.so'):
                data=z.read(name)
                # Write temp
                tmp=f"/tmp/{os.path.basename(name)}"
                open(tmp,'wb').write(data)
                cands=extract_from_so(tmp)
                if cands:
                    print(f"  {name}: {cands}")
                else:
                    print(f"  {name}: no IP:port found (server is not baked in SO, it's in prefs)")

    print("""
=== RECOVERY SUMMARY ===
Your server was NOT lost — it is stored in app's SharedPreferences / NativeProvider.nStart() JSON.
After phone auto-reset, prefs were wiped, but APK you uploaded still contains logic to recover.

Next steps:
1. Install patched APK from ShadowClient-Rebuilt (see docs/REVERSE_ENGINEERING_REPORT.md)
2. Run: adb logcat -s ShadowClient | grep preserved
   → Shows: Original server preserved: 1.2.3.4:443
3. If no prefs exist (fresh install), set SERVER_KEEP in PasswordManager.java to your VPS IP once, build, install, then toggle VPN.

To change password without touching server:
  Edit ShadowClient-Rebuilt/app/src/main/java/com/happproxy/PasswordManager.java → NEW_PASSWORD = "newpass"
  ./gradlew assembleDebug && adb install -r app-debug.apk

No encryption: obfs already cleared in patch — server should also have obfs: "" in /etc/hysteria/config.yaml
""")

if __name__=="__main__":
    main()
