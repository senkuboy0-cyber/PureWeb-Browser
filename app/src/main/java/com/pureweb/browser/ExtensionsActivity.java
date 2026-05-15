package com.pureweb.browser;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ExtensionsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ExtensionListAdapter adapter;
    private List<ExtensionItem> fullList = new ArrayList<>();
    private List<ExtensionItem> filteredList = new ArrayList<>();
    private EditText searchInput;
    private TextView extensionCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        recyclerView = findViewById(R.id.recyclerViewExtensions);
        searchInput = findViewById(R.id.searchExtensions);
        extensionCount = findViewById(R.id.extensionCount);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExtensionListAdapter();
        recyclerView.setAdapter(adapter);

        // Search
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterExtensions(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadRecommendedExtensions();
    }

    private void filterExtensions(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            String lower = query.toLowerCase();
            for (ExtensionItem item : fullList) {
                if (item.name.toLowerCase().contains(lower) ||
                    item.desc.toLowerCase().contains(lower)) {
                    filteredList.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateCount(filteredList.size());
    }

    private void updateCount(int count) {
        extensionCount.setText(String.valueOf(count));
        extensionCount.animate()
                .scaleX(1.2f).scaleY(1.2f)
                .setDuration(100)
                .withEndAction(() ->
                        extensionCount.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
    }

    private void loadRecommendedExtensions() {
        new Thread(() -> {
            try {
                URL url = new URL("https://addons.mozilla.org/api/v5/addons/search/?app=android&sort=users&type=extension&page_size=20");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                reader.close();

                JSONObject response = new JSONObject(result.toString());
                JSONArray results = response.getJSONArray("results");
                fullList.clear();

                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String id = obj.getString("guid");
                    String name = obj.getJSONObject("name").optString("en-US", "Unknown");
                    String summary = obj.has("summary") ?
                            obj.getJSONObject("summary").optString("en-US", "") : "";
                    String iconUrl = obj.optString("icon_url", "");
                    double rating = obj.has("ratings") ?
                            obj.getJSONObject("ratings").optDouble("average", 0.0) : 0.0;
                    String authors = "Unknown";
                    if (obj.has("authors")) {
                        JSONArray authorsArr = obj.getJSONArray("authors");
                        StringBuilder sb = new StringBuilder();
                        for (int j = 0; j < authorsArr.length(); j++) {
                            sb.append(authorsArr.getJSONObject(j).optString("name", ""));
                            if (j < authorsArr.length() - 1) sb.append(", ");
                        }
                        authors = sb.toString();
                    }
                    String version = obj.has("current_version") ?
                            obj.getJSONObject("current_version").optString("version", "N/A") : "N/A";
                    long users = obj.optLong("average_daily_users", 0);

                    fullList.add(new ExtensionItem(name, id, summary, rating, iconUrl, authors, version, users));
                }

                runOnUiThread(() -> {
                    filteredList.clear();
                    filteredList.addAll(fullList);
                    adapter.notifyDataSetChanged();
                    updateCount(filteredList.size());
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(ExtensionsActivity.this,
                            "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ========================================================================
    //  EXTENSION ITEM MODEL
    // ========================================================================

    public static class ExtensionItem implements java.io.Serializable {
        public String name, id, desc, iconUrl;
        public double rating;
        public String authors;
        public String version;
        public long users;

        public ExtensionItem(String name, String id, String desc, double rating,
                             String iconUrl, String authors, String version, long users) {
            this.name = name;
            this.id = id;
            this.desc = desc;
            this.rating = rating;
            this.iconUrl = iconUrl;
            this.authors = authors;
            this.version = version;
            this.users = users;
        }
    }

    // ========================================================================
    //  EXTENSION LIST ADAPTER
    // ========================================================================

    class ExtensionListAdapter extends RecyclerView.Adapter<ExtensionListAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_extension, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ExtensionItem item = filteredList.get(position);
            holder.name.setText(item.name);
            holder.desc.setText(item.desc);
            holder.rating.setText("★ " + String.format("%.1f", item.rating));
            holder.users.setText("• " + formatUsers(item.users));

            // Load icon
            if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
                try {
                    Glide.with(ExtensionsActivity.this)
                            .load(item.iconUrl)
                            .placeholder(android.R.drawable.sym_def_app_icon)
                            .error(android.R.drawable.sym_def_app_icon)
                            .into(holder.icon);
                } catch (Exception e) {
                    holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            }

            // Stagger entrance animation
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(30f);
            holder.itemView.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(300)
                    .setStartDelay(position * 40L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            // Click → Detail
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ExtensionsActivity.this, ExtensionDetailActivity.class);
                intent.putExtra("EXTENSION_DATA", item);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });

            // Install Button
            holder.btnAction.setOnClickListener(v -> startInstall(item, holder.btnAction));
        }

        private String formatUsers(long count) {
            if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
            if (count >= 1_000) return String.format("%.1fK", count / 1_000.0);
            return String.valueOf(count);
        }

        private void startInstall(ExtensionItem item, MaterialButton btn) {
            if (MainActivity.runtime == null) {
                Toast.makeText(ExtensionsActivity.this, "Runtime not ready",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            btn.setText("Installing...");
            btn.setEnabled(false);

            // Simple permission prompt
            MainActivity.runtime.getWebExtensionController().setPromptDelegate(
                    new WebExtensionController.PromptDelegate() {
                @NonNull
                @Override
                public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                        @NonNull WebExtension extension, @NonNull String[] permissions,
                        @NonNull String[] origins, @NonNull String[] dataCollectionPermissions) {

                    final GeckoResult<WebExtension.PermissionPromptResponse> result =
                            new GeckoResult<>();

                    String[] permStrings = permissions != null ? permissions : new String[0];
                    StringBuilder permMsg = new StringBuilder();
                    for (String p : permStrings) {
                        permMsg.append("• ").append(p).append("\n");
                    }

                    runOnUiThread(() -> {
                        new AlertDialog.Builder(ExtensionsActivity.this)
                                .setTitle("🧩 Add " + item.name + "?")
                                .setMessage("This extension needs:\n" + permMsg.toString())
                                .setPositiveButton("Allow", (dialog, which) ->
                                        result.complete(new WebExtension.PermissionPromptResponse(
                                                true, true, true)))
                                .setNegativeButton("Cancel", (dialog, which) -> {
                                    result.complete(new WebExtension.PermissionPromptResponse(
                                            false, false, false));
                                    runOnUiThread(() -> {
                                        btn.setText("Add");
                                        btn.setEnabled(true);
                                    });
                                })
                                .show();
                    });
                    return result;
                }
            });

            String url = "https://addons.mozilla.org/firefox/downloads/latest/" +
                    item.id + "/latest.xpi";
            MainActivity.runtime.getWebExtensionController().install(url).accept(
                    e -> runOnUiThread(() -> {
                        Toast.makeText(ExtensionsActivity.this,
                                "✅ " + item.name + " installed!", Toast.LENGTH_SHORT).show();
                        btn.setText("Installed ✓");
                        btn.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(
                                        getResources().getColor(android.R.color.holo_green_dark,
                                                getTheme())));
                    }),
                    e -> runOnUiThread(() -> {
                        Toast.makeText(ExtensionsActivity.this,
                                "❌ Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btn.setText("Add");
                        btn.setEnabled(true);
                    })
            );
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, desc, rating, users;
            MaterialButton btnAction;
            ImageView icon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.extension_name);
                desc = itemView.findViewById(R.id.extension_desc);
                rating = itemView.findViewById(R.id.extension_rating);
                users = itemView.findViewById(R.id.extension_users);
                btnAction = itemView.findViewById(R.id.extension_btn_action);
                icon = itemView.findViewById(R.id.extension_icon);
            }
        }
    }
}
