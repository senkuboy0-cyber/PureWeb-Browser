package com.pureweb.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup radioGroup;
    private RadioButton radioGoogle, radioDuckDuckGo, radioBing;
    private Button btnClearCache, btnOpenExtensions, btnInstallAdBlocker, btnCustomExtension;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // UI Initialize
        radioGroup = findViewById(R.id.radioGroupEngine);
        radioGoogle = findViewById(R.id.radioGoogle);
        radioDuckDuckGo = findViewById(R.id.radioDuckDuckGo);
        radioBing = findViewById(R.id.radioBing);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnOpenExtensions = findViewById(R.id.btnOpenExtensions); // নতুন ID
        btnInstallAdBlocker = findViewById(R.id.btnInstallAdBlocker);
        btnCustomExtension = findViewById(R.id.btnCustomExtension);

        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);

        // Search Engine Logic
        String savedEngine = prefs.getString("search_engine", "Google");
        if (savedEngine.equals("DuckDuckGo")) {
            radioDuckDuckGo.setChecked(true);
        } else if (savedEngine.equals("Bing")) {
            radioBing.setChecked(true);
        } else {
            radioGoogle.setChecked(true);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            SharedPreferences.Editor editor = prefs.edit();
            if (checkedId == R.id.radioGoogle) {
                editor.putString("search_engine", "Google");
            } else if (checkedId == R.id.radioDuckDuckGo) {
                editor.putString("search_engine", "DuckDuckGo");
            } else if (checkedId == R.id.radioBing) {
                editor.putString("search_engine", "Bing");
            }
            editor.apply();
            Toast.makeText(this, "Search Engine Saved!", Toast.LENGTH_SHORT).show();
        });

        // ১. Extension Store ওপেন করা
        btnOpenExtensions.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, ExtensionsActivity.class));
        });

        // ২. uBlock Origin সরাসরি ইনস্টল করা
        btnInstallAdBlocker.setOnClickListener(v -> {
            installuBlockOrigin();
        });

        // ৩. Developer Custom Extension ইনস্টল করা
        btnCustomExtension.setOnClickListener(v -> {
            showCustomUrlDialog();
        });

        btnClearCache.setOnClickListener(v -> {
            Toast.makeText(this, "Cache Cleared!", Toast.LENGTH_SHORT).show();
        });
    }

    private void installuBlockOrigin() {
        if (MainActivity.runtime == null) return;
        
        btnInstallAdBlocker.setText("Installing...");
        btnInstallAdBlocker.setEnabled(false);

        // প্রম্পট ডেলিগেট সেট করা
        MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @NonNull
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(@NonNull WebExtension extension, @NonNull String[] permissions, @NonNull String[] origins, @NonNull String[] dataCollectionPermissions) {
                final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("Add uBlock Origin?")
                            .setMessage("Allow this extension to block ads?")
                            .setPositiveButton("Allow", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(true, true, true)))
                            .setNegativeButton("Cancel", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(false, false, false)))
                            .show();
                });
                return result;
            }
        });

        String url = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";
        MainActivity.runtime.getWebExtensionController().install(url).accept(
                extension -> runOnUiThread(() -> {
                    Toast.makeText(this, "uBlock Origin Installed!", Toast.LENGTH_SHORT).show();
                    btnInstallAdBlocker.setText("Installed");
                }),
                exception -> runOnUiThread(() -> {
                    Toast.makeText(this, "Failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    btnInstallAdBlocker.setText("Install uBlock Origin");
                    btnInstallAdBlocker.setEnabled(true);
                })
        );
    }

    private void showCustomUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Install Custom Extension");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Enter Extension URL (.xpi)");
        builder.setView(input);

        builder.setPositiveButton("Install", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                installCustomExtension(url);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void installCustomExtension(String url) {
        if (MainActivity.runtime == null) return;
        
        MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @NonNull
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(@NonNull WebExtension extension, @NonNull String[] permissions, @NonNull String[] origins, @NonNull String[] dataCollectionPermissions) {
                final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("Install Extension?")
                            .setMessage("Do you want to install this extension?")
                            .setPositiveButton("Allow", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(true, true, true)))
                            .setNegativeButton("Cancel", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(false, false, false)))
                            .show();
                });
                return result;
            }
        });

        MainActivity.runtime.getWebExtensionController().install(url).accept(
                extension -> runOnUiThread(() -> Toast.makeText(this, "Extension Installed!", Toast.LENGTH_SHORT).show()),
                exception -> runOnUiThread(() -> Toast.makeText(this, "Failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show())
        );
    }
}