package com.pureweb.browser;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.text.NumberFormat;
import java.util.Locale;

public class ExtensionDetailActivity extends AppCompatActivity {

    private ExtensionsActivity.ExtensionItem extensionItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extension_detail);

        extensionItem = (ExtensionsActivity.ExtensionItem) getIntent().getSerializableExtra("EXTENSION_DATA");

        if (extensionItem == null) {
            finish();
            return;
        }

        TextView name = findViewById(R.id.detail_name);
        TextView authors = findViewById(R.id.detail_authors);
        TextView desc = findViewById(R.id.detail_desc);
        TextView rating = findViewById(R.id.detail_rating);
        TextView usersText = findViewById(R.id.detail_users);
        TextView versionText = findViewById(R.id.detail_version);
        ImageView icon = findViewById(R.id.detail_icon);
        Button addBtn = findViewById(R.id.detail_btn_add);

        name.setText(extensionItem.name);
        authors.setText(extensionItem.authors);
        desc.setText(extensionItem.desc);
        rating.setText(String.format(Locale.US, "%.1f", extensionItem.rating));
        usersText.setText(NumberFormat.getNumberInstance(Locale.US).format(extensionItem.users));
        versionText.setText(extensionItem.version);

        Glide.with(this)
                .load(extensionItem.iconUrl)
                .placeholder(R.drawable.ic_placeholder)
                .into(icon);

        addBtn.setOnClickListener(v -> startInstall(addBtn));
    }

    private void startInstall(Button btn) {
        if (MainActivity.runtime == null) return;

        btn.setText("Installing...");
        btn.setEnabled(false);

        MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(WebExtension extension, String[] permissions, String[] origins, String[] dataCollectionPermissions) {
                final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                runOnUiThread(() -> result.complete(new WebExtension.PermissionPromptResponse(true, true, true)));
                return result;
            }
        });

        String url = "https://addons.mozilla.org/firefox/downloads/latest/" + extensionItem.id + "/latest.xpi";
        MainActivity.runtime.getWebExtensionController().install(url).accept(
                e -> runOnUiThread(() -> {
                    Toast.makeText(this, "Installed!", Toast.LENGTH_SHORT).show();
                    btn.setText("Installed");
                }),
                e -> runOnUiThread(() -> {
                    Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
                    btn.setText("Add");
                    btn.setEnabled(true);
                })
        );
    }
}