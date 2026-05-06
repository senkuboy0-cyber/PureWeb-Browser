package com.pureweb.browser;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
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
    private ExtensionAdapter adapter;
    private List<ExtensionItem> recommendedList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        recyclerView = findViewById(R.id.recyclerViewExtensions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExtensionAdapter();
        recyclerView.setAdapter(adapter);

        loadRecommendedExtensions();
    }

    private void loadRecommendedExtensions() {
        new Thread(() -> {
            try {
                URL url = new URL("https://addons.mozilla.org/api/v5/addons/search/?app=android&sort=users&type=extension&page_size=15");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                JSONObject response = new JSONObject(result.toString());
                JSONArray results = response.getJSONArray("results");

                recommendedList.clear();

                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String id = obj.getString("guid");
                    String name = obj.getJSONObject("name").optString("en-US", "Unknown");
                    String summary = "";
                    if (obj.has("summary")) {
                        summary = obj.getJSONObject("summary").optString("en-US", "");
                    }
                    String iconUrl = obj.optString("icon_url");
                    double rating = 0.0;
                    if (obj.has("ratings")) {
                        rating = obj.getJSONObject("ratings").optDouble("average", 0.0);
                    }

                    recommendedList.add(new ExtensionItem(name, id, summary, rating, iconUrl));
                }

                runOnUiThread(() -> {
                    if(adapter != null) adapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(ExtensionsActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // --- Install Logic ---
    private void startInstall(ExtensionItem item, Button btn) {
        btn.setText("Installing...");
        btn.setEnabled(false);

        // ১. PromptDelegate সেট করা
        if (MainActivity.runtime != null) {
            MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
                @NonNull
                @Override
                public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(@NonNull WebExtension extension, @NonNull String[] permissions, @NonNull String[] origins, @NonNull String[] dataCollectionPermissions) {
                    final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();                    
                    // UI থ্রেডে ডায়ালগ দেখানো
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(ExtensionsActivity.this)
                                .setTitle("Add " + item.name + "?")
                                .setMessage("This extension requires permissions to function.")
                                .setPositiveButton("Allow", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(true, true, true)))
                                .setNegativeButton("Cancel", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(false, false, false)))
                                .setOnCancelListener(dialog -> result.complete(new WebExtension.PermissionPromptResponse(false, false, false)))
                                .show();
                    });
                    return result;
                }
            });
        }

        // ২. ইনস্টলেশন শুরু
        String url = "https://addons.mozilla.org/firefox/downloads/latest/" + item.id + "/latest.xpi";
        if (MainActivity.runtime != null) {
            MainActivity.runtime.getWebExtensionController().install(url).accept(
                    extension -> runOnUiThread(() -> {
                        Toast.makeText(this, item.name + " Installed!", Toast.LENGTH_SHORT).show();
                        btn.setText("Installed");
                        checkInstalledExtensions(); // লিস্ট রিফ্রেশ করা
                    }),
                    exception -> runOnUiThread(() -> {
                        Toast.makeText(this, "Failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        btn.setText("Add");
                        btn.setEnabled(true);
                    })
            );
        }
    }

    private void checkInstalledExtensions() {
        if (MainActivity.runtime == null) return;
        MainActivity.runtime.getWebExtensionController().list().accept(extensions -> {
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        });
    }

    // --- Model Class ---
    class ExtensionItem {
        String name, id, desc, iconUrl;
        double rating;
        boolean isInstalled = false;

        ExtensionItem(String name, String id, String desc, double rating, String iconUrl) {
            this.name = name;
            this.id = id;
            this.desc = desc;
            this.rating = rating;
            this.iconUrl = iconUrl;
        }
    }

    // --- Adapter Class ---
    class ExtensionAdapter extends RecyclerView.Adapter<ExtensionAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_extension, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ExtensionItem item = recommendedList.get(position);

            holder.name.setText(item.name);
            holder.desc.setText(item.desc);
            holder.rating.setText(String.valueOf(item.rating));

            Glide.with(ExtensionsActivity.this)
                    .load(item.iconUrl)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_placeholder)
                    .into(holder.icon);

            // চেক করা ইনস্টল কি না
            if (MainActivity.runtime != null) {
                MainActivity.runtime.getWebExtensionController().list().accept(extensions -> {
                    boolean found = false;
                    for (WebExtension ext : extensions) {
                        if (ext.id.equals(item.id)) {
                            found = true;
                            break;
                        }
                    }
                    boolean finalFound = found;
                    runOnUiThread(() -> {
                        if (finalFound) {
                            holder.btnAction.setText("Installed");
                            holder.btnAction.setEnabled(false);
                            holder.btnAction.setBackgroundColor(Color.parseColor("#555555"));
                        } else {
                            holder.btnAction.setText("Add");
                            holder.btnAction.setEnabled(true);
                            holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50"));
                        }
                    });
                });
            }

            holder.btnAction.setOnClickListener(v -> {
                if (!holder.btnAction.getText().toString().equals("Installed")) {
                    startInstall(item, holder.btnAction);
                }
            });
        }

        @Override
        public int getItemCount() {
            return recommendedList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, desc, rating;
            Button btnAction;
            ImageView icon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.extension_name);
                desc = itemView.findViewById(R.id.extension_desc);
                rating = itemView.findViewById(R.id.extension_rating);
                btnAction = itemView.findViewById(R.id.extension_btn_action);
                icon = itemView.findViewById(R.id.extension_icon);
            }
        }
    }
}