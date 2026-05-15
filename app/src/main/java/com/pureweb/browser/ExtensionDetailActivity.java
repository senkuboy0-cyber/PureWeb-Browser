package com.pureweb.browser;

import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.text.NumberFormat;
import java.util.Locale;

public class ExtensionDetailActivity extends AppCompatActivity {

    private ExtensionsActivity.ExtensionItem extensionItem;
    private MaterialButton addBtn;
    private MaterialCardView heroCard, statsCard, descCard, permissionsCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extension_detail);

        extensionItem = (ExtensionsActivity.ExtensionItem)
                getIntent().getSerializableExtra("EXTENSION_DATA");

        if (extensionItem == null) {
            finish();
            return;
        }

        // ─── Initialize Views ───────────────────────────────────────────
        TextView name = findViewById(R.id.detail_name);
        TextView authors = findViewById(R.id.detail_authors);
        TextView desc = findViewById(R.id.detail_desc);
        TextView rating = findViewById(R.id.detail_rating);
        TextView usersText = findViewById(R.id.detail_users);
        TextView versionText = findViewById(R.id.detail_version);
        ImageView icon = findViewById(R.id.detail_icon);
        addBtn = findViewById(R.id.detail_btn_add);
        LinearLayout permissionsContainer = findViewById(R.id.detail_permissions);

        heroCard = findViewById(R.id.heroCard);
        statsCard = findViewById(R.id.statsCard);
        descCard = findViewById(R.id.descCard);
        permissionsCard = findViewById(R.id.permissionsCard);

        // ─── Set Data ───────────────────────────────────────────────────
        name.setText(extensionItem.name);
        authors.setText("by " + (extensionItem.authors.isEmpty() ? "Unknown" : extensionItem.authors));
        desc.setText(extensionItem.desc.isEmpty() ? "No description available" : extensionItem.desc);
        rating.setText(String.format(Locale.US, "%.1f", extensionItem.rating));
        usersText.setText(NumberFormat.getNumberInstance(Locale.US).format(extensionItem.users));
        versionText.setText("v" + (extensionItem.version.isEmpty() ? "1.0" : extensionItem.version));

        // Load icon
        if (extensionItem.iconUrl != null && !extensionItem.iconUrl.isEmpty()) {
            try {
                Glide.with(this)
                        .load(extensionItem.iconUrl)
                        .placeholder(android.R.drawable.sym_def_app_icon)
                        .error(android.R.drawable.sym_def_app_icon)
                        .into(icon);
            } catch (Exception e) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }

        // ─── Add sample permissions ─────────────────────────────────────
        String[] samplePerms = {
                "Access your data for all websites",
                "Store unlimited data locally",
                "Access browser tabs"
        };
        for (String perm : samplePerms) {
            TextView permView = new TextView(this);
            permView.setText("•  " + perm);
            permView.setTextSize(13);
            permView.setTextColor(getResources().getColor(android.R.color.tab_indicator_text, getTheme()));
            permView.setAlpha(0.8f);
            permView.setPadding(8, 6, 8, 6);
            permissionsContainer.addView(permView);
        }

        // ─── Install Button ─────────────────────────────────────────────
        addBtn.setOnClickListener(v -> startInstall());

        // ─── Animate Entrance ───────────────────────────────────────────
        animateEntrance();
    }

    private void animateEntrance() {
        long delay = 100;

        // Hero Section
        if (heroCard != null) {
            heroCard.setAlpha(0f);
            heroCard.setTranslationY(-40f);
            heroCard.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(400)
                    .setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            delay += 150;
        }

        // Stats Card
        if (statsCard != null) {
            statsCard.setAlpha(0f);
            statsCard.setTranslationY(30f);
            statsCard.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(350)
                    .setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            delay += 150;
        }

        // Description
        if (descCard != null) {
            descCard.setAlpha(0f);
            descCard.setTranslationY(30f);
            descCard.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(350)
                    .setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            delay += 150;
        }

        // Permissions
        if (permissionsCard != null) {
            permissionsCard.setAlpha(0f);
            permissionsCard.setTranslationY(30f);
            permissionsCard.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(350)
                    .setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            delay += 150;
        }

        // Install Button
        if (addBtn != null) {
            addBtn.setAlpha(0f);
            addBtn.setTranslationY(30f);
            addBtn.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(300)
                    .setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void startInstall() {
        if (MainActivity.runtime == null) {
            Toast.makeText(this, "Runtime not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        addBtn.setText("Installing...");
        addBtn.setEnabled(false);

        // Button press animation
        addBtn.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() -> addBtn.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                .start();

        MainActivity.runtime.getWebExtensionController().setPromptDelegate(
                new WebExtensionController.PromptDelegate() {
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                    WebExtension extension, String[] permissions,
                    String[] origins, String[] dataCollectionPermissions) {

                final GeckoResult<WebExtension.PermissionPromptResponse> result =
                        new GeckoResult<>();

                StringBuilder permMsg = new StringBuilder();
                if (permissions != null) {
                    for (String p : permissions) {
                        permMsg.append("• ").append(p).append("\n");
                    }
                }

                runOnUiThread(() -> {
                    new AlertDialog.Builder(ExtensionDetailActivity.this)
                            .setTitle("🧩 Add " + extensionItem.name + "?")
                            .setMessage("This extension needs:\n" + permMsg.toString())
                            .setPositiveButton("Allow", (dialog, which) ->
                                    result.complete(new WebExtension.PermissionPromptResponse(
                                            true, true, true)))
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                result.complete(new WebExtension.PermissionPromptResponse(
                                        false, false, false));
                                runOnUiThread(() -> {
                                    addBtn.setText("Add Extension");
                                    addBtn.setEnabled(true);
                                });
                            })
                            .show();
                });
                return result;
            }
        });

        String url = "https://addons.mozilla.org/firefox/downloads/latest/" +
                extensionItem.id + "/latest.xpi";
        MainActivity.runtime.getWebExtensionController().install(url).accept(
                e -> runOnUiThread(() -> {
                    Toast.makeText(this, "✅ Installed!", Toast.LENGTH_SHORT).show();
                    addBtn.setText("Installed ✓");
                    addBtn.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(
                                    getResources().getColor(android.R.color.holo_green_dark,
                                            getTheme())));
                }),
                e -> runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    addBtn.setText("Add Extension");
                    addBtn.setEnabled(true);
                })
        );
    }
}
