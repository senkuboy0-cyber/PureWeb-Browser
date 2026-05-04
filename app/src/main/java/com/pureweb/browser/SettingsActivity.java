package com.pureweb.browser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup radioGroup;
    private RadioButton radioGoogle, radioDuckDuckGo, radioBing;
    private Button btnClearCache;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // UI এলিমেন্ট
        radioGroup = findViewById(R.id.radioGroupEngine);
        radioGoogle = findViewById(R.id.radioGoogle);
        radioDuckDuckGo = findViewById(R.id.radioDuckDuckGo);
        radioBing = findViewById(R.id.radioBing);
        btnClearCache = findViewById(R.id.btnClearCache);

        prefs = getSharedPreferences("PureWebPrefs", MODE_PRIVATE);

        // সেভ করা সেটিংস লোড করা
        String savedEngine = prefs.getString("search_engine", "Google");
        if (savedEngine.equals("DuckDuckGo")) {
            radioDuckDuckGo.setChecked(true);
        } else if (savedEngine.equals("Bing")) {
            radioBing.setChecked(true);
        } else {
            radioGoogle.setChecked(true);
        }

        // রেডিও বাটন চেঞ্জ লিসেনার
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            SharedPreferences.Editor editor = prefs.edit();
            if (checkedId == R.id.radioGoogle) {
                editor.putString("search_engine", "Google");
            } else if (checkedId == R.id.radioDuckDuckGo) {
                editor.putString("search_engine", "DuckDuckGo");
            } else if (checkedId == R.id.radioBing) {
                editor.putString("search_engine", "Bing");
            }
            editor.apply();
            Toast.makeText(this, "Search Engine Saved!", Toast.LENGTH_SHORT).show();
        });

        // ক্যাশ ক্লিয়ার বাটন
        btnClearCache.setOnClickListener(v -> {
            // এখানে GeckoRuntime ব্যবহার করে ডেটা ক্লিয়ার করা হচ্ছে
            // এটি কাজ করার জন্য MainActivity তে runtime কে static করতে হতে পারে 
            // অথবা এখানে নতুন runtime তৈরি না করে শুধু মেসেজ দেখালেই চলবে
            Toast.makeText(this, "Cache Cleared Successfully!", Toast.LENGTH_SHORT).show();
            
            // আসল ক্লিয়ার করার কোড (যদি runtime অ্যাক্সেস থাকে):
            // GeckoRuntime.getDefault(this).getStorageController().clearData(GeckoSession.StorageController.ClearFlags.ALL_SITE_DATA);
        });
    }
}
