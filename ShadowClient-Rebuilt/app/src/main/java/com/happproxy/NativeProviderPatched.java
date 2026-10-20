package com.happproxy;

import android.util.Log;

/**
 * Patched NativeProvider — wraps original NativeProvider's JNI calls
 * to allow password change and encryption removal while keeping server.
 *
 * Original NativeProvider (decompiled in decompiled/com_happproxy_NativeProvider;.smali.txt):
 *   static { System.loadLibrary("happcore"); }
 *   public static native String nStart();
 *   public static native int nLogin(String key);
 *   public static native boolean nIsAuth();
 *   ... (all natives backed by libgojni.so / libhappcore.so)
 *
 * This patched version:
 *   - Intercepts nSave / nStart to inject no-encryption config
 *   - Bypasses nLogin failure so any password works (server still validates, but we keep original server)
 *   - Provides nSave() helper not in original (persists merged JSON)
 */
public class NativeProviderPatched {
    private static final String TAG = "ShadowClient";

    static {
        try {
            System.loadLibrary("happcore");
            System.loadLibrary("gojni"); // Go bridge, contains Golib
            Log.i(TAG, "Native libraries loaded (happcore, gojni)");
        } catch (Throwable t) {
            Log.e(TAG, "loadLibrary failed: " + t.getMessage(), t);
        }
    }

    // ---- Original natives (passthrough) ----
    public static native String nStart();
    public static native String nGetServers();
    public static native String nGetFeatures();
    public static native boolean nIsAuth();
    public static native int nLogin(String key);
    public static native void nLogout();
    public static native int nCheck(String server, int port);
    public static native String nGetKey();

    // ---- Patched helpers ----

    /**
     * Save merged config JSON (new password + preserved server + no obfs).
     * Original app had no explicit nSave — it used SharedPreferences + Golib._NewSession.
     * We add this to cleanly persist after PasswordManager merges.
     */
    public static void nSave(String json) {
        try {
            // Try Golib path first if available
            // Golib expects config string via Session constructor, but we can also store in prefs
            android.content.Context ctx = getAppContext();
            if (ctx != null) {
                ctx.getSharedPreferences("shadowclient_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("app_config_json", json).putString("SERVER_SELECT", json).apply();
                ctx.getSharedPreferences("HAPP_PROXY_PREFS", android.content.Context.MODE_PRIVATE)
                        .edit().putString("app_config_json", json).putString("SERVER_SELECT", json).apply();
                Log.i(TAG, "nSave persisted: " + json);
            }
            // Also try to call original's internal storage via reflection if exists
            try {
                java.lang.reflect.Method m = NativeProvider.class.getMethod("nSave", String.class);
                m.invoke(null, json);
                Log.i(TAG, "NativeProvider.nSave reflected");
            } catch (Throwable ignored) {}
        } catch (Exception e) {
            Log.e(TAG, "nSave failed: " + e.getMessage(), e);
        }
    }

    /**
     * Bypass auth validation — makes nLogin always succeed.
     * Call before any login attempt. Server IP remains same.
     * WARNING: This only bypasses client-side check. Server-side Hysteria auth still happens,
     * but since we keep original server and user controls server config, they can set any matching password.
     */
    public static void disableValidation() {
        // Java-side bypass is done via hooking; actual Go validation bypass would require SO patch
        // For now we set a flag that MainActivityPatched checks to skip nLogin error dialog
        sValidationDisabled = true;
        Log.i(TAG, "Validation disabled — any password will be accepted client-side");
    }

    public static boolean sValidationDisabled = false;

    /**
     * Patched nLogin that preserves server and strips obfs before calling real nLogin.
     * @param key new password
     * @return 0 on success (always when validation disabled)
     */
    public static int nLoginPatched(String key) {
        if (sValidationDisabled) {
            Log.i(TAG, "Patched login with key: " + key + " — bypassed, returning success");
            // Still persist the key so XRayVpnService picks it up
            try {
                String cur = nStart();
                if (cur != null && !cur.isEmpty()) {
                    org.json.JSONObject obj = new org.json.JSONObject(cur);
                    obj.put("auth", key);
                    obj.put("Auth", key);
                    obj.put("obfs", "");
                    nSave(obj.toString());
                }
            } catch (Throwable t) {
                Log.w(TAG, "nLoginPatched persist failed: " + t.getMessage());
            }
            return 0; // success
        }
        // fallback to real
        return NativeProvider.nLogin(key);
    }

    /**
     * Strip encryption from any config JSON before passing to Golib.
     */
    public static String stripEncryption(String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            o.put("obfs", "");
            o.put("Obfs", "");
            o.remove("obfs_password");
            return o.toString();
        } catch (Exception e) {
            return json;
        }
    }

    // Helper to get context without passing it everywhere (set in Application.onCreate)
    private static android.content.Context sAppContext;
    public static void setAppContext(android.content.Context c) { sAppContext = c.getApplicationContext(); }
    public static android.content.Context getAppContext() { return sAppContext; }
}
