package com.pureweb.browser;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.splashLogo);
        TextView text = findViewById(R.id.splashText);

        // Simple fade in animation for now (you'd ideally add a custom anim resource)
        logo.setAlpha(0f);
        text.setAlpha(0f);

        logo.animate().alpha(1f).setDuration(1000).start();
        text.animate().alpha(1f).setDuration(1000).setStartDelay(500).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 2000);
    }
}
