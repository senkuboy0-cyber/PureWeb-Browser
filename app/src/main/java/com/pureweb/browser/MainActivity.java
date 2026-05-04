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

public class MainActivity extends AppCompatActivity {

    private GeckoView geckoView;
    private GeckoSession session;
    private GeckoRuntime runtime;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, menuBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // SharedPreferences সেটআপ (সেটিংস সেভ করার জন্য)
        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);

        // UI এলিমেন্ট খুঁজে বের করা
        geckoView = findViewById(R.id.geckoView);
        urlBar = findViewById(R.id.urlBar);
        progressBar = findViewById(R.id.progressBar);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnHome = findViewById(R.id.btnHome);
        btnRefresh = findViewById(R.id.btnRefresh);
        menuBtn = findViewById(R.id.menuBtn);

        // GeckoRuntime সেটআপ (শুধু প্রথমবার)
        if (runtime == null) {
            runtime = GeckoRuntime.create(this);
            // uBlock Origin ইনস্টল
            installAdBlocker();
        }

        // নতুন সেশন তৈরি
        session = new GeckoSession();
        session.open(runtime);
        geckoView.setSession(session);

        // প্রগ্রেস বার এবং URL আপডেট
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.VISIBLE);
                    urlBar.setText(url); // URL বারে লিংক দেখানো
                });
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        });

        // প্রাথমিকভাবে হোম পেজ লোড
        loadHomePage();

        // বাটন অ্যাকশন
        setupNavigationButtons();
        
        // URL বার এন্টার অ্যাকশন
        setupUrlBar();
        
        // মেনু বাটন অ্যাকশন
        setupMenuButton();
    }

    // uBlock Origin ইনস্টলেশন
    private void installAdBlocker() {
        String extensionUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi";
        runtime.getWebExtensionController().install(extensionUrl).accept(
            extension -> Log.d("PureWeb", "uBlock Origin Installed Successfully!"),
            exception -> Log.e("PureWeb", "Extension failed: " + exception.getMessage())
        );
    }

    // নেভিগেশন বাটন সেটআপ
    private void setupNavigationButtons() {
        btnBack.setOnClickListener(v -> {
            if (session != null) session.goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (session != null) session.goForward();
        });

        btnHome.setOnClickListener(v -> loadHomePage());

        btnRefresh.setOnClickListener(v -> {
            if (session != null) session.reload();
        });
    }

    // URL বার এবং সার্চ লজিক
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

    // থ্রি-ডট মেনু সেটআপ
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

    // স্মার্ট সার্চ লজিক
    private void loadUrlOrSearch(String input) {
        if (input.isEmpty()) return;

        String url;
        // যদি কোনো ওয়েবসাইট হয় (যেমন google.com)
        if (input.contains(".") && !input.contains(" ")) {
            if (!input.startsWith("http://") && !input.startsWith("https://")) {
                url = "https://" + input;
            } else {
                url = input;
            }
        } 
        // যদি সার্চ কোয়েরি হয়
        else {
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
        // ডিফল্ট হোম পেজ Google
        session.loadUri("https://www.google.com");
    }

    // ব্যাক বাটন ফাংশনালিটি
    @Override
    public void onBackPressed() {
        if (session != null) {
            if (session.canGoBack()) {
                session.goBack();
            } else {
                super.onBackPressed();
            }
        }
    }
}
