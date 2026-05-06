package com.pureweb.browser;

import android.content.Intent;
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

                    String version = "N/A";
                    if (obj.has("current_version")) {
                        version = obj.getJSONObject("current_version").optString("version", "N/A");
                    }

                    long users = obj.optLong("average_daily_users", 0);

                    recommendedList.add(new ExtensionItem(name, id, summary, rating, iconUrl, authors, version, users));
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

    public static class ExtensionItem implements java.io.Serializable {
        public String name, id, desc, iconUrl;
        public double rating;
        public String authors;
        public String version;
        public long users;

        public ExtensionItem(String name, String id, String desc, double rating, String iconUrl, String authors, String version, long users) {
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

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ExtensionsActivity.this, ExtensionDetailActivity.class);
                intent.putExtra("EXTENSION_DATA", item);
                startActivity(intent);
            });

            holder.btnAction.setText("Add");
            holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50"));
            holder.btnAction.setEnabled(true);

            holder.btnAction.setOnClickListener(v -> startInstall(item, holder.btnAction));
        }
        
        private void startInstall(ExtensionItem item, Button btn) {
             btn.setText("Installing...");
             btn.setEnabled(false);
             
             MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
                 @NonNull
                 @Override
                 public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(@NonNull WebExtension extension, @NonNull String[] permissions, @NonNull String[] origins, @NonNull String[] dataCollectionPermissions) {
                     final GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                     runOnUiThread(() -> {
                         new androidx.appcompat.app.AlertDialog.Builder(ExtensionsActivity.this)
                                 .setTitle("Add " + item.name + "?")
                                 .setMessage("Allow permissions?")
                                 .setPositiveButton("Allow", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(true, true, true)))
                                 .setNegativeButton("Cancel", (dialog, which) -> result.complete(new WebExtension.PermissionPromptResponse(false, false, false)))
                                 .show();
                     });
                     return result;
                 }
             });

             String url = "https://addons.mozilla.org/firefox/downloads/latest/" + item.id + "/latest.xpi";
             MainActivity.runtime.getWebExtensionController().install(url).accept(
                     e -> runOnUiThread(() -> {
                         Toast.makeText(ExtensionsActivity.this, "Installed!", Toast.LENGTH_SHORT).show();
                         btn.setText("Installed");
                     }),
                     e -> runOnUiThread(() -> {
                         Toast.makeText(ExtensionsActivity.this, "Failed", Toast.LENGTH_SHORT).show();
                         btn.setText("Add");
                         btn.setEnabled(true);
                     })
             );
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