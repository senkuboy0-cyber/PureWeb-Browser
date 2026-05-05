package com.pureweb.browser;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.util.ArrayList;
import java.util.List;

public class ExtensionsActivity extends AppCompatActivity {

    private LinearLayout container;
    
    // ১৫টি এক্সটেনশনের লিস্ট (নাম এবং Mozilla ID)
    private class ExtensionData {
        String name;
        String id;
        String description;

        ExtensionData(String name, String id, String desc) {
            this.name = name;
            this.id = id;
            this.description = desc;
        }
    }

    private List<ExtensionData> extensions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ScrollView এবং LinearLayout প্রোগ্রামেটিক্যালি তৈরি করা
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 32, 32, 32);
        container.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        scrollView.addView(container);
        setContentView(scrollView);
        
        // টাইটেল
        TextView title = new TextView(this);
        title.setText("Recommended Extensions");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, 40);
        container.addView(title);

        // ১৫টি এক্সটেনশন অ্যাড করা
        populateExtensions();
        
        // UI তৈরি করা
        for (ExtensionData ext : extensions) {
            addExtensionView(ext);
        }
    }

    private void populateExtensions() {
        extensions.add(new ExtensionData("uBlock Origin", "uBlock0@raymondhill.net", "Block ads and trackers"));
        extensions.add(new ExtensionData("Dark Reader", "addon@darkreader.org", "Dark mode for every website"));
        extensions.add(new ExtensionData("Bitwarden", "{446900e4-71c2-419f-a6a7-df9c091e268b}", "Free Password Manager"));
        extensions.add(new ExtensionData("Privacy Badger", "jid1-MnnxcxisBPnSXQ@jetpack", "Blocks invisible trackers"));
        extensions.add(new ExtensionData("SponsorBlock", "sponsorBlocker@ajay.app", "Skip YouTube Sponsors"));
        extensions.add(new ExtensionData("NoScript", "{73a6fe31-595d-460b-a920-fcc0f8843232}", "Allow/Block Scripts"));
        extensions.add(new ExtensionData("Tampermonkey", "firefox@tampermonkey.net", "User Script Manager"));
        extensions.add(new ExtensionData("LastPass", "support@lastpass.com", "Password Manager"));
        extensions.add(new ExtensionData("AdGuard Adblocker", "adguardadblocker@adguard.com", "Ads & Pop-ups Blocker"));
        extensions.add(new ExtensionData("Adblock Plus", "{d10d0bf8-f5b5-c8b4-a8b2-2b9879e08c5d}", "Popular Ad Blocker"));
        extensions.add(new ExtensionData("ClearURLs", "{74145f27-f039-47ce-a470-a662b129930a}", "Remove tracking from URLs"));
        extensions.add(new ExtensionData("Decentraleyes", "jid1-BoFifL9Vbdl2zQ@jetpack", "Local CDN Emulation"));
        extensions.add(new ExtensionData("User-Agent Switcher", "user-agent-switcher@ninetailed.ninja", "Change User Agent"));
        extensions.add(new ExtensionData("Stylus", "{7a7a4a92-a2a0-41d1-9fd7-1e92480d612d}", "Custom Website Themes"));
        extensions.add(new ExtensionData("I don't care about cookies", "jid1-KKzOGWgsW3Ao4Q@jetpack", "Hide Cookie Warnings"));
    }

    private void addExtensionView(ExtensionData ext) {
        // Card View এর মতো দেখানোর জন্য Layout
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(30, 30, 30, 30);
        card.setBackgroundColor(Color.parseColor("#F5F5F5"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 30);
        card.setLayoutParams(params);

        // নাম
        TextView name = new TextView(this);
        name.setText(ext.name);
        name.setTextSize(18);
        name.setTextColor(Color.parseColor("#1A73E8"));
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(name);

        // ডিসক্রিপশন
        TextView desc = new TextView(this);
        desc.setText(ext.description);
        desc.setTextSize(14);
        desc.setTextColor(Color.DKGRAY);
        card.addView(desc);

        // ইনস্টল বাটন
        Button installBtn = new Button(this);
        installBtn.setText("Install");
        installBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        installBtn.setTextColor(Color.WHITE);
        
        installBtn.setOnClickListener(v -> {
            installExtension(ext.name, ext.id, installBtn);
        });

        card.addView(installBtn);
        container.addView(card);
    }

    private void installExtension(String name, String id, Button btn) {
        if (MainActivity.runtime == null) return;

        btn.setText("Installing...");
        btn.setEnabled(false);

        // প্রম্পট ডেলিগেট সেট করা
        MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @NonNull
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(@NonNull WebExtension extension, @NonNull String[] permissions, @NonNull String[] origins, @NonNull String[] dataCollectionPermissions) {
                final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(ExtensionsActivity.this)
                        .setTitle("Install " + name + "?")
                        .setMessage("This extension requires permissions.")
                        .setPositiveButton("Allow", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(true, true, true)))
                        .setNegativeButton("Cancel", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(false, false, false)))
                        .show();
                });
                return result;
            }
        });

        // ইনস্টলেশন URL
        String url = "https://addons.mozilla.org/firefox/downloads/latest/" + id + "/latest.xpi";

        MainActivity.runtime.getWebExtensionController().install(url).accept(
            extension -> runOnUiThread(() -> {
                Toast.makeText(this, name + " Installed!", Toast.LENGTH_SHORT).show();
                btn.setText("Installed");
            }),
            exception -> runOnUiThread(() -> {
                Toast.makeText(this, "Failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                btn.setText("Install");
                btn.setEnabled(true);
            })
        );
    }
}