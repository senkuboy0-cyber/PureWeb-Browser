package com.pureweb.browser;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlBar;
    private ImageButton btnBack, btnForward, btnRefresh, btnMenu;
    private ProgressBar progressBar;
    private TextView statusText, pageTitle;
    private View bottomBar;

    private SharedPreferences preferences;
    private String currentUrl;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        initViews();
        setupWebView();
        setupListeners();
        loadHome();
    }

    private void initViews() {
        webView = findViewById(R.id.web_view);
        urlBar = findViewById(R.id.url_bar);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnMenu = findViewById(R.id.btn_menu);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        pageTitle = findViewById(R.id.page_title);
        bottomBar = findViewById(R.id.bottom_bar);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(preferences.getBoolean("js_enabled", true));
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAllowFileAccess(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        if (preferences.getBoolean("desktop_mode", false)) {
            webSettings.setUserAgentString(WebSettings.getDefaultUserAgent(this));
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                urlBar.setText(url);
                currentUrl = url;
                statusText.setText(R.string.loading);
                btnRefresh.setImageResource(R.drawable.ic_close);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                statusText.setText(R.string.page_loaded);
                btnRefresh.setImageResource(R.drawable.ic_refresh);
                progressBar.setVisibility(View.GONE);
                updateNavigationButtons();
                invalidateOptionsMenu();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                statusText.setText(R.string.error_loading);
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setMax(100);
                progressBar.setProgress(newProgress);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                pageTitle.setText(title);
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });

        btnRefresh.setOnClickListener(v -> {
            if (webView.isLoading()) {
                webView.stopLoading();
            } else {
                webView.reload();
            }
        });

        btnMenu.setOnClickListener(this::showMenu);

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadUrl(urlBar.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.browser_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_home) {
                loadHome();
            } else if (id == R.id.action_share) {
                shareUrl();
            } else if (id == R.id.action_settings) {
                openSettings();
            } else if (id == R.id.action_find) {
                findInPage();
            }
            return true;
        });

        popup.show();
    }

    private void loadHome() {
        String homeUrl = preferences.getString("home_url", getString(R.string.default_home));
        webView.loadUrl(homeUrl);
    }

    private void loadUrl(String url) {
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".")) {
                url = "https://" + url;
            } else {
                url = "https://www.google.com/search?q=" + url.replace(" ", "+");
            }
        }

        webView.loadUrl(url);
    }

    private void shareUrl() {
        if (currentUrl != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, currentUrl);
            startActivity(Intent.createChooser(shareIntent, "Share URL"));
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void findInPage() {
        Toast.makeText(this, "Find in page feature", Toast.LENGTH_SHORT).show();
    }

    private void updateNavigationButtons() {
        btnBack.setEnabled(webView.canGoBack());
        btnBack.setAlpha(webView.canGoBack() ? 1.0f : 0.5f);
        btnForward.setEnabled(webView.canGoForward());
        btnForward.setAlpha(webView.canGoForward() ? 1.0f : 0.5f);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        webView.getSettings().setJavaScriptEnabled(prefs.getBoolean("js_enabled", true));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        return true;
    }
}
