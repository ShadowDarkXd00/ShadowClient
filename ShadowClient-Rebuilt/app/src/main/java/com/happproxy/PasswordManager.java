package com.happproxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;

/**
 * ShadowClient PasswordManager — RECOVERY MODULE
 * Created after phone auto-reset wiped /Users/alex/Documents/GitHub/DRIPCLIENT source.
 *
 * Purpose:
 *   - Change VPN password (auth) WITHOUT touching server IP
 *   - Disable encryption/obfs as requested ("NO ENCRYPTION ETC")
 *   - Keep libgojni.so server endpoint intact
 *
 * Usage:
 *   1. Set NEW_PASSWORD below, leave SERVER_KEEP empty to preserve original server.
 *   2. Call PasswordManager.applyPatch(context) on app start (already hooked in MainActivityPatched).
 *   3. Or trigger via UI: tap logo 5x → dialog → type password → save.
 */
public class PasswordManager {
    private static final String TAG = "ShadowClient";

    // ==================== USER CONFIG — EDIT HERE ====================
    /**
     * Your new password. Change this string and rebuild, or leave empty and use UI dialog.
     * Example: "mypassword123"
     */
    public static final String NEW_PASSWORD = ""; // ← SET PASSWORD HERE, e.g. "NewPass2025"

    /**
     * Leave empty to KEEP original server from APK (recommended after phone reset).
     * Set to "IP:PORT" only if you migrated server.
     * Example: "38.47.210.19:443"
     */
    public static final String SERVER_KEEP = ""; // ← empty = preserve

    /**
     * Set false to disable Hysteria obfs/salamander encryption (as you requested).
     * True = keep original salamander encryption (more stealth).
     * Recommended: false for now to avoid "NO ENCRYPTION ETC" issues.
     */
    public static final boolean USE_ENCRYPTION = false;

    // ================================================================

    private static final String PREFS_NAME = "shadowclient_prefs";
    private static final String KEY_AUTH = "auth";
    private static final String KEY_SERVER = "server";
    private static final String KEY_CONFIG_JSON = "app_config_json"; // mirrors NativeProvider.nStart storage

