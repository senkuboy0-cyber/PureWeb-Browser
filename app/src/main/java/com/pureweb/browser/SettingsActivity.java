package com.pureweb.browser;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;

public class SettingsActivity extends AppCompatActivity {

    private Button btnExt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        btnExt = findViewById(R.id.btnOpenExtensions);
        btnExt.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, ExtensionsActivity.class));
        });
    }
}