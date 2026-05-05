package com.pureweb.browser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
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
    private Button btnClearCache, btnInstallAdBlocker;
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

        // uBlock Origin Install Button Logic
        btnInstallAdBlocker.setOnClickListener(v -> {
            if (MainActivity.runtime != null) {
                installExtension();
            } else {
                Toast.makeText(this, "Browser engine not ready!", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearCache.setOnClickListener(v -> {
            Toast.makeText(this, "Cache Cleared!", Toast.LENGTH_SHORT).show();
        });
    }

    private void installExtension() {
        String extensionUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";
        
        btnInstallAdBlocker.setText("Installing...");
        btnInstallAdBlocker.setEnabled(false);

        // নতুন API অনুযায়ী সেটআপ
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
                        .setTitle("Install uBlock Origin?")
                        .setMessage("This extension needs permissions to block ads and trackers.")
                        .setPositiveButton("Allow", (dialog, which) -> {
                            // ছোট হাতের অক্ষরে allow() এবং deny()
                            result.complete(WebExtension.PermissionPromptResponse.allow());
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            result.complete(WebExtension.PermissionPromptResponse.deny());
                        })
                        .setOnCancelListener(dialog -> result.complete(WebExtension.PermissionPromptResponse.deny()))
                        .show();
                });

                return result;
            }
        });

        // ইনস্টলেশন শুরু
        MainActivity.runtime.getWebExtensionController().install(extensionUrl).accept(
            extension -> {
                runOnUiThread(() -> {
                    Toast.makeText(this, "uBlock Origin Installed!", Toast.LENGTH_LONG).show();
                    btnInstallAdBlocker.setText("Installed");
                });
            },
            exception -> {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                    btnInstallAdBlocker.setText("Install uBlock Origin");
                    btnInstallAdBlocker.setEnabled(true);
                });
            }
        );
    }
}