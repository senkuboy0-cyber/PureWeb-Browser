package com.pureweb.browser;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
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
        adapter = new ExtensionAdapter(recommendedList);
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
}