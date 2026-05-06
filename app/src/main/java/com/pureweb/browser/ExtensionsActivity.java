package com.pureweb.browser;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
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
    private ExtensionsAdapter adapter;
    private List<ExtensionItem> recommendedList = new ArrayList<>();
    private List<WebExtension> installedList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        recyclerView = findViewById(R.id.recyclerViewExtensions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadRecommendedExtensions();

        adapter = new ExtensionsAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkInstalledExtensions();
    }

    private void loadRecommendedExtensions() {
        // API থেকে ডাটা লোড করার জন্য নতুন Thread ব্যবহার করতে হবে
        new Thread(() -> {
            try {
                // Mozilla AMO API (Android এর জন্য জনপ্রিয় ১৫টি এক্সটেনশন)
                URL url = new URL("https://addons.mozilla.org/api/v5/addons/search/?app=android&sort=users&type=extension&page_size=15");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                // JSON Response রিড করা
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                // JSON Parse করা
                org.json.JSONObject response = new org.json.JSONObject(result.toString());
                org.json.JSONArray results = response.getJSONArray("results");

                // আগের লিস্ট ক্লিয়ার করা
                recommendedList.clear();

                // লুপ করে প্রতিটি এক্সটেনশনের ডাটা বের করা
                for (int i = 0; i < results.length(); i++) {
                    org.json.JSONObject obj = results.getJSONObject(i);
                    
                    // এক্সটেনশনের GUID (ID)
                    String id = obj.getString("guid"); 
                    
                    // নাম (Localized)
                    String name = obj.getJSONObject("name").optString("en-US", "Unknown");
                    
                    // ডিসক্রিপশন
                    String summary = "";
                    if (obj.has("summary")) {
                        summary = obj.getJSONObject("summary").optString("en-US", "");
                    }
                    
                    // ছবির URL
                    String iconUrl = obj.optString("icon_url");
                    
                    // রেটিং
                    double rating = 0.0;
                    if (obj.has("ratings")) {
                        rating = obj.getJSONObject("ratings").optDouble("average", 0.0);
                    }

                    // লিস্টে যোগ করা
                    recommendedList.add(new ExtensionItem(name, id, summary, rating, iconUrl));
                }

                // UI আপডেট করা (Main Thread এ)
                runOnUiThread(() -> adapter.notifyDataSetChanged());

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to load extensions", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void checkInstalledExtensions() {
        if (MainActivity.runtime == null) return;
        MainActivity.runtime.getWebExtensionController().list().accept(extensions -> {
            installedList.clear();
            installedList.addAll(extensions);
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        });
    }

    // Model Class
    class ExtensionItem {
        String name, id, desc, iconUrl;
        double rating;

        ExtensionItem(String name, String id, String desc, double rating, String iconUrl) {
            this.name = name;
            this.id = id;
            this.desc = desc;
            this.rating = rating;
            this.iconUrl = iconUrl;
        }
    }

    // Adapter Class
    class ExtensionsAdapter extends RecyclerView.Adapter<ExtensionsAdapter.ViewHolder> {

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

            // Glide ব্যবহার করে ছবি লোড করা হচ্ছে
            Glide.with(ExtensionsActivity.this)
                    .load(item.iconUrl)
                    .placeholder(R.mipmap.ic_launcher)
                    .into(holder.icon);

            boolean isInstalled = false;
            for (WebExtension ext : installedList) {
                if (ext.id.equals(item.id)) {
                    isInstalled = true;
                    break;
                }
            }

            if (isInstalled) {
                holder.btnAction.setText("Installed");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setBackgroundColor(Color.parseColor("#555555"));
            } else {
                holder.btnAction.setText("Add");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50"));
            }

            holder.btnAction.setOnClickListener(v -> {
                showInstallDialog(item);
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

    private void showInstallDialog(ExtensionItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_install_permission, null);
        builder.setView(view);

        TextView title = view.findViewById(R.id.dialogTitle);
        TextView perms = view.findViewById(R.id.dialogPermissions);
        CheckBox privateBrowsing = view.findViewById(R.id.checkPrivateBrowsing);
        Button btnAdd = view.findViewById(R.id.btnAdd);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        title.setText("Add " + item.name + "?");
        perms.setText("- Access your data for all websites\n- Access browser tabs\n- Display notifications");

        AlertDialog dialog = builder.create();
        dialog.show();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            boolean allowPrivate = privateBrowsing.isChecked();
            startInstall(item, allowPrivate);
            dialog.dismiss();
        });
    }

    private void startInstall(ExtensionItem item, boolean privateBrowsing) {
        String url = "https://addons.mozilla.org/firefox/downloads/latest/" + item.id + "/latest.xpi";

        MainActivity.runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @NonNull
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(@NonNull WebExtension extension, @NonNull String[] permissions, @NonNull String[] origins, @NonNull String[] dataCollectionPermissions) {
                return GeckoResult.fromValue(new WebExtension.PermissionPromptResponse(true, true, true));
            }
        });

        MainActivity.runtime.getWebExtensionController().install(url).accept(
                extension -> runOnUiThread(() -> {
                    Toast.makeText(this, item.name + " Installed!", Toast.LENGTH_SHORT).show();
                    checkInstalledExtensions();
                }),
                exception -> runOnUiThread(() -> Toast.makeText(this, "Failed to install", Toast.LENGTH_SHORT).show())
        );
    }
}