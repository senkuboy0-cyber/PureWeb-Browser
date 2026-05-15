package com.pureweb.browser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DownloadsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DownloadAdapter adapter;
    private View emptyState;
    private MaterialButton btnClear;
    private List<DownloadItem> downloadList = new ArrayList<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        recyclerView = findViewById(R.id.downloadsRecyclerView);
        emptyState = findViewById(R.id.emptyState);
        btnClear = findViewById(R.id.btnClearDownloads);

        prefs = getSharedPreferences("PureWebDownloads", MODE_PRIVATE);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadDownloads();

        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("🗑️ Clear Downloads?")
                    .setMessage("This will remove all download records.")
                    .setPositiveButton("Clear", (d, w) -> {
                        downloadList.clear();
                        saveDownloads();
                        adapter.notifyDataSetChanged();
                        toggleEmptyState();
                        Toast.makeText(this, "Downloads cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void loadDownloads() {
        downloadList.clear();
        try {
            String json = prefs.getString("downloads", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                downloadList.add(new DownloadItem(
                        obj.optString("title", "File"),
                        obj.optString("url", ""),
                        obj.optString("size", "--"),
                        obj.optString("status", "Completed"),
                        obj.optLong("timestamp", System.currentTimeMillis()),
                        obj.optInt("progress", 100)
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        adapter = new DownloadAdapter(downloadList);
        recyclerView.setAdapter(adapter);
        toggleEmptyState();
    }

    private void saveDownloads() {
        try {
            JSONArray arr = new JSONArray();
            for (DownloadItem item : downloadList) {
                JSONObject obj = new JSONObject();
                obj.put("title", item.title);
                obj.put("url", item.url);
                obj.put("size", item.size);
                obj.put("status", item.status);
                obj.put("timestamp", item.timestamp);
                obj.put("progress", item.progress);
                arr.put(obj);
            }
            prefs.edit().putString("downloads", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void toggleEmptyState() {
        if (downloadList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // Static utility to add a download record
    public static void addDownload(android.content.Context context, String title,
                                    String url, String size, String status) {
        SharedPreferences prefs = context.getSharedPreferences("PureWebDownloads", MODE_PRIVATE);
        try {
            String json = prefs.getString("downloads", "[]");
            JSONArray arr = new JSONArray(json);

            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("url", url);
            obj.put("size", size);
            obj.put("status", status);
            obj.put("timestamp", System.currentTimeMillis());
            obj.put("progress", 100);
            arr.put(obj);

            // Keep max 200
            if (arr.length() > 200) {
                JSONArray trimmed = new JSONArray();
                for (int i = arr.length() - 200; i < arr.length(); i++) {
                    trimmed.put(arr.getJSONObject(i));
                }
                arr = trimmed;
            }

            prefs.edit().putString("downloads", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─── Model ────────────────────────────────────────────────────────────

    public static class DownloadItem {
        String title, url, size, status;
        long timestamp;
        int progress;

        DownloadItem(String title, String url, String size, String status,
                     long timestamp, int progress) {
            this.title = title;
            this.url = url;
            this.size = size;
            this.status = status;
            this.timestamp = timestamp;
            this.progress = progress;
        }

        String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            return sdf.format(new Date(timestamp));
        }

        String getFileIcon() {
            if (url != null) {
                String lower = url.toLowerCase();
                if (lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".webm"))
                    return "🎬";
                if (lower.contains(".mp3") || lower.contains(".wav") || lower.contains(".aac"))
                    return "🎵";
                if (lower.contains(".pdf")) return "📄";
                if (lower.contains(".zip") || lower.contains(".rar")) return "🗜️";
                if (lower.contains(".jpg") || lower.contains(".png") || lower.contains(".gif"))
                    return "🖼️";
            }
            return "📁";
        }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────

    class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {
        private List<DownloadItem> items;

        DownloadAdapter(List<DownloadItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DownloadItem item = items.get(position);
            holder.title.setText(item.title);
            holder.icon.setText(item.getFileIcon());

            String info = item.size + " • " + item.status;
            if (item.status.equals("Completed") || item.status.equals("complete")) {
                info += " • " + item.getFormattedDate();
            }
            holder.info.setText(info);

            // Progress bar
            if (item.progress < 100) {
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setProgress(item.progress);
                holder.actionBtn.setText("⏸");
            } else {
                holder.progress.setVisibility(View.GONE);
                holder.actionBtn.setText("▶");
            }

            // Animate entrance
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(20f);
            holder.itemView.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(250)
                    .setStartDelay(position * 30L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            // Click action
            holder.actionBtn.setOnClickListener(v -> {
                if (item.progress < 100 && item.url != null) {
                    Toast.makeText(DownloadsActivity.this,
                            "Resuming download...", Toast.LENGTH_SHORT).show();
                } else if (item.url != null) {
                    // Open file / URL
                    Toast.makeText(DownloadsActivity.this,
                            "Opening file...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, info, icon;
            ProgressBar progress;
            MaterialButton actionBtn;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.download_title);
                info = itemView.findViewById(R.id.download_info);
                icon = itemView.findViewById(R.id.download_icon);
                progress = itemView.findViewById(R.id.download_progress);
                actionBtn = itemView.findViewById(R.id.btnDownloadAction);
            }
        }
    }
}