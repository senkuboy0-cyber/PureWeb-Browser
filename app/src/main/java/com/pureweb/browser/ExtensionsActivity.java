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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public class ExtensionsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ExtensionsAdapter adapter;
    private List<ExtensionItem> extensionList = new ArrayList<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // Model Class
    class ExtensionItem implements java.io.Serializable {
        String name, id, desc, iconUrl;
        double rating;
        String authors;
        String version;
        long users;

        ExtensionItem(String name, String id, String desc, double rating, String iconUrl, String authors, String version, long users) {
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

    // Adapter
    class ExtensionsAdapter extends RecyclerView.Adapter<ExtensionsAdapter.ViewHolder> {
        List<ExtensionItem> items;

        ExtensionsAdapter(List<ExtensionItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_extension, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ExtensionItem item = items.get(position);
            holder.name.setText(item.name);
            holder.desc.setText(item.desc);
            holder.rating.setText(String.format("%.1f", item.rating));

            Glide.with(ExtensionsActivity.this)
                    .load(item.iconUrl)
                    .placeholder(R.drawable.ic_placeholder)
                    .into(holder.icon);

            // Click listener for detail page
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ExtensionsActivity.this, ExtensionDetailActivity.class);
                intent.putExtra("EXTENSION_DATA", item);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, desc, rating;
            ImageView icon;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.ext_name);
                desc = itemView.findViewById(R.id.ext_desc);
                rating = itemView.findViewById(R.id.ext_rating);
                icon = itemView.findViewById(R.id.ext_icon);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        recyclerView = findViewById(R.id.extensions_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExtensionsAdapter(extensionList);
        recyclerView.setAdapter(adapter);

        loadRecommendedExtensions();
    }

    private void loadRecommendedExtensions() {
        executor.execute(() -> {
            try {
                URL url = new URL("https://addons-server.prod.mozilla.org/api/v4/addons/search/?type=extension&sort=rating&limit=20");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                List<ExtensionItem> recommendedList = new ArrayList<>();
                JSONObject json = new JSONObject(response.toString());
                JSONArray results = json.getJSONArray("results");

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
                    extensionList.clear();
                    extensionList.addAll(recommendedList);
                    adapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error loading extensions", Toast.LENGTH_SHORT).show());
            }
        });
    }
}