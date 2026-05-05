package com.pureweb.browser;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public class MainActivity extends AppCompatActivity {

    private GeckoView geckoView;
    private GeckoSession session;
    public static GeckoRuntime runtime;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, menuBtn;
    private boolean canGoBack = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);

        geckoView = findViewById(R.id.geckoView);
        urlBar = findViewById(R.id.urlBar);
        progressBar = findViewById(R.id.progressBar);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnHome = findViewById(R.id.btnHome);
        btnRefresh = findViewById(R.id.btnRefresh);
        menuBtn = findViewById(R.id.menuBtn);

        if (runtime == null) {
            runtime = GeckoRuntime.create(this);
        }

        session = new GeckoSession();
        session.open(runtime);
        geckoView.setSession(session);

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                MainActivity.this.canGoBack = canGoBack;
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.VISIBLE);
                    urlBar.setText(url);
                });
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        });

        // ফুলস্ক্রিন লজিক (বাটনে চাপ দিলে ল্যান্ডস্কেপ হবে)
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

        // Step 1: window কে বলো insets নিজে handle করবে না
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Step 2: GeckoView এর উপরেই insets listener বসাও
        ViewCompat.setOnApplyWindowInsetsListener(
            geckoView, (v, insets) -> {

                // keyboard এর height বের করো
                int imeHeight = insets.getInsets(
                    WindowInsetsCompat.Type.ime()
                ).bottom;

                // system navigation bar height বাদ দাও
                int sysNavHeight = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                ).bottom;

                // GeckoView কে বলো কতটুকু নিচে ঢাকা আছে
                geckoView.setVerticalClipping(Math.max(0, imeHeight - sysNavHeight));

                return insets;
            }
        );
    }

    private void setupNavigationButtons() {
        btnBack.setOnClickListener(v -> session.goBack());
        btnForward.setOnClickListener(v -> session.goForward());
        btnHome.setOnClickListener(v -> loadHomePage());
        btnRefresh.setOnClickListener(v -> session.reload());
    }

    private void setupUrlBar() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                String input = urlBar.getText().toString().trim();
                loadUrlOrSearch(input);
                
                // সার্চ দেওয়ার পর কিবোর্ড অটোমেটিক বন্ধ করা
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
                
                return true;
            }
            return false;
        });
    }

    private void setupMenuButton() {
        menuBtn.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, v);
            popup.getMenuInflater().inflate(R.menu.browser_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_settings) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    return true;
                } else if (id == R.id.menu_refresh) {
                    session.reload();
                    return true;
                } else if (id == R.id.menu_share) {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.setExtra(Intent.EXTRA_TEXT, urlBar.getText().toString());
                    startActivity(Intent.createChooser(shareIntent, "Share URL"));
                    return true;
                } else if (id == R.id.menu_history) {
                    Toast.makeText(this, "History feature coming soon", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_bookmark) {
                    Toast.makeText(this, "Bookmark feature coming soon", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void loadUrlOrSearch(String input) {
        if (input.isEmpty()) return;
        String url;
        if (input.contains(".") && !input.contains(" ")) {
            url = input.startsWith("http") ? input : "https://" + input;
        } else {
            String engine = prefs.getString("search_engine", "Google");
            String baseUrl;
            if (engine.equals("DuckDuckGo")) {
                baseUrl = "https://duckduckgo.com/?q=";
            } else if (engine.equals("Bing")) {
                baseUrl = "https://www.bing.com/search?q=";
            } else {
                baseUrl = "https://www.google.com/search?q=";
            }
            url = baseUrl + input;
        }
        session.loadUri(url);
    }

    private void loadHomePage() {
        session.loadUri("https://www.google.com");
    }

    @Override
    public void onBackPressed() {
        if (canGoBack) {
            session.goBack();
        } else {
            super.onBackPressed();
        }
    }
}