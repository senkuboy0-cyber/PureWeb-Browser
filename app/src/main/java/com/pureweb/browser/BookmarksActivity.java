package com.pureweb.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
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

import java.util.ArrayList;
import java.util.List;

public class BookmarksActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private BookmarkAdapter adapter;
    private View emptyState;
    private MaterialButton btnAdd;
    private List<BookmarkItem> bookmarkList = new ArrayList<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);

        recyclerView = findViewById(R.id.bookmarksRecyclerView);
        emptyState = findViewById(R.id.emptyState);
        btnAdd = findViewById(R.id.btnAddBookmark);

        prefs = getSharedPreferences("PureWebBookmarks", MODE_PRIVATE);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadBookmarks();

        btnAdd.setOnClickListener(v -> showAddDialog());
    }

    private void loadBookmarks() {
        bookmarkList.clear();
        try {
            String json = prefs.getString("bookmarks", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                bookmarkList.add(new BookmarkItem(
                        obj.optString("title", "Untitled"),
                        obj.optString("url", "")
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }

        adapter = new BookmarkAdapter(bookmarkList);
        recyclerView.setAdapter(adapter);
        toggleEmptyState();
    }

    private void saveBookmarks() {
        try {
            JSONArray arr = new JSONArray();
            for (BookmarkItem item : bookmarkList) {
                JSONObject obj = new JSONObject();
                obj.put("title", item.title);
                obj.put("url", item.url);
                arr.put(obj);
            }
            prefs.edit().putString("bookmarks", arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void toggleEmptyState() {
        if (bookmarkList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showAddDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⭐ Add Bookmark");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("Title");
        titleInput.setPadding(8, 8, 8, 8);
        layout.addView(titleInput);

        final EditText urlInput = new EditText(this);
        urlInput.setHint("URL");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setPadding(8, 8, 8, 8);
        layout.addView(urlInput);

        // Pre-fill from current URL if available
        if (MainActivity.session != null) {
            String currentUrl = urlBar != null ? urlBar.getText().toString() : "";
            if (!currentUrl.isEmpty()) urlInput.setText(currentUrl);
        }

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String title = titleInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                if (title.isEmpty()) title = url;
                bookmarkList.add(new BookmarkItem(title, url));
                saveBookmarks();
                adapter.notifyItemInserted(bookmarkList.size() - 1);
                toggleEmptyState();
                Toast.makeText(this, "⭐ Bookmark added!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Static utility to add from browser
    public static void addBookmark(Context context, String title, String url) {
        SharedPreferences prefs = context.getSharedPreferences("PureWebBookmarks", MODE_PRIVATE);
        try {
            String json = prefs.getString("bookmarks", "[]");
            JSONArray arr = new JSONArray(json);

            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optString("url").equals(url)) {
                    Toast.makeText(context, "Already bookmarked!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("url", url);
            arr.put(obj);

            if (arr.length() > 500) {
                JSONArray trimmed = new JSONArray();
                for (int i = arr.length() - 500; i < arr.length(); i++)
                    trimmed.put(arr.getJSONObject(i));
                arr = trimmed;
            }

            prefs.edit().putString("bookmarks", arr.toString()).apply();
            Toast.makeText(context, "⭐ Bookmarked!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─── Model ────────────────────────────────────────────────────────────

    public static class BookmarkItem {
        String title, url;
        BookmarkItem(String title, String url) { this.title = title; this.url = url; }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────

    class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {
        private List<BookmarkItem> items;
        BookmarkAdapter(List<BookmarkItem> items) { this.items = items; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bookmark, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BookmarkItem item = items.get(position);
            holder.title.setText(item.title);
            holder.url.setText(item.url);

            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(20f);
            holder.itemView.animate().alpha(1f).translationY(0f).setDuration(250)
                    .setStartDelay(position * 30L)
                    .setInterpolator(new DecelerateInterpolator()).start();

            holder.itemView.setOnClickListener(v -> {
                if (MainActivity.session != null) { MainActivity.session.loadUri(item.url); finish(); }
            });

            holder.itemView.setOnLongClickListener(v -> {
                String[] options = {"✏️ Edit", "🗑️ Delete"};
                new AlertDialog.Builder(BookmarksActivity.this)
                        .setTitle(item.title).setItems(options, (dialog, which) -> {
                            if (which == 0) showEditDialog(position);
                            else {
                                items.remove(position);
                                notifyItemRemoved(position);
                                notifyItemRangeChanged(position, items.size());
                                saveBookmarks();
                                toggleEmptyState();
                            }
                        }).show();
                return true;
            });

            holder.btnDelete.setOnClickListener(v -> {
                items.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, items.size());
                saveBookmarks();
                toggleEmptyState();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, url;
            MaterialButton btnDelete;
            ViewHolder(@NonNull View v) { super(v);
                title = v.findViewById(R.id.bookmark_title);
                url = v.findViewById(R.id.bookmark_url);
                btnDelete = v.findViewById(R.id.btnDeleteBookmark);
            }
        }
    }

    private void showEditDialog(int position) {
        BookmarkItem item = bookmarkList.get(position);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✏️ Edit Bookmark");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        final EditText titleInput = new EditText(this);
        titleInput.setText(item.title);
        titleInput.setPadding(8, 8, 8, 8);
        layout.addView(titleInput);

        final EditText urlInput = new EditText(this);
        urlInput.setText(item.url);
        urlInput.setPadding(8, 8, 8, 8);
        layout.addView(urlInput);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            item.title = titleInput.getText().toString().trim();
            item.url = urlInput.getText().toString().trim();
            saveBookmarks();
            adapter.notifyItemChanged(position);
            Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
