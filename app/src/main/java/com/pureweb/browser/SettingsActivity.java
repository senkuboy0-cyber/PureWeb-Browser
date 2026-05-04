package com.pureweb.browser;

import android.content.DialogInterface;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchJs, switchDesktop;
    private LinearLayout btnClearData;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        initViews();
        setupListeners();
        loadSettings();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        switchJs = findViewById(R.id.switch_js);
        switchDesktop = findViewById(R.id.switch_desktop);
        btnClearData = findViewById(R.id.btn_clear_data);
    }

    private void setupListeners() {
        switchJs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("js_enabled", isChecked).apply();
        });

        switchDesktop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("desktop_mode", isChecked).apply();
        });

        btnClearData.setOnClickListener(v -> showClearDataDialog());
    }

    private void loadSettings() {
        switchJs.setChecked(preferences.getBoolean("js_enabled", true));
        switchDesktop.setChecked(preferences.getBoolean("desktop_mode", false));
    }

    private void showClearDataDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Data")
                .setMessage("This will clear all cache, cookies, and history. Continue?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    clearBrowserData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearBrowserData() {
        try {
            WebView webView = new WebView(this);
            webView.clearCache(true);
            webView.clearFormData();
            webView.clearHistory();
            webView.destroy();

            if (WebViewDatabase.getInstance(this) != null) {
                WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword();
            }

            getSharedPreferences("webview", MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("prefs", MODE_PRIVATE).edit().clear().apply();

            android.webkit.CookieManager.getInstance().removeAllCookies(null);

            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .clear()
                    .apply();

            recreate();

            new AlertDialog.Builder(this)
                    .setTitle("Success")
                    .setMessage("All browser data has been cleared.")
                    .setPositiveButton("OK", null)
                    .show();

        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage("Failed to clear data: " + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }
}
