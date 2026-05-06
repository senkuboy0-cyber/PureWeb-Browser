package com.pureweb.browser;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

public class MainActivity extends AppCompatActivity {

    private GeckoView geckoView;
    private GeckoSession session;
    public static GeckoRuntime runtime;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, menuBtn;
    private boolean canGoBack = false;
    private boolean isFullScreenMode = false;

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

        // ১. Runtime তৈরি (শুধু প্রথমবার)
        if (runtime == null) {
            runtime = GeckoRuntime.create(this);
        }

        // ২. Session তৈরি বা পুনরায় সেটআপ
        if (session == null) {
            session = new GeckoSession();
            setupDelegates(); // সব গুরুত্বপূর্ণ Delegates এখানে সেট হবে
        }

        // ৩. Session ওপেন করা
        if (!session.isOpen()) {
            session.open(runtime);
        }

        // ৪. View তে Session সেট করা
        geckoView.setSession(session);

        loadHomePage();
        setupNavigationButtons();
        setupUrlBar();
        setupMenuButton();
    }

    // Delegates সেটআপ (এটি মিস হয়ে গিয়েছিল)
    private void setupDelegates() {
        // Progress Delegate (Progress Bar এবং URL এর জন্য)
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

        // Navigation Delegate (Back button এর জন্য)
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                MainActivity.this.canGoBack = canGoBack;
            }
        });

        // Content Delegate (Full Screen এবং DevTools এর জন্য)
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFullScreen(GeckoSession session, boolean fullScreen) {
                isFullScreenMode = fullScreen;
                if (fullScreen) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // অ্যাপে ফিরে এলে Session Active করা এবং View এ সেট করা (সাদা পর্দা ঠিক করে)
        if (session != null) {
            session.setActive(true);
            if (geckoView.getSession() != session) {
                geckoView.setSession(session);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            session.setActive(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // শুধুমাত্র অ্যাপ বন্ধ হলে Session বন্ধ করব
        if (isFinishing()) {
            if (session != null) {
                session.close();
                session = null;
            }
        }
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
                    shareIntent.putExtra(Intent.EXTRA_TEXT, urlBar.getText().toString());
                    startActivity(Intent.createChooser(shareIntent, "Share URL"));
                    return true;
                } else if (id == R.id.menu_devtools) {
                    String jsCode = "(function(){if(window.eruda){eruda.show();return;}var s=document.createElement('script');s.src='https://cdn.jsdelivr.net/npm/eruda';document.body.appendChild(s);s.onload=function(){eruda.init();eruda.show();};})();";
                    session.loadUri("javascript:" + jsCode);
                    Toast.makeText(this, "DevTools Loading...", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_active_extensions) {
                    showActiveExtensions();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void showActiveExtensions() {
        if (runtime == null) return;
        runtime.getWebExtensionController().list().accept(extensions -> {
            if (extensions.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "No extensions installed", Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Active Extensions");
                String[] names = new String[extensions.size()];
                for (int i = 0; i < extensions.size(); i++) {
                    names[i] = extensions.get(i).metaData.name;
                }
                builder.setItems(names, (dialog, which) -> {
                    WebExtension selected = extensions.get(which);
                    openExtensionPopup(selected);
                });
                builder.setNegativeButton("Close", null);
                builder.show();
            });
        });
    }

    private void openExtensionPopup(WebExtension extension) {
        if (extension.metaData != null && extension.metaData.optionsPageUrl != null) {
            session.loadUri(extension.metaData.optionsPageUrl);
            Toast.makeText(this, "Opening " + extension.metaData.name + " settings", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "This extension has no settings page", Toast.LENGTH_SHORT).show();
        }
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