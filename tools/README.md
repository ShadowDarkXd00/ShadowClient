# Tools

- `extract_server.py` — recover server IP after phone reset (scans libgojni.so + prefs hints)
- `patch_apk.py` — change password while preserving server, strip encryption

Example:
```bash
python3 tools/extract_server.py dripclient-wire-v4.2.0.apk
python3 tools/patch_apk.py --password "NewPass123" --no-encrypt
```
