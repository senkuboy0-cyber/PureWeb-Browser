package com.pureweb.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.NavigationDelegate;

public class MainActivity extends AppCompatActivity {

    private GeckoView geckoView;
    private GeckoSession session;
    private GeckoRuntime runtime;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, menuBtn;
    
    // ব্যাক বাটন চেক করার জন্য ভেরিয়েবল
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
            installAdBlocker();
        }

        session = new GeckoSession();
        session.open(runtime);
        geckoView.setSession(session);

        // NavigationDelegate সেট করা হচ্ছে canGoBack চেক করার জন্য
        session.setNavigationDelegate(new NavigationDelegate() {
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

        loadHomePage();
        setupNavigationButtons();
        setupUrlBar();
        setupMenuButton();
    }

    private void installAdBlocker() {
        String extensionUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";
        runtime.getWebExtensionController().install(extensionUrl).accept(
            extension -> Log.d("PureWeb", "uBlock Origin Installed Successfully!"),
            exception -> Log.e("PureWeb", "Extension failed: " + exception.getMessage())
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
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                String input = urlBar.getText().toString().trim();
                loadUrlOrSearch(input);
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
            if (!input.startsWith("http://") && !input.startsWith("https://")) {
                url = "https://" + input;
            } else {
                url = input;
            }
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
        // এখানে আমরা ভেরিয়েবল ব্যবহার করছি
        if (canGoBack) {
            session.goBack();
        } else {
            super.onBackPressed();
        }
    }
}