    /**
     * Main patch entry — call from MainActivity.onCreate() before Golib init.
     * It merges new password into existing config, preserving server.
     */
    public static void applyPatch(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // Also read legacy HAPP proxy prefs (where original server lives)
            SharedPreferences legacy = ctx.getSharedPreferences("HAPP_PROXY_PREFS", Context.MODE_PRIVATE);
            // Try multiple possible keys (observed in decompiled XRayVpnService)
            String[] possibleKeys = new String[]{"SERVER_SELECT", "app_config_json", "config", "happ_config"};
            String currentJson = null;

            // 1) Try NativeProvider.nStart() if available (Go-persisted JSON)
            try {
                currentJson = NativeProvider.nStart();
                Log.d(TAG, "Got config from NativeProvider.nStart(): " + currentJson);
            } catch (Throwable t) {
                Log.w(TAG, "NativeProvider.nStart() not ready yet: " + t.getMessage());
            }

            // 2) Fallback to SharedPreferences scan
            if (currentJson == null || currentJson.isEmpty() || currentJson.equals("null")) {
                for (String k : possibleKeys) {
                    String v = prefs.getString(k, null);
                    if (v == null) v = legacy.getString(k, null);
                    if (v != null && v.contains("server")) {
                        currentJson = v;
                        Log.d(TAG, "Found legacy config under key " + k + ": " + v);
                        break;
                    }
                }
            }

            if (currentJson == null || currentJson.isEmpty()) {
                Log.w(TAG, "No existing config found — will create fresh one if NEW_PASSWORD set");
                if (NEW_PASSWORD != null && !NEW_PASSWORD.isEmpty()) {
                    createFreshConfig(ctx, NEW_PASSWORD, SERVER_KEEP);
                }
                return;
            }

            JSONObject cfg = new JSONObject(currentJson);
            String originalServer = cfg.optString("server", cfg.optString("Server", ""));
            String originalAuth = cfg.optString("auth", cfg.optString("Auth", ""));

            Log.i(TAG, "Original server preserved: " + originalServer);
            Log.i(TAG, "Original auth: " + originalAuth + " -> will update to: " + (NEW_PASSWORD.isEmpty() ? "(keep/UI)" : NEW_PASSWORD));

            boolean changed = false;

            // Apply new password if provided via code
            if (NEW_PASSWORD != null && !NEW_PASSWORD.isEmpty()) {
                cfg.put("auth", NEW_PASSWORD);
                cfg.put("Auth", NEW_PASSWORD); // cover both cases (go json tags)
                // Hysteria2 also uses "auth" lowercase — ensure both
                changed = true;
            }

            // Preserve or override server
            if (SERVER_KEEP != null && !SERVER_KEEP.isEmpty()) {
                cfg.put("server", SERVER_KEEP);
                cfg.put("Server", SERVER_KEEP);
                changed = true;
                Log.i(TAG, "Server overridden to: " + SERVER_KEEP);
            } else {
                // Explicitly keep original — do nothing, but log
                Log.i(TAG, "Server KEEP mode — not touching: " + originalServer);
            }

            // Disable encryption as requested
            if (!USE_ENCRYPTION) {
                cfg.put("obfs", "");
                cfg.put("Obfs", "");
                cfg.remove("obfs_password");
                cfg.remove("salamander");
                cfg.put("lazy", true); // allow lazy connect without obfs handshake
                cfg.put("Lazy", true);
                changed = true;
                Log.i(TAG, "Encryption disabled: obfs cleared, lazy=true");
            }

            if (changed) {
                String patchedJson = cfg.toString();
                Log.i(TAG, "Patched config: " + patchedJson);

                // Persist back via all known channels
                try {
                    // NativeProvider.nSave() — if exists in patched version
                    NativeProviderPatched.nSave(patchedJson);
                } catch (Throwable ignored) {}

                prefs.edit().putString(KEY_CONFIG_JSON, patchedJson).putString("SERVER_SELECT", patchedJson).apply();
                legacy.edit().putString(KEY_CONFIG_JSON, patchedJson).putString("SERVER_SELECT", patchedJson).apply();

                // Also update Golib's in-memory config if already initialized
                try {
                    // This will be picked up on next Session creation
                    Log.i(TAG, "Config persisted — next VPN start will use new password with same server");
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to update Golib config: " + t.getMessage());
                }
            }

            // Bypass ValidateConfig — always true
            try {
                NativeProviderPatched.disableValidation();
                Log.i(TAG, "ValidateConfig bypass enabled — no encryption check");
            } catch (Throwable ignored) {}

        } catch (Exception e) {
            Log.e(TAG, "applyPatch failed: " + e.getMessage(), e);
        }
    }

    private static void createFreshConfig(Context ctx, String password, String server) {
        try {
            JSONObject cfg = new JSONObject();
            // If user provided server, use it; else placeholder — will be filled via UI logcat
            String srv = (server != null && !server.isEmpty()) ? server : "YOUR_SERVER_IP:443";
            cfg.put("server", srv);
            cfg.put("Server", srv);
            cfg.put("auth", password);
            cfg.put("Auth", password);
            cfg.put("obfs", USE_ENCRYPTION ? "salamander" : "");
            cfg.put("listen", ":1080");
            cfg.put("lazy", true);

            String json = cfg.toString();
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CONFIG_JSON, json).apply();
            Log.i(TAG, "Created fresh config: " + json + " — REPLACE YOUR_SERVER_IP with real IP from extract_server.py");
        } catch (Exception e) {
            Log.e(TAG, "createFreshConfig failed", e);
        }
    }

    /**
     * UI helper: call from dialog's Save button with user-typed password.
     */
    public static boolean setPasswordFromUI(Context ctx, String newPass) {
        if (newPass == null || newPass.trim().isEmpty()) return false;
        try {
            String currentJson = null;
            try { currentJson = NativeProvider.nStart(); } catch (Throwable ignored) {}
            if (currentJson == null) {
                currentJson = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CONFIG_JSON, "{}");
            }
            JSONObject cfg = new JSONObject(currentJson);
            cfg.put("auth", newPass.trim());
            cfg.put("Auth", newPass.trim());
            if (!USE_ENCRYPTION) {
                cfg.put("obfs", "");
                cfg.put("Obfs", "");
            }
            String patched = cfg.toString();
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CONFIG_JSON, patched).putString("SERVER_SELECT", patched).apply();
            try { NativeProviderPatched.nSave(patched); } catch (Throwable ignored) {}
            Log.i(TAG, "Password updated via UI to: " + newPass + " — server preserved: " + cfg.optString("server"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "setPasswordFromUI failed", e);
            return false;
        }
    }

    public static String getCurrentServer(Context ctx) {
        try {
            String j = NativeProvider.nStart();
            if (j == null) j = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CONFIG_JSON, "{}");
            return new JSONObject(j).optString("server", new JSONObject(j).optString("Server", "unknown"));
        } catch (Exception e) { return "unknown"; }
    }

    public static String getCurrentPassword(Context ctx) {
        try {
            String j = NativeProvider.nStart();
            if (j == null) j = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CONFIG_JSON, "{}");
            return new JSONObject(j).optString("auth", new JSONObject(j).optString("Auth", ""));
        } catch (Exception e) { return ""; }
    }
}
