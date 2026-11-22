package com.francis.optimize;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Locale;

public class SplashActivity extends Activity {

    private ProgressBar progressBar;
    private TextView txtStatus;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int progress = 0;
    private boolean finished = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (finished) return;
            progress += 3;
            if (progress > 100) progress = 100;
            progressBar.setProgress(progress);

            String phase;
            if (progress < 30) {
                phase = "INITIALIZING MONOCHROME GL ENGINE...";
            } else if (progress < 62) {
                phase = "CHECKING EMULATOR ROOT BRIDGE...";
            } else if (progress < 90) {
                phase = "LOADING BLUR & MIPMAP PROFILES...";
            } else {
                phase = "FRANCIS OPTIMIZE READY";
            }
            txtStatus.setText(String.format(Locale.US, "%s [%d%%]", phase, progress));

            if (progress >= 100) {
                openMain();
            } else {
                handler.postDelayed(this, 35);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.splashProgress);
        txtStatus = findViewById(R.id.txtSplashStatus);
        Button btnSkip = findViewById(R.id.btnSkipSplash);

        btnSkip.setOnClickListener(v -> openMain());
        handler.postDelayed(tickRunnable, 40);
    }

    private void openMain() {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(tickRunnable);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        finished = true;
        handler.removeCallbacks(tickRunnable);
        super.onDestroy();
    }
}
