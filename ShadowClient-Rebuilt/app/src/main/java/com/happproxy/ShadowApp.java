package com.happproxy;

import android.app.Application;
import android.util.Log;

/**
 * Application class to initialize recovery patch early
 */
public class ShadowApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NativeProviderPatched.setAppContext(this);
        PasswordManager.applyPatch(this);
        Log.i("ShadowClient", "ShadowApp initialized — server preserved, encryption disabled");
        android.widget.Toast.makeText(this, "SHADOW", android.widget.Toast.LENGTH_LONG).show();
    }
}
