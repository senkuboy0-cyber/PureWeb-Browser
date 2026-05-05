package com.pureweb.browser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup radioGroup;
    private RadioButton radioGoogle, radioDuckDuckGo, radioBing;
    private Button btnClearCache, btnInstallAdBlocker, btnCustomExtension;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        radioGroup = findViewById(R.id.radioGroupEngine);
        radioGoogle = findViewById(R.id.radioGoogle);
        radioDuckDuckGo = findViewById(R.id.radioDuckDuckGo);
        radioBing = findViewById(R.id.radioBing);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnInstallAdBlocker = findViewById(R.id.btnInstallAdBlocker);
        btnCustomExtension = findViewById(R.id.btnCustomExtension); // নতুন বাটন

        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);

        setupSearchEngine();
        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // প্রতিবার সেটিংসে ঢুকলে চেক করবে এক্সটেনশন ইনস্টল আছে কি না
        checkIfExtensionInstalled();
    }

    private void setupSearchEngine() {
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
    }

    private void setupButtons() {
        btnInstallAdBlocker.setOnClickListener(v -> {
            if (MainActivity.runtime != null) {
                installExtension("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi");
            }
        });

        btnCustomExtension.setOnClickListener(v -> {
            showCustomExtensionDialog();
        });

        btnClearCache.setOnClickListener(v -> {
            Toast.makeText(this, "Cache Cleared!", Toast.LENGTH_SHORT).show();
        });
    }

    // চেক করার লজিক
    private void checkIfExtensionInstalled() {
        if (MainActivity.runtime == null) return;

        MainActivity.runtime.getWebExtensionController().list().accept(extensions -> {
            boolean isInstalled = false;
            for (WebExtension ext : extensions) {
                // uBlock এর আইডি বা নাম চেক করা
                if (ext.metaData != null && ext.metaData.name != null && ext.metaData.name.contains("uBlock")) {
                    isInstalled = true;
                    break;
                }
            }

            final boolean finalInstalled = isInstalled;
            runOnUiThread(() -> {
                if (finalInstalled) {
                    btnInstallAdBlocker.setText("Installed");
                    btnInstallAdBlocker.setEnabled(false);
                } else {
                    btnInstallAdBlocker.setText("Install uBlock Origin");
                    btnInstallAdBlocker.setEnabled(true);
                }
            });
        });
    }

    private void installExtension(String url) {
        btnInstallAdBlocker.setText("Installing...");
        btnInstallAdBlocker.setEnabled(false);

        MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @NonNull
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                    @NonNull WebExtension extension,
                    @NonNull String[] permissions,
                    @NonNull String[] origins,
                    @NonNull String[] dataCollectionPermissions) {
                
                final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Install Extension?")
                        .setMessage("Do you want to install this extension?")
                        .setPositiveButton("Allow", (dialog, which) -> 
                            result.complete(new WebExtension.PermissionPromptResponse(true, true, true)))
                        .setNegativeButton("Cancel", (dialog, which) -> 
                            result.complete(new WebExtension.PermissionPromptResponse(false, false, false)))
                        .setOnCancelListener(dialog -> 
                            result.complete(new WebExtension.PermissionPromptResponse(false, false, false)))
                        .show();
                });
                return result;
            }
        });

        MainActivity.runtime.getWebExtensionController().install(url).accept(
            extension -> runOnUiThread(() -> {
                Toast.makeText(this, "Extension Installed!", Toast.LENGTH_LONG).show();
                checkIfExtensionInstalled(); // স্ট্যাটাস আপডেট করা
            }),
            exception -> runOnUiThread(() -> {
                Toast.makeText(this, "Failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                btnInstallAdBlocker.setText("Install uBlock Origin");
                btnInstallAdBlocker.setEnabled(true);
            })
        );
    }

    // কাস্টম এক্সটেনশন ইনস্টলের ডায়ালগ
    private void showCustomExtensionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Install Custom Extension");

        final EditText input = new EditText(this);
        input.setHint("Enter Extension URL (.xpi)");
        builder.setView(input);

        builder.setPositiveButton("Install", (dialog, which) -> {
            String url = input.getText().toString();
            if (!url.isEmpty() && MainActivity.runtime != null) {
                installExtension(url);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}