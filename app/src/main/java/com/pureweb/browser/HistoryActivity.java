package com.pureweb.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private View emptyState;
    private MaterialButton btnClear;
    private List<HistoryItem> historyList = new ArrayList<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.historyRecyclerView);
        emptyState = findViewById(R.id.emptyState);
        btnClear = findViewById(R.id.btnClearHistory);

        prefs = getSharedPreferences("PureWebHistory", MODE_PRIVATE);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadHistory();

        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("🗑️ Clear History?")
                    .setMessage("This will remove all browsing history.")
                    .setPositiveButton("Clear", (d, w) -> {
                        historyList.clear();
                        saveHistory();
                        adapter.notifyDataSetChanged();
                        toggleEmptyState();
                        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void loadHistory() {
        historyList.clear();
        try {
            String json = prefs.getString("history", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                historyList.add(new HistoryItem(
                        obj.optString("title", "Unknown"),
                        obj.optString("url", ""),
                        obj.optLong("timestamp", System.currentTimeMillis())
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }

        Collections.sort(historyList, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        adapter = new HistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);
        toggleEmptyState();
    }

    private void saveHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (HistoryItem item : historyList) {
                JSONObject obj = new JSONObject();
                obj.put("title", item.title);
                obj.put("url", item.url);
                obj.put("timestamp", item.timestamp);
                arr.put(obj);
            }
            prefs.edit().putString("history", arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void toggleEmptyState() {
        if (historyList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    public static void addToHistory(Context context, String title, String url) {
        SharedPreferences prefs = context.getSharedPreferences("PureWebHistory", MODE_PRIVATE);
        try {
            String json = prefs.getString("history", "[]");
            JSONArray arr = new JSONArray(json);
            JSONArray filtered = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (!arr.getJSONObject(i).optString("url").equals(url))
                    filtered.put(arr.getJSONObject(i));
            }
            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("url", url);
            obj.put("timestamp", System.currentTimeMillis());
            filtered.put(obj);
            if (filtered.length() > 500) {
                JSONArray trimmed = new JSONArray();
                for (int i = filtered.length() - 500; i < filtered.length(); i++)
                    trimmed.put(filtered.getJSONObject(i));
                filtered = trimmed;
            }
            prefs.edit().putString("history", filtered.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static class HistoryItem {
        String title, url;
        long timestamp;
        HistoryItem(String title, String url, long timestamp) {
            this.title = title; this.url = url; this.timestamp = timestamp;
        }
        String getFormattedTime() {
            return new SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.US).format(new Date(timestamp));
        }
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<HistoryItem> items;
        HistoryAdapter(List<HistoryItem> items) { this.items = items; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryItem item = items.get(position);
            holder.title.setText(item.title);
            holder.url.setText(item.url);
            holder.time.setText(item.getFormattedTime());

            holder.itemView.setAlpha(0f); holder.itemView.setTranslationX(-30f);
            holder.itemView.animate().alpha(1f).translationX(0f).setDuration(250)
                    .setStartDelay(position * 30L)
                    .setInterpolator(new DecelerateInterpolator()).start();

            holder.itemView.setOnClickListener(v -> {
                GeckoSession session = MainActivity.getCurrentSession();
                if (session != null) { session.loadUri(item.url); finish(); }
            });

            holder.btnDelete.setOnClickListener(v -> {
                items.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, items.size());
                saveHistory();
                toggleEmptyState();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, url, time;
            MaterialButton btnDelete;
            ViewHolder(@NonNull View v) { super(v);
                title = v.findViewById(R.id.history_title);
                url = v.findViewById(R.id.history_url);
                time = v.findViewById(R.id.history_time);
                btnDelete = v.findViewById(R.id.btnDeleteHistory);
            }
        }
    }
}
