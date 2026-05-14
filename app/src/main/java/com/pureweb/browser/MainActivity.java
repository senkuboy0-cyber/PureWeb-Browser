package com.pureweb.browser;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.pureweb.browser.data.VideoInfo;
import com.pureweb.browser.download.PureWebDownloader;
import com.pureweb.browser.manager.VideoDetectionManager;
import com.pureweb.browser.network.HttpClient;
import com.pureweb.browser.proxy.ProxyController;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements VideoDetectionManager.VideoDetectionListener {

    private GeckoView geckoView;
    public static GeckoSession session;
    public static GeckoRuntime runtime;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, menuBtn;
    private boolean canGoBack = false;

    // Video Detection System with Proxy Support
    private VideoDetectionManager videoDetectionManager;
    private PureWebDownloader downloader;
    private ProxyController proxyController;
    private HttpClient httpClient;
    private List<VideoInfo> detectedVideos = new ArrayList<>();
    private ExoPlayer exoPlayer;
    private Handler mainHandler;

    // Video badge for menu
    private TextView videoBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize video detection system
        videoDetectionManager = VideoDetectionManager.getInstance(this);
        videoDetectionManager.addListener(this);
        downloader = PureWebDownloader.getInstance(this);
        proxyController = ProxyController.getInstance(this);
        httpClient = HttpClient.getInstance(this);

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

        // Navigation Delegate
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
                            videoDetectionManager.processDetectedUrl(videoUrl, type, title);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }

                // M3U8 (HLS) manifest detected
                if (request.uri.startsWith("pureweb://m3u8")) {
                    try {
                        Uri uri = Uri.parse(request.uri);
                        String m3u8Url = uri.getQueryParameter("url");
                        String title = uri.getQueryParameter("title");

                        if (m3u8Url != null && !m3u8Url.isEmpty()) {
                            videoDetectionManager.processDetectedUrl(m3u8Url, "m3u8", title);
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
                    // Clear previous videos
                    detectedVideos.clear();
                    updateVideoBadge();
                });
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
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

    // ─── Video Detection Listener ──────────────────────────────────────────
    
    @Override
    public void onVideoDetected(VideoInfo videoInfo) {
        runOnUiThread(() -> {
            detectedVideos.add(videoInfo);
            updateVideoBadge();
            String typeLabel = videoInfo.isM3u8() ? "HLS" : (videoInfo.isMpd() ? "DASH" : "Video");
            Toast.makeText(this, "🎬 " + typeLabel + ": " + videoInfo.getTitle(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onVideoDetecting(String url) {
        // Optional: Show detecting state
    }

    @Override
    public void onVideosCleared() {
        runOnUiThread(() -> {
            detectedVideos.clear();
            updateVideoBadge();
        });
    }

    @Override
    public void onM3U8Detected(String url) {
        runOnUiThread(() -> {
            Toast.makeText(this, "📺 HLS Stream detected!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onMPDDetected(String url) {
        runOnUiThread(() -> {
            Toast.makeText(this, "🎞️ DASH Stream detected!", Toast.LENGTH_SHORT).show();
        });
    }

    // Update video badge count
    private void updateVideoBadge() {
        // Find video badge in menu
        View menuItem = findViewById(R.id.menu_videos);
        // Just show toast count
        if (detectedVideos.size() > 0) {
            // Update menu badge if exists
        }
    }

    // ─── Video Sniffer Injection ──────────────────────────────────────────────
    
    private void injectVideoSniffer() {
        String js = "javascript:(function(){" +
            "if(window.__pw)return;window.__pw=true;" +

            "var VP=/\\.(mp4|webm|m3u8|mpd|ts|mkv|mov|flv|mp3|aac|ogg|wav)/i;" +
            "var det=new Set();" +
            "var q=[];var snd=false;" +

            "function nxt(){" +
                "if(q.length===0){snd=false;return;}" +
                "snd=true;var i=q.shift();" +
                "window.location.href='pureweb://video?url='+encodeURIComponent(i.u)+'&type='+i.t+'&title='+encodeURIComponent(i.l);" +
                "setTimeout(nxt,200);" +
            "}" +

            "function notify(url,type){" +
                "if(!url||det.has(url))return;det.add(url);" +
                "q.push({u:url,t:type,l:document.title||'Video'});" +
                "if(!snd)nxt();" +
            "}" +

            // Intercept XHR
            "var ox=XMLHttpRequest.prototype.open;" +
            "XMLHttpRequest.prototype.open=function(m,u){" +
                "if(typeof u==='string'&&VP.test(u))notify(u,'xhr');" +
                "return ox.apply(this,arguments);" +
            "};" +

            // Intercept Fetch
            "if(window.fetch){var of=window.fetch;" +
            "window.fetch=function(i,o){" +
                "var u=typeof i==='string'?i:(i&&i.url?i.url:'');" +
                "if(u&&VP.test(u))notify(u,'fetch');" +
                "return of.apply(this,arguments);" +
            "};}" +

            // Detect video elements
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

            // Detect M3U8 specifically (HLS streaming)
            "function detectM3U8(){" +
                "document.querySelectorAll('source[src*=\".m3u8\"], video source[src*=\".m3u8\"], a[href*=\".m3u8\"]').forEach(function(el){" +
                    "var src=el.src||el.href;" +
                    "if(src&&VP.test(src))notify(src,'m3u8');" +
                "});" +
            "}" +

            // Periodic M3U8 detection for dynamic content
            "setInterval(detectM3U8,2000);" +

            "new MutationObserver(function(){" +
                "document.querySelectorAll('video').forEach(chkV);" +
                "detectM3U8();" +
            "}).observe(document.documentElement,{childList:true,subtree:true});" +
            "document.querySelectorAll('video').forEach(chkV);" +
            "detectM3U8();" +
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
            rv.setAdapter(new VideoListAdapter(
                    detectedVideos,
                    (videoInfo) -> {
                        downloader.download(videoInfo);
                        dialog.dismiss();
                    },
                    (videoInfo) -> {
                        openVideoPreview(videoInfo.getFirstUrl());
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
            VideoInfo videoInfo = new VideoInfo(url);
            downloader.download(videoInfo);
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
        
        // Start proxy for better video detection
        if (!proxyController.isProxyRunning()) {
            proxyController.startLocalProxy();
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
        if (videoDetectionManager != null) {
            videoDetectionManager.removeListener(this);
        }
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
                } else if (id == R.id.menu_proxy) {
                    // Toggle proxy
                    if (proxyController.isProxyRunning()) {
                        proxyController.stopProxy();
                        Toast.makeText(this, "Proxy stopped", Toast.LENGTH_SHORT).show();
                    } else {
                        proxyController.startLocalProxy();
                        Toast.makeText(this, "Proxy started on port 8888", Toast.LENGTH_SHORT).show();
                    }
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