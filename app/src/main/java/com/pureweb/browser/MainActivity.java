package com.pureweb.browser;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private GeckoView geckoView;
    public static GeckoSession session;
    public static GeckoRuntime runtime;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, menuBtn;
    private boolean canGoBack = false;

    // Video detection
    private final List<Map<String, String>> detectedVideos = new ArrayList<>();
    private VideoDownloadManager downloadManager;
    private ExoPlayer exoPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);
        downloadManager = new VideoDownloadManager(this);

        geckoView  = findViewById(R.id.geckoView);
        urlBar     = findViewById(R.id.urlBar);
        progressBar = findViewById(R.id.progressBar);
        btnBack    = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnHome    = findViewById(R.id.btnHome);
        btnRefresh = findViewById(R.id.btnRefresh);
        menuBtn    = findViewById(R.id.menuBtn);

        if (runtime == null) {
            runtime = GeckoRuntime.create(this);
        }

        if (session == null) {
            session = new GeckoSession();
        }

        if (!session.isOpen()) {
            session.open(runtime);
        }

        geckoView.setSession(session);

        // Navigation Delegate — back button + video URL intercept
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                MainActivity.this.canGoBack = canGoBack;
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(
                    GeckoSession session,
                    GeckoSession.NavigationDelegate.LoadRequest request) {

                // Video sniffer থেকে আসা URL ধরো
                if (request.uri.startsWith("pureweb://video")) {
                    try {
                        Uri uri = Uri.parse(request.uri);
                        String videoUrl = uri.getQueryParameter("url");
                        String type     = uri.getQueryParameter("type");
                        String title    = uri.getQueryParameter("title");

                        if (videoUrl != null && !videoUrl.isEmpty()) {
                            // Duplicate check
                            boolean exists = false;
                            for (Map<String, String> v : detectedVideos) {
                                if (videoUrl.equals(v.get("url"))) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                Map<String, String> video = new HashMap<>();
                                video.put("url",   videoUrl);
                                video.put("type",  type  != null ? type  : "video");
                                video.put("title", title != null ? title : "Video");
                                detectedVideos.add(video);
                                runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this,
                                        "🎬 Video detected! (" + detectedVideos.size() + ")",
                                        Toast.LENGTH_SHORT).show()
                                );
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }

                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }
        });

        // Progress Delegate
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.VISIBLE);
                    urlBar.setText(url);
                    // নতুন page এ পুরনো video list clear
                    detectedVideos.clear();
                });
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    // YouTube বাদে সব page এ sniffer inject
                    String url = urlBar.getText().toString();
                    if (!url.contains("youtube.com") && !url.contains("youtu.be")) {
                        injectVideoSniffer();
                    }
                });
            }
        });

        // Fullscreen
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFullScreen(GeckoSession session, boolean fullScreen) {
                if (fullScreen) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                    findViewById(R.id.topBar).setVisibility(View.GONE);
                    findViewById(R.id.bottomNav).setVisibility(View.GONE);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                    findViewById(R.id.topBar).setVisibility(View.VISIBLE);
                    findViewById(R.id.bottomNav).setVisibility(View.VISIBLE);
                }
            }
        });

        loadHomePage();
        setupNavigationButtons();
        setupUrlBar();
        setupMenuButton();

        // Keyboard handling
        View bottomNav = findViewById(R.id.bottomNav);
        findViewById(R.id.root_layout).getViewTreeObserver()
                .addOnGlobalLayoutListener(() -> {
                    android.graphics.Rect r = new android.graphics.Rect();
                    getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
                    int screenH   = getWindow().getDecorView().getHeight();
                    int kbdHeight = screenH - r.bottom;
                    bottomNav.setVisibility(
                            kbdHeight > screenH * 0.15 ? View.GONE : View.VISIBLE);
                });
    }

    // ─── Video Sniffer Injection ──────────────────────────────────────────────

    private void injectVideoSniffer() {
        String js = "javascript:(function(){" +
            "if(window.__pw)return;window.__pw=true;" +
            "var VP=/\\.(mp4|webm|m3u8|mpd|ts|mkv|mov|flv)/i;" +
            "var det=new Set();" +
            "var q=[];var snd=false;" +
            "function nxt(){" +
                "if(q.length===0){snd=false;return;}" +
                "snd=true;var i=q.shift();" +
                "window.location.href='pureweb://video?url='+i.u+'&type='+i.t+'&title='+i.l;" +
                "setTimeout(nxt,200);" +
            "}" +
            "function notify(url,type){" +
                "if(!url||det.has(url))return;det.add(url);" +
                "q.push({u:encodeURIComponent(url),t:type,l:encodeURIComponent(document.title||'')});" +
                "if(!snd)nxt();" +
            "}" +
            "var ox=XMLHttpRequest.prototype.open;" +
            "XMLHttpRequest.prototype.open=function(m,u){" +
                "if(typeof u==='string'&&VP.test(u))notify(u,'xhr');" +
                "return ox.apply(this,arguments);" +
            "};" +
            "if(window.fetch){var of=window.fetch;" +
            "window.fetch=function(i,o){" +
                "var u=typeof i==='string'?i:(i&&i.url?i.url:'');" +
                "if(u&&VP.test(u))notify(u,'fetch');" +
                "return of.apply(this,arguments);" +
            "};}" +
            "function chkV(v){" +
                "var s=[v.src,v.currentSrc];" +
                "v.querySelectorAll('source').forEach(function(x){s.push(x.src);});" +
                "s.forEach(function(u){" +
                    "if(u&&u.length>5){" +
                        "if(VP.test(u))notify(u,'video');" +
                        "else if(u.startsWith('blob:'))notify(u,'blob');" +
                    "}" +
                "});" +
            "}" +
            "new MutationObserver(function(){" +
                "document.querySelectorAll('video').forEach(chkV);" +
            "}).observe(document.documentElement,{childList:true,subtree:true});" +
            "document.querySelectorAll('video').forEach(chkV);" +
        "})();";

        session.loadUri(js);
    }

    // ─── Video Bottom Sheet ──────────────────────────────────────────────────
    private void showVideoBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.video_bottom_sheet, null);

        RecyclerView rv  = view.findViewById(R.id.videoRecyclerView);
        TextView tvEmpty = view.findViewById(R.id.tvEmpty);

        if (detectedVideos.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(new VideoAdapter(
                    detectedVideos,
                    (url, title) -> {
                        downloadManager.download(url, title);
                        dialog.dismiss();
                    },
                    (url) -> {
                        openVideoPreview(url);
                        dialog.dismiss();
                    }
            ));
        }

        dialog.setContentView(view);
        dialog.show();
    }

    // ─── Video Preview Player ─────────────────────────────────────────────────
    private void openVideoPreview(String url) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.video_player_sheet, null);

        PlayerView playerView = view.findViewById(R.id.playerView);
        Button btnDownload    = view.findViewById(R.id.btnDownload);

        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);
        exoPlayer.setMediaItem(MediaItem.fromUri(url));
        exoPlayer.prepare();
        exoPlayer.play();

        btnDownload.setOnClickListener(v -> {
            downloadManager.download(url, "video_" + System.currentTimeMillis());
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> {
            if (exoPlayer != null) {
                exoPlayer.release();
                exoPlayer = null;
            }
        });

        dialog.setContentView(view);
        dialog.show();
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        if (session != null) {
            session.setActive(true);
            if (geckoView.getSession() != session) geckoView.setSession(session);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) session.setActive(false);
        if (exoPlayer != null) exoPlayer.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
        if (isFinishing()) {
            if (session != null) { session.close(); session = null; }
        }
    }

    // ─── Navigation ──────────────────────────────────────────────────────────
    private void setupNavigationButtons() {
        btnBack.setOnClickListener(v    -> session.goBack());
        btnForward.setOnClickListener(v -> session.goForward());
        btnHome.setOnClickListener(v    -> loadHomePage());
        btnRefresh.setOnClickListener(v -> session.reload());
    }

    private void setupUrlBar() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrlOrSearch(urlBar.getText().toString().trim());
                InputMethodManager imm = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
                return true;
            }
            return false;
        });
    }

    // ─── Menu ───────────────────────────────────────────────────────────────
    private void setupMenuButton() {
        menuBtn.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, v);
            popup.getMenuInflater().inflate(R.menu.browser_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.menu_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                } else if (id == R.id.menu_refresh) {
                    session.reload();
                    return true;
                } else if (id == R.id.menu_share) {
                    Intent si = new Intent(Intent.ACTION_SEND);
                    si.setType("text/plain");
                    si.putExtra(Intent.EXTRA_TEXT, urlBar.getText().toString());
                    startActivity(Intent.createChooser(si, "Share URL"));
                    return true;
                } else if (id == R.id.menu_history) {
                    Toast.makeText(this, "History coming soon",
                            Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_bookmark) {
                    Toast.makeText(this, "Bookmark coming soon",
                            Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_active_extensions) {
                    showActiveExtensions();
                    return true;
                } else if (id == R.id.menu_videos) {
                    showVideoBottomSheet();
                    return true;
                } else if (id == R.id.menu_devtools) {
                    String js = "(function(){if(window.eruda){eruda.show();return;}" +
                            "var s=document.createElement('script');" +
                            "s.src='https://cdn.jsdelivr.net/npm/eruda';" +
                            "document.body.appendChild(s);" +
                            "s.onload=function(){eruda.init();eruda.show();};})();";
                    session.loadUri("javascript:" + js);
                    Toast.makeText(this, "DevTools Loading...",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    // ─── Extensions ─────────────────────────────────────────────────────────
    private void showActiveExtensions() {
        if (runtime == null) return;
        runtime.getWebExtensionController().list().accept(extensions -> {
            if (extensions.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this,
                        "No extensions installed", Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> {
                String[] names = new String[extensions.size()];
                for (int i = 0; i < extensions.size(); i++)
                    names[i] = extensions.get(i).metaData.name;
                new AlertDialog.Builder(this)
                        .setTitle("Active Extensions")
                        .setItems(names, (d, which) ->
                                openExtensionPopup(extensions.get(which)))
                        .setNegativeButton("Close", null)
                        .show();
            });
        });
    }

    private void openExtensionPopup(WebExtension extension) {
        if (extension.metaData != null &&
                extension.metaData.optionsPageUrl != null) {
            session.loadUri(extension.metaData.optionsPageUrl);
            Toast.makeText(this, "Opening settings...",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No settings page",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ─── URL / Search ─────────────────────────────────────────────────────────
    private void loadUrlOrSearch(String input) {
        if (input.isEmpty()) return;
        String url;
        if (input.contains(".") && !input.contains(" ")) {
            url = input.startsWith("http") ? input : "https://" + input;
        } else {
            String engine = prefs.getString("search_engine", "Google");
            String base;
            if (engine.equals("DuckDuckGo")) base = "https://duckduckgo.com/?q=";
            else if (engine.equals("Bing"))  base = "https://www.bing.com/search?q=";
            else                             base = "https://www.google.com/search?q=";
            url = base + input;
        }
        session.loadUri(url);
    }

    private void loadHomePage() {
        session.loadUri("https://www.google.com");
    }

    @Override
    public void onBackPressed() {
        if (canGoBack) session.goBack();
        else super.onBackPressed();
    }
}
