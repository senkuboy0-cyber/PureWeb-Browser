package com.pureweb.browser;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;

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
    private FrameLayout btnBack, btnForward, btnHome, btnRefresh, btnBookmarks;
    private ImageButton menuBtn;
    private TextView securityIcon, videoCount;
    private MaterialCardView topBar, bottomNav, videoBadge;
    private LinearLayout homePage, browserContainer, logoSection, homeSearchCard;
    private TextView homeSearchText;
    private RecyclerView speedDialGrid;

    private boolean canGoBack = false;
    private boolean isBrowserMode = false;
    private SharedPreferences prefs;

    private VideoDetectionManager videoDetectionManager;
    private PureWebDownloader downloader;
    private ProxyController proxyController;
    private HttpClient httpClient;
    private List<VideoInfo> detectedVideos = new ArrayList<>();
    private ExoPlayer exoPlayer;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());

        videoDetectionManager = VideoDetectionManager.getInstance(this);
        videoDetectionManager.addListener(this);
        downloader = PureWebDownloader.getInstance(this);
        proxyController = ProxyController.getInstance(this);
        httpClient = HttpClient.getInstance(this);

        initViews();
        initGeckoView();
        setupHomePage();
        setupNavigationButtons();
        setupUrlBar();
        setupMenuButton();
        showHomePage(true);
    }

    private void initViews() {
        homePage = findViewById(R.id.homePage);
        logoSection = findViewById(R.id.logoSection);
        homeSearchCard = findViewById(R.id.homeSearchCard);
        homeSearchText = findViewById(R.id.homeSearchText);
        speedDialGrid = findViewById(R.id.speedDialGrid);
        browserContainer = findViewById(R.id.browserContainer);
        geckoView = findViewById(R.id.geckoView);
        urlBar = findViewById(R.id.urlBar);
        progressBar = findViewById(R.id.progressBar);
        securityIcon = findViewById(R.id.securityIcon);
        topBar = findViewById(R.id.topBar);
        bottomNav = findViewById(R.id.bottomNav);
        videoBadge = findViewById(R.id.videoBadge);
        videoCount = findViewById(R.id.videoCount);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnHome = findViewById(R.id.btnHome);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnBookmarks = findViewById(R.id.btnBookmarks);
        menuBtn = findViewById(R.id.menuBtn);
    }

    private void initGeckoView() {
        if (runtime == null) runtime = GeckoRuntime.create(this);
        if (session == null) session = new GeckoSession();
        if (!session.isOpen()) session.open(runtime);
        geckoView.setSession(session);

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onCanGoBack(GeckoSession s, boolean cgb) {
                canGoBack = cgb;
                btnBack.setAlpha(canGoBack ? 1.0f : 0.4f);
            }
            @Override public void onLocationChange(GeckoSession s, String url) {
                runOnUiThread(() -> { urlBar.setText(url); updateSecurityIcon(url); });
            }
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(
                    GeckoSession s, GeckoSession.NavigationDelegate.LoadRequest r) {
                runOnUiThread(() -> {
                    if (!isBrowserMode) showBrowserMode(true);
                    HistoryActivity.addToHistory(MainActivity.this, r.uri, r.uri);
                });
                if (r.uri.startsWith("pureweb://video")) {
                    try {
                        Uri uri = Uri.parse(r.uri);
                        String vu = uri.getQueryParameter("url");
                        String ty = uri.getQueryParameter("type");
                        String ti = uri.getQueryParameter("title");
                        if (vu != null) videoDetectionManager.processDetectedUrl(vu, ty, ti);
                    } catch (Exception e) { e.printStackTrace(); }
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                if (r.uri.startsWith("pureweb://m3u8")) {
                    try {
                        Uri uri = Uri.parse(r.uri);
                        String mu = uri.getQueryParameter("url");
                        if (mu != null) videoDetectionManager.processDetectedUrl(mu, "m3u8", null);
                    } catch (Exception e) { e.printStackTrace(); }
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession s, String url) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);
                    detectedVideos.clear();
                    updateVideoBadge();
                });
            }
            @Override public void onPageStop(GeckoSession s, boolean success) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    progressBar.setProgress(100);
                    String url = urlBar.getText().toString();
                    if (!url.contains("youtube.com") && !url.contains("youtu.be")) injectVideoSniffer();
                });
            }
            @Override public void onProgressChange(GeckoSession s, int p) {
                runOnUiThread(() -> progressBar.setProgress(p));
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onFullScreen(GeckoSession s, boolean fs) {
                runOnUiThread(() -> {
                    if (fs) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        getWindow().getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                        topBar.setVisibility(View.GONE);
                        bottomNav.setVisibility(View.GONE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        topBar.setVisibility(View.VISIBLE);
                        bottomNav.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void setupHomePage() {
        homeSearchCard.setOnClickListener(v -> {
            showBrowserMode(true);
            urlBar.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT);
        });
        homeSearchCard.setOnLongClickListener(v -> {
            String clip = getClipboardText();
            if (clip != null && !clip.isEmpty()) {
                homeSearchText.setText(clip);
                homeSearchText.setTextColor(getResources().getColor(android.R.color.tab_indicator_text, getTheme()));
                Toast.makeText(this, "Pasted from clipboard", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        animateHomePage();
    }

    private void animateHomePage() {
        logoSection.setAlpha(0f); logoSection.setTranslationY(-30f);
        homeSearchCard.setAlpha(0f); homeSearchCard.setTranslationY(30f);
        logoSection.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100)
                .setInterpolator(new DecelerateInterpolator()).start();
        homeSearchCard.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(300)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void showHomePage(boolean animate) {
        isBrowserMode = false;
        homePage.setVisibility(View.VISIBLE);
        browserContainer.setVisibility(View.GONE);
        if (animate) {
            homePage.setAlpha(0f); homePage.setTranslationY(20f);
            homePage.animate().alpha(1f).translationY(0f).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator()).start();
            animateHomePage();
        }
        urlBar.setText(""); securityIcon.setText("🔒");
    }

    private void showBrowserMode(boolean animate) {
        isBrowserMode = true;
        browserContainer.setVisibility(View.VISIBLE);
        if (animate) {
            browserContainer.setAlpha(0f); browserContainer.setTranslationY(30f);
            browserContainer.animate().alpha(1f).translationY(0f).setDuration(350)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }
        topBar.setTranslationY(-100f); topBar.setAlpha(0f);
        topBar.animate().translationY(0f).alpha(1f).setDuration(350).setStartDelay(100)
                .setInterpolator(new DecelerateInterpolator()).start();
        bottomNav.setTranslationY(100f); bottomNav.setAlpha(0f);
        bottomNav.animate().translationY(0f).alpha(1f).setDuration(350).setStartDelay(150)
                .setInterpolator(new DecelerateInterpolator()).start();
        homePage.animate().alpha(0f).translationY(-30f).setDuration(200)
                .withEndAction(() -> homePage.setVisibility(View.GONE)).start();
    }

    private void updateSecurityIcon(String url) {
        if (url.startsWith("https://")) { securityIcon.setText("🔒"); securityIcon.setAlpha(1.0f); }
        else if (url.startsWith("http://")) { securityIcon.setText("⚠️"); securityIcon.setAlpha(0.8f); }
        else { securityIcon.setText("🔒"); securityIcon.setAlpha(0.6f); }
    }

    private String getClipboardText() {
        android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0)
            return cb.getPrimaryClip().getItemAt(0).getText().toString();
        return null;
    }

    @Override public void onVideoDetected(VideoInfo v) {
        runOnUiThread(() -> { detectedVideos.add(v); updateVideoBadge(); }); }
    @Override public void onVideoDetecting(String url) {}
    @Override public void onVideosCleared() { runOnUiThread(() -> { detectedVideos.clear(); updateVideoBadge(); }); }
    @Override public void onM3U8Detected(String url) { runOnUiThread(() -> Toast.makeText(this, "📺 HLS Stream detected!", Toast.LENGTH_SHORT).show()); }
    @Override public void onMPDDetected(String url) { runOnUiThread(() -> Toast.makeText(this, "🎞️ DASH Stream detected!", Toast.LENGTH_SHORT).show()); }

    private void updateVideoBadge() {
        if (detectedVideos.size() > 0) {
            videoBadge.setVisibility(View.VISIBLE);
            videoBadge.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();
            videoCount.setText(String.valueOf(detectedVideos.size()));
        } else {
            videoBadge.animate().scaleX(0.5f).scaleY(0.5f).alpha(0f).setDuration(200)
                    .withEndAction(() -> videoBadge.setVisibility(View.GONE)).start();
        }
    }

    private void injectVideoSniffer() {
        String js = "javascript:(function(){if(window.__pw)return;window.__pw=true;" +
            "var VP=/\\.(mp4|webm|m3u8|mpd|ts|mkv|mov|flv|mp3|aac|ogg|wav)/i;" +
            "var det=new Set();var q=[];var snd=false;" +
            "function nxt(){if(q.length===0){snd=false;return;}snd=true;var i=q.shift();window.location.href='pureweb://video?url='+encodeURIComponent(i.u)+'&type='+i.t+'&title='+encodeURIComponent(i.l);setTimeout(nxt,200);}" +
            "function notify(url,type){if(!url||det.has(url))return;det.add(url);q.push({u:url,t:type,l:document.title||'Video'});if(!snd)nxt();}" +
            "var ox=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){if(typeof u==='string'&&VP.test(u))notify(u,'xhr');return ox.apply(this,arguments);};" +
            "if(window.fetch){var of=window.fetch;window.fetch=function(i,o){var u=typeof i==='string'?i:(i&&i.url?i.url:'');if(u&&VP.test(u))notify(u,'fetch');return of.apply(this,arguments);};}" +
            "function chkV(v){var s=[v.src,v.currentSrc];v.querySelectorAll('source').forEach(function(x){s.push(x.src);});s.forEach(function(u){if(u&&u.length>5){if(VP.test(u))notify(u,'video');else if(u.startsWith('blob:'))notify(u,'blob');}});}" +
            "function detectM3U8(){document.querySelectorAll('source[src*=\".m3u8\"], video source[src*=\".m3u8\"], a[href*=\".m3u8\"]').forEach(function(el){var src=el.src||el.href;if(src&&VP.test(src))notify(src,'m3u8');});}" +
            "setInterval(detectM3U8,2000);" +
            "new MutationObserver(function(){document.querySelectorAll('video').forEach(chkV);detectM3U8();}).observe(document.documentElement,{childList:true,subtree:true});" +
            "document.querySelectorAll('video').forEach(chkV);detectM3U8();})();";
        session.loadUri(js);
    }

    private void showVideoBottomSheet() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.video_bottom_sheet, null);
        RecyclerView rv = v.findViewById(R.id.videoRecyclerView);
        View es = v.findViewById(R.id.emptyState);
        ((TextView)v.findViewById(R.id.videoCountBadge)).setText(String.valueOf(detectedVideos.size()));
        if (detectedVideos.isEmpty()) { es.setVisibility(View.VISIBLE); rv.setVisibility(View.GONE); }
        else {
            es.setVisibility(View.GONE); rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(new VideoListAdapter(detectedVideos,
                    vi -> { downloader.download(vi); d.dismiss(); },
                    vi -> { openVideoPreview(vi.getFirstUrl()); d.dismiss(); }));
        }
        d.setContentView(v); d.show();
    }

    private void openVideoPreview(String url) {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.video_player_sheet, null);
        PlayerView pv = v.findViewById(R.id.playerView);
        Button bd = v.findViewById(R.id.btnDownload);
        ((TextView)v.findViewById(R.id.playerTitle)).setText("▶️ Playing video");
        exoPlayer = new ExoPlayer.Builder(this).build();
        pv.setPlayer(exoPlayer);
        exoPlayer.setMediaItem(MediaItem.fromUri(url));
        exoPlayer.prepare(); exoPlayer.play();
        bd.setOnClickListener(ev -> { new PureWebDownloader(this).download(new VideoInfo(url)); d.dismiss(); });
        d.setOnDismissListener(ev -> { if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; } });
        d.setContentView(v); d.show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (session != null) { session.setActive(true); if (geckoView.getSession() != session) geckoView.setSession(session); }
        if (!proxyController.isProxyRunning()) proxyController.startLocalProxy();
    }
    @Override protected void onPause() { super.onPause(); if (session != null) session.setActive(false); if (exoPlayer != null) exoPlayer.pause(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
        if (videoDetectionManager != null) videoDetectionManager.removeListener(this);
        if (isFinishing()) { if (session != null) { session.close(); session = null; } }
    }
    @Override public void onBackPressed() {
        if (isBrowserMode && canGoBack) session.goBack();
        else if (isBrowserMode) showHomePage(true);
        else super.onBackPressed();
    }

    private void setupNavigationButtons() {
        btnBack.setOnClickListener(v -> {
            if (canGoBack) session.goBack();
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
        });
        btnForward.setOnClickListener(v -> {
            session.goForward();
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
        });
        btnHome.setOnClickListener(v -> {
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
            if (isBrowserMode) showHomePage(true);
        });
        btnRefresh.setOnClickListener(v -> { v.animate().rotationBy(360f).setDuration(400).start(); session.reload(); });
        btnBookmarks.setOnClickListener(v -> {
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
            startActivity(new Intent(this, BookmarksActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        videoBadge.setOnClickListener(v -> showVideoBottomSheet());
    }

    private void setupUrlBar() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                String input = urlBar.getText().toString().trim();
                if (!input.isEmpty()) {
                    loadUrlOrSearch(input);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
        urlBar.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) urlBar.selectAll(); });
    }

    // 🎯 UPDATED MENU WITH DOWNLOADS
    private void setupMenuButton() {
        menuBtn.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, v);
            popup.getMenuInflater().inflate(R.menu.browser_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                } else if (id == R.id.menu_history) {
                    startActivity(new Intent(this, HistoryActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                } else if (id == R.id.menu_bookmark) {
                    startActivity(new Intent(this, BookmarksActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                } else if (id == R.id.menu_downloads) {
                    startActivity(new Intent(this, DownloadsActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                } else if (id == R.id.menu_refresh) {
                    session.reload();
                } else if (id == R.id.menu_share) {
                    Intent si = new Intent(Intent.ACTION_SEND);
                    si.setType("text/plain");
                    si.putExtra(Intent.EXTRA_TEXT, urlBar.getText().toString());
                    startActivity(Intent.createChooser(si, "Share URL"));
                } else if (id == R.id.menu_find_in_page) {
                    Toast.makeText(this, "🔍 Find in Page coming soon", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.menu_active_extensions) {
                    showActiveExtensions();
                } else if (id == R.id.menu_videos) {
                    showVideoBottomSheet();
                } else if (id == R.id.menu_desktop_mode) {
                    item.setChecked(!item.isChecked());
                    String js = "javascript:(function(){var m=document.querySelector('meta[name=\"viewport\"]');if(m){if(m.content.indexOf('user-scalable=no')>=0)m.content='width=device-width, initial-scale=1.0, maximum-scale=5.0';else m.content='width=1920, user-scalable=no';}})();";
                    session.loadUri(js);
                    Toast.makeText(this, item.isChecked() ? "💻 Desktop Mode ON" : "📱 Desktop Mode OFF", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.menu_devtools) {
                    String js2 = "(function(){if(window.eruda){eruda.show();return;}var s=document.createElement('script');s.src='https://cdn.jsdelivr.net/npm/eruda';document.body.appendChild(s);s.onload=function(){eruda.init();eruda.show();};})();";
                    session.loadUri("javascript:" + js2);
                    Toast.makeText(this, "🛠️ DevTools Loading...", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.menu_proxy) {
                    if (proxyController.isProxyRunning()) { proxyController.stopProxy(); Toast.makeText(this, "🌐 Proxy stopped", Toast.LENGTH_SHORT).show(); }
                    else { proxyController.startLocalProxy(); Toast.makeText(this, "🌐 Proxy started on port 8888", Toast.LENGTH_SHORT).show(); }
                }
                return true;
            });
            popup.show();
        });
    }

    private void showActiveExtensions() {
        if (runtime == null) return;
        runtime.getWebExtensionController().list().accept(extensions -> {
            if (extensions.isEmpty()) { runOnUiThread(() -> Toast.makeText(this, "No extensions installed", Toast.LENGTH_SHORT).show()); return; }
            runOnUiThread(() -> {
                String[] names = new String[extensions.size()];
                for (int i = 0; i < extensions.size(); i++) names[i] = extensions.get(i).metaData.name;
                new AlertDialog.Builder(this).setTitle("🧩 Active Extensions").setItems(names, (d, w) -> openExtensionPopup(extensions.get(w))).setNegativeButton("Close", null).show();
            });
        });
    }

    private void openExtensionPopup(WebExtension ext) {
        if (ext.metaData != null && ext.metaData.optionsPageUrl != null) { session.loadUri(ext.metaData.optionsPageUrl); Toast.makeText(this, "Opening settings...", Toast.LENGTH_SHORT).show(); }
        else Toast.makeText(this, "No settings page", Toast.LENGTH_SHORT).show();
    }

    private void loadUrlOrSearch(String input) {
        if (input.isEmpty()) return;
        String url;
        if (input.contains(".") && !input.contains(" ")) {
            url = input.startsWith("http") ? input : "https://" + input;
        } else {
            String engine = prefs.getString("search_engine", "Google");
            String base;
            if (engine.equals("DuckDuckGo")) base = "https://duckduckgo.com/?q=";
            else if (engine.equals("Bing")) base = "https://www.bing.com/search?q=";
            else base = "https://www.google.com/search?q=";
            url = base + Uri.encode(input);
        }
        session.loadUri(url);
    }
}
