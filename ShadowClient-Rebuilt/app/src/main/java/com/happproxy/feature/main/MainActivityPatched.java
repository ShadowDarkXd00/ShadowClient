package com.happproxy.feature.main;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;
import com.happproxy.NativeProviderPatched;
import com.happproxy.PasswordManager;
import com.happproxy.R;

/**
 * Patched MainActivity — recovers login flow after phone reset.
 * Original MainActivity (decompiled in decompiled/com_happproxy_feature_main_MainActivity;.smali.txt)
 * had obfuscated login via DeobfuscatorHelper.getString(...) and called NativeProvider.nLogin().
 *
 * Patched behavior:
 *   - On launch, restores server from backup (if present) and shows PasswordManager dialog option
 *   - Allows any password when USE_ENCRYPTION=false
 *   - Long-press logo 5x to trigger password change UI
 */
public class MainActivityPatched extends android.app.Activity {
    private static final String TAG = "ShadowClient";
    private EditText inputAccessKey;
    private int logoTapCount = 0;
    private long lastTapTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NativeProviderPatched.setAppContext(this);

        // Apply recovery patch: keep server, disable encryption, prepare password
        PasswordManager.applyPatch(this);
        NativeProviderPatched.disableValidation();
        Toast.makeText(this, "SHADOW", Toast.LENGTH_LONG).show();

        // Try to load original layout — same as decompiled MainActivity
        try {
            setContentView(R.layout.activity_dashboard); // recovered from res/1b.xml is dashboard
        } catch (Throwable t) {
            // Fallback: create minimal login UI programmatically if resources missing
            Log.w(TAG, "Original layout missing, creating fallback UI: " + t.getMessage());
            createFallbackUI();
            return;
        }

        // Bind original input (id: input_access_key from decompiled resources)
        try {
            inputAccessKey = findViewById(R.id.input_access_key);
            if (inputAccessKey != null) {
                String savedPass = PasswordManager.getCurrentPassword(this);
                if (savedPass != null && !savedPass.isEmpty()) {
                    inputAccessKey.setText(savedPass);
                }
                // Show hint about server preservation
                String srv = PasswordManager.getCurrentServer(this);
                Toast.makeText(this, "SHADOW", Toast.LENGTH_LONG).show();
                Log.i(TAG, "Current server: " + srv + " password: " + savedPass);
            }

            // Hook logo to trigger password manager (5 taps)
            android.view.View logo = findViewById(R.id.icon_action);
            if (logo == null) logo = findViewById(R.id.title_bar);
            if (logo != null) {
                logo.setOnClickListener(v -> {
                    long now = System.currentTimeMillis();
                    if (now - lastTapTime > 2000) logoTapCount = 0;
                    logoTapCount++;
                    lastTapTime = now;
                    if (logoTapCount >= 5) {
                        logoTapCount = 0;
                        showPasswordDialog();
                    }
                });
            }

            // Hook login button
            android.view.View btnLogin = findViewById(R.id.btn_login);
            if (btnLogin != null) {
                btnLogin.setOnClickListener(v -> doLogin());
            }

        } catch (Exception e) {
            Log.e(TAG, "Patched onCreate binding failed", e);
        }
    }

    private void createFallbackUI() {
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("ShadowClient — Drip Wire Recovery");
        Toast.makeText(this, "SHADOW", Toast.LENGTH_LONG).show();
        title.setTextSize(20);
        root.addView(title);
        android.widget.TextView srvLabel = new android.widget.TextView(this);
        srvLabel.setText("Server: " + PasswordManager.getCurrentServer(this) + " (preserved)");
        srvLabel.setPadding(0, 24, 0, 24);
        root.addView(srvLabel);
        inputAccessKey = new EditText(this);
        inputAccessKey.setHint("Enter new password (access key)");
        inputAccessKey.setText(PasswordManager.getCurrentPassword(this));
        root.addView(inputAccessKey);
        android.widget.Button btn = new android.widget.Button(this);
        btn.setText("Save Password & Connect");
        btn.setOnClickListener(v -> doLogin());
        root.addView(btn);
        android.widget.Button btn2 = new android.widget.Button(this);
        btn2.setText("Change Password (Advanced)");
        btn2.setOnClickListener(v -> showPasswordDialog());
        root.addView(btn2);
        setContentView(root);
    }

    private void doLogin() {
        String key = inputAccessKey != null ? inputAccessKey.getText().toString().trim() : "";
        if (key.isEmpty()) {
            Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show();
            return;
        }
        // Use patched login (bypasses client-side encryption check, keeps server)
        int res = NativeProviderPatched.nLoginPatched(key);
        if (res == 0) {
            // Also update PasswordManager store
            PasswordManager.setPasswordFromUI(this, key);
            Toast.makeText(this, "SHADOW", Toast.LENGTH_LONG).show();
            // Start VPN service (same as original MainActivity behavior)
            try {
                android.content.Intent intent = new android.content.Intent(this, com.happproxy.service.XRayVpnService.class);
                startService(intent);
                // Move to dashboard/server activity
                startActivity(new android.content.Intent(this, com.happproxy.ui.ServerActivity.class));
            } catch (Exception e) {
                Log.e(TAG, "Start VPN failed: " + e.getMessage());
                Toast.makeText(this, "Saved but auto-connect failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Login check returned " + res + " — but password still saved (no-encryption mode)", Toast.LENGTH_LONG).show();
            PasswordManager.setPasswordFromUI(this, key);
        }
    }

    private void showPasswordDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("ShadowClient Password Manager");
        b.setMessage("Server preserved: " + PasswordManager.getCurrentServer(this) + "\nEnter new password below (no encryption).");
        final EditText input = new EditText(this);
        input.setHint("New password");
        input.setText(PasswordManager.getCurrentPassword(this));
        b.setView(input);
        b.setPositiveButton("Save", (d, w) -> {
            String np = input.getText().toString().trim();
            if (PasswordManager.setPasswordFromUI(this, np)) {
                if (inputAccessKey != null) inputAccessKey.setText(np);
                Toast.makeText(this, "New password saved: " + np, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Password changed via dialog to: " + np);
            }
        });
        b.setNegativeButton("Cancel", null);
        b.setNeutralButton("Show Server", (d, w) -> {
            String srv = PasswordManager.getCurrentServer(this);
            Toast.makeText(this, "Server: " + srv, Toast.LENGTH_LONG).show();
            showPasswordDialog(); // reopen
        });
        b.show();
    }
}
