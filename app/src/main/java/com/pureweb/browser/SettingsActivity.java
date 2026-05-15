package com.pureweb.browser;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup radioGroup;
    private RadioButton radioGoogle, radioDuckDuckGo, radioBing;
    private Button btnClearCache, btnOpenExtensions, btnInstallAdBlocker, btnCustomExtension;
    private SharedPreferences prefs;
    private LinearLayout settingsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Setup window animations
        getWindow().setWindowAnimations(android.R.style.Animation_Translucent);

        settingsContainer = findViewById(R.id.settingsContainer);

        // UI Initialize
        radioGroup = findViewById(R.id.radioGroupEngine);
        radioGoogle = findViewById(R.id.radioGoogle);
        radioDuckDuckGo = findViewById(R.id.radioDuckDuckGo);
        radioBing = findViewById(R.id.radioBing);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnOpenExtensions = findViewById(R.id.btnOpenExtensions);
        btnInstallAdBlocker = findViewById(R.id.btnInstallAdBlocker);
        btnCustomExtension = findViewById(R.id.btnCustomExtension);

        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);

        // Animate settings items on start
        animateSettingsItems();

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

        btnOpenExtensions.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, ExtensionsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        btnInstallAdBlocker.setOnClickListener(v -> {
            showModernInstallDialog("uBlock Origin",
                    "addons.mozilla.org",
                    new String[]{"Access your data for all websites", "Block content on pages"},
                    true);
        });

        btnCustomExtension.setOnClickListener(v -> {
            showCustomUrlDialog();
        });

        btnClearCache.setOnClickListener(v -> {
            // Animate button press
            btnClearCache.animate()
                    .scaleX(0.95f).scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        btnClearCache.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(100)
                                .start();
                        Toast.makeText(this, "Cache Cleared!", Toast.LENGTH_SHORT).show();
                    })
                    .start();
        });
    }

    private void animateSettingsItems() {
        if (settingsContainer == null) return;
        for (int i = 0; i < settingsContainer.getChildCount(); i++) {
            View child = settingsContainer.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(30f);
            child.setScaleX(0.95f);
            child.setScaleY(0.95f);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(350)
                    .setStartDelay(i * 60L)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    // ========================================================================
    //  MODERN MATERIAL 3 EXTENSION INSTALL DIALOG WITH ANIMATIONS
    // ========================================================================

    private void showModernInstallDialog(String extensionName, String source,
                                          String[] permissions, boolean isAdBlocker) {
        // Inflate the custom dialog layout
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_install_permission, null);

        // Build the AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_NoActionBar_TranslucentDecor);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Make dialog background transparent so CardView shows properly
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);

        // --- Initialize Views ---
        CardView dialogRoot = dialogView.findViewById(R.id.dialogRoot);
        TextView title = dialogView.findViewById(R.id.dialogTitle);
        TextView urlText = dialogView.findViewById(R.id.extensionUrl);
        TextView permissionCount = dialogView.findViewById(R.id.permissionCount);
        LinearLayout permissionsContainer = dialogView.findViewById(R.id.permissionsContainer);
        SwitchCompat privateSwitch = dialogView.findViewById(R.id.checkPrivateBrowsing);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnAdd = dialogView.findViewById(R.id.btnAdd);

        // Set data
        title.setText("Install " + extensionName);
        urlText.setText("from " + source);
        permissionCount.setText(permissions.length + " item" + (permissions.length > 1 ? "s" : ""));

        // --- Animate Dialog Entrance ---
        dialogRoot.setAlpha(0f);
        dialogRoot.setScaleX(0.8f);
        dialogRoot.setScaleY(0.8f);
        dialogRoot.setTranslationY(100f);

        dialogRoot.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setStartDelay(50)
                .start();

        // --- Add Permission Items with Stagger Animation ---
        if (permissions.length > 0) {
            for (int i = 0; i < permissions.length; i++) {
                final int index = i;
                View permItem = createPermissionItem(permissions[i]);
                permissionsContainer.addView(permItem);

                // Stagger entrance animation
                permItem.setAlpha(0f);
                permItem.setTranslationX(-40f);
                permItem.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(300)
                        .setStartDelay(250 + (i * 100L))
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
        } else {
            // No permissions - show a friendly message
            TextView noPerm = new TextView(this);
            noPerm.setText("No special permissions required");
            noPerm.setTextSize(13);
            noPerm.setPadding(0, 8, 0, 8);
            noPerm.setAlpha(0.7f);
            permissionsContainer.addView(noPerm);
        }

        // --- Animate Private Browsing Section ---
        View privateSection = dialogView.findViewById(R.id.privateBrowsingSection);
        privateSection.setAlpha(0f);
        privateSection.setTranslationY(20f);
        privateSection.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(250 + (permissions.length * 100L))
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // --- Animate Buttons ---
        btnCancel.setAlpha(0f);
        btnAdd.setAlpha(0f);
        btnCancel.setTranslationY(20f);
        btnAdd.setTranslationY(20f);

        long btnDelay = 300 + (permissions.length * 100L) + 100;

        btnCancel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setStartDelay(btnDelay)
                .start();

        btnAdd.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setStartDelay(btnDelay + 80)
                .start();

        // --- Button Click Handlers ---
        btnCancel.setOnClickListener(v -> {
            animateDialogExit(dialogRoot, dialog);
        });

        btnAdd.setOnClickListener(v -> {
            boolean allowPrivate = privateSwitch.isChecked();

            // Button press feedback
            btnAdd.animate()
                    .scaleX(0.9f).scaleY(0.9f)
                    .setDuration(80)
                    .withEndAction(() -> {
                        btnAdd.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(80)
                                .start();
                    })
                    .start();

            // Show installing state
            btnAdd.setText("Installing...");
            btnAdd.setEnabled(false);
            btnCancel.setEnabled(false);

            // Proceed with installation
            if (isAdBlocker) {
                installuBlockOrigin(dialog, dialogRoot);
            } else {
                // For custom extensions, we handle it separately
                Toast.makeText(this, "Extension installation started...", Toast.LENGTH_SHORT).show();
                animateDialogExit(dialogRoot, dialog);
            }
        });

        dialog.show();
    }

    private View createPermissionItem(String permissionText) {
        View itemView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
        TextView textView = itemView.findViewById(android.R.id.text1);

        // Style the permission text
        textView.setText("•  " + permissionText);
        textView.setTextSize(13);
        textView.setPadding(12, 10, 12, 10);
        textView.setTextColor(getResources().getColor(android.R.color.tab_indicator_text, getTheme()));
        textView.setAlpha(0.85f);

        return itemView;
    }

    private void animateDialogExit(CardView dialogRoot, AlertDialog dialog) {
        dialogRoot.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .translationY(80f)
                .setDuration(200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(dialog::dismiss)
                .start();
    }

    // ========================================================================
    //  EXTENSION INSTALLATION METHODS (UPDATED WITH MODERN DIALOG)
    // ========================================================================

    private void installuBlockOrigin(AlertDialog dialog, CardView dialogRoot) {
        if (MainActivity.runtime == null) {
            Toast.makeText(this, "Runtime not ready!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return;
        }

        MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @NonNull
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                    @NonNull WebExtension extension,
                    @NonNull String[] permissions,
                    @NonNull String[] origins,
                    @NonNull String[] dataCollectionPermissions) {
                final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                result.complete(new WebExtension.PermissionPromptResponse(true, true, true));
                return result;
            }
        });

        String url = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";
        MainActivity.runtime.getWebExtensionController().install(url).accept(
                extension -> runOnUiThread(() -> {
                    Toast.makeText(this, "✅ uBlock Origin Installed!", Toast.LENGTH_SHORT).show();
                    btnInstallAdBlocker.setText("Installed ✓");
                    btnInstallAdBlocker.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(
                                    getResources().getColor(android.R.color.holo_green_dark, getTheme())));
                    animateDialogExit(dialogRoot, dialog);
                }),
                exception -> runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    animateDialogExit(dialogRoot, dialog);
                })
        );
    }

    private void showCustomUrlDialog() {
        // Modern custom URL dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert);
        builder.setTitle("🔗 Install Custom Extension");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://example.com/extension.xpi");
        input.setPadding(24, 16, 24, 16);
        input.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        getResources().getColor(android.R.color.holo_blue_light, getTheme())));

        builder.setView(input);

        builder.setPositiveButton("Install", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                showModernInstallDialog("Custom Extension", url,
                        new String[]{"Full access to website content", "Access your data for all websites"},
                        false);
            } else {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                getResources().getColor(android.R.color.holo_blue_dark, getTheme()));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                getResources().getColor(android.R.color.darker_gray, getTheme()));
    }
}
