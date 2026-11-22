package com.francis.optimize;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PKG_FF = "com.dts.freefireth";
    private static final String PKG_FF_MAX = "com.dts.freefiremax";
    private static final String CHANNEL_ID = "francis_optimize_active";

    private String selectedPackage = PKG_FF;
    private boolean rootAvailable = false;

    private TextView txtRootBadge;
    private TextView txtSelectedPkg;
    private TextView txtGpuLoad;
    private TextView txtEstFps;
    private TextView txtSmoothness;
    private TextView txtResVal;
    private TextView txtTexVal;
    private TextView txtLodVal;
    private TextView txtFpsVal;
    private TextView txtLog;

    private Button btnSelectFF;
    private Button btnSelectFFMax;
    private Button btnPickAnyApp;
    private Button btnLaunchGame;
    private Button btnRestore;

    private SeekBar seekRes;
    private SeekBar seekTex;
    private SeekBar seekLod;
    private SeekBar seekFps;

    private Switch swGlHook;
    private Switch swHwOverlay;
    private Switch swNoAnim;
    private Switch swTrimRam;

    private GlPreviewView glPreview;
    private final StringBuilder logBuf = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupListeners();
        updateMetrics();
        checkRootAsync();
    }

    private void bindViews() {
        txtRootBadge = findViewById(R.id.txtRootBadge);
        txtSelectedPkg = findViewById(R.id.txtSelectedPkg);
        txtGpuLoad = findViewById(R.id.txtGpuLoad);
        txtEstFps = findViewById(R.id.txtEstFps);
        txtSmoothness = findViewById(R.id.txtSmoothness);
        txtResVal = findViewById(R.id.txtResVal);
        txtTexVal = findViewById(R.id.txtTexVal);
        txtLodVal = findViewById(R.id.txtLodVal);
        txtFpsVal = findViewById(R.id.txtFpsVal);
        txtLog = findViewById(R.id.txtLog);

        btnSelectFF = findViewById(R.id.btnSelectFF);
        btnSelectFFMax = findViewById(R.id.btnSelectFFMax);
        btnPickAnyApp = findViewById(R.id.btnPickAnyApp);
        btnLaunchGame = findViewById(R.id.btnLaunchGame);
        btnRestore = findViewById(R.id.btnRestore);

        seekRes = findViewById(R.id.seekRes);
        seekTex = findViewById(R.id.seekTex);
        seekLod = findViewById(R.id.seekLod);
        seekFps = findViewById(R.id.seekFps);

        swGlHook = findViewById(R.id.swGlHook);
        swHwOverlay = findViewById(R.id.swHwOverlay);
        swNoAnim = findViewById(R.id.swNoAnim);
        swTrimRam = findViewById(R.id.swTrimRam);

        // Strictly OFF by default
        swGlHook.setChecked(false);
        swHwOverlay.setChecked(false);
        swNoAnim.setChecked(false);
        swTrimRam.setChecked(false);

        glPreview = findViewById(R.id.glPreview);
        appendLog("FRANCIS OPTIMIZE v2.0 ready.");
    }

    private void setupListeners() {
        btnSelectFF.setOnClickListener(v -> selectTarget(PKG_FF, true, false));
        btnSelectFFMax.setOnClickListener(v -> selectTarget(PKG_FF_MAX, false, true));
        btnPickAnyApp.setOnClickListener(v -> showInstalledAppsDialog());

        findViewById(R.id.btnPresetMaxBlur).setOnClickListener(v -> applyPreset(32, 28, 40, 240, "MAX BLUR"));
        findViewById(R.id.btnPresetPotato).setOnClickListener(v -> applyPreset(45, 45, 30, 150, "POTATO GL"));
        findViewById(R.id.btnPresetSmooth).setOnClickListener(v -> applyPreset(65, 70, 15, 120, "SMOOTH PRO"));
        findViewById(R.id.btnPresetStock).setOnClickListener(v -> applyPreset(100, 100, 0, 60, "STOCK 100%"));

        SeekBar.OnSeekBarChangeListener sliderListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateMetrics();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        seekRes.setOnSeekBarChangeListener(sliderListener);
        seekTex.setOnSeekBarChangeListener(sliderListener);
        seekLod.setOnSeekBarChangeListener(sliderListener);
        seekFps.setOnSeekBarChangeListener(sliderListener);

        btnLaunchGame.setOnClickListener(v -> applyAndLaunch());
        btnRestore.setOnClickListener(v -> restoreStockGraphics());
    }

    private void selectTarget(String pkg, boolean ffSel, boolean ffMaxSel) {
        selectedPackage = pkg;
        txtSelectedPkg.setText(pkg);
        styleSelectionButton(btnSelectFF, ffSel);
        styleSelectionButton(btnSelectFFMax, ffMaxSel);
        styleSelectionButton(btnPickAnyApp, !ffSel && !ffMaxSel);
        appendLog("Target selected: " + pkg);
    }

    private void styleSelectionButton(Button btn, boolean selected) {
        btn.setBackgroundResource(selected ? R.drawable.bg_btn_selected : R.drawable.bg_btn_secondary);
        btn.setTextColor(selected ? Color.BLACK : Color.WHITE);
    }

    private void applyPreset(int resPct, int texPct, int lodTenths, int fpsCap, String name) {
        seekRes.setProgress(Math.max(0, Math.min(75, resPct - 25)));
        seekTex.setProgress(Math.max(0, Math.min(75, texPct - 25)));
        seekLod.setProgress(Math.max(0, Math.min(40, lodTenths)));
        seekFps.setProgress(Math.max(0, Math.min(180, fpsCap - 60)));
        updateMetrics();
        appendLog("Preset loaded: " + name);
    }

    private int getResPercent() { return 25 + seekRes.getProgress(); }
    private int getTexPercent() { return 25 + seekTex.getProgress(); }
    private int getLodTenths()  { return seekLod.getProgress(); }
    private int getFpsCap()     { return 60 + seekFps.getProgress(); }

    private void updateMetrics() {
        int res = getResPercent();
        int tex = getTexPercent();
        int lod = getLodTenths();
        int fps = getFpsCap();

        txtResVal.setText(res + "%");
        txtTexVal.setText(String.format(Locale.US, "%.2fx", tex / 100.0));
        txtLodVal.setText(String.format(Locale.US, "+%.1f", lod / 10.0));
        txtFpsVal.setText(fps + " FPS");

        glPreview.updateParams(res, tex, lod, fps);

        double load = (res * 0.52) + (tex * 0.36) - (lod * 0.35);
        int gpuPct = Math.max(14, Math.min(98, (int) Math.round(load)));
        txtGpuLoad.setText(gpuPct + "%");
        txtEstFps.setText(fps + " FPS");

        if (gpuPct <= 36) txtSmoothness.setText("ULTRA");
        else if (gpuPct <= 55) txtSmoothness.setText("HIGH");
        else if (gpuPct <= 75) txtSmoothness.setText("MEDIUM");
        else txtSmoothness.setText("STOCK");
    }

    private void checkRootAsync() {
        new Thread(() -> {
            boolean ok = RootEngine.hasRootAccess();
            runOnUiThread(() -> {
                rootAvailable = ok;
                if (ok) {
                    txtRootBadge.setText("ROOT: ACTIVE");
                    txtRootBadge.setTextColor(Color.WHITE);
                    appendLog("Root access GRANTED. Full GL Blur Engine active!");
                } else {
                    txtRootBadge.setText("ROOT: OFF");
                    txtRootBadge.setTextColor(Color.rgb(175, 175, 175));
                    appendLog("Root not detected! Enable Root in MSI App Player / BlueStacks for blur.");
                }
            });
        }).start();
    }

    private void showInstalledAppsDialog() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> labels = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();

        Collections.sort(apps, (a, b) -> pm.getApplicationLabel(a).toString()
                .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

        for (ApplicationInfo ai : apps) {
            if (pm.getLaunchIntentForPackage(ai.packageName) != null) {
                labels.add(pm.getApplicationLabel(ai) + " (" + ai.packageName + ")");
                pkgs.add(ai.packageName);
            }
        }

        String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Select App / Game")
                .setItems(items, (dialog, which) -> {
                    String chosen = pkgs.get(which);
                    selectTarget(chosen, PKG_FF.equals(chosen), PKG_FF_MAX.equals(chosen));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyAndLaunch() {
        int res = getResPercent();
        int tex = getTexPercent();
        int lod = getLodTenths();
        int fps = getFpsCap();
        boolean glHook = swGlHook.isChecked();
        boolean hwOv = swHwOverlay.isChecked();
        boolean noAnim = swNoAnim.isChecked();
        boolean trimRam = swTrimRam.isChecked();
        String pkg = selectedPackage;

        btnLaunchGame.setEnabled(false);
        btnLaunchGame.setText("APPLYING BLUR...");
        appendLog("Applying profile to " + pkg + "...");

        new Thread(() -> {
            if (!rootAvailable) {
                rootAvailable = RootEngine.hasRootAccess();
            }

            if (!rootAvailable) {
                runOnUiThread(() -> {
                    btnLaunchGame.setEnabled(true);
                    btnLaunchGame.setText("APPLY BLUR & LAUNCH GAME");
                    appendLog("ERROR: Root access required for real in-emulator GL blur!");
                    appendLog("Tip: Run MSI_BlueStacks_Root_Helper.bat on PC to enable Root.");
                    Toast.makeText(this, "Enable Root in Emulator Settings first!", Toast.LENGTH_LONG).show();
                });
                return;
            }

            RootEngine.ExecResult result = RootEngine.applyBlurProfile(
                    this, pkg, res, tex, lod, fps, glHook, hwOv, noAnim, trimRam);

            runOnUiThread(() -> {
                btnLaunchGame.setEnabled(true);
                btnLaunchGame.setText("APPLY BLUR & LAUNCH GAME");
                appendLog(result.output);
                showRestoreNotification(res);

                // Fallback launch via PackageManager if monkey didn't foreground it
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                }
            });
        }).start();
    }

    private void restoreStockGraphics() {
        appendLog("Restoring 100% stock emulator graphics...");
        applyPreset(100, 100, 0, 60, "STOCK 100%");
        swGlHook.setChecked(false);
        swHwOverlay.setChecked(false);
        swNoAnim.setChecked(false);
        swTrimRam.setChecked(false);

        String pkg = selectedPackage;
        new Thread(() -> {
            RootEngine.restoreStock(pkg);
            runOnUiThread(() -> {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(1001);
                appendLog("Stock resolution & graphics restored!");
                Toast.makeText(this, "Stock Graphics Restored!", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void showRestoreNotification(int resPercent) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Francis Optimize Active", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }

        Intent restoreIntent = new Intent(this, RestoreReceiver.class);
        restoreIntent.setAction(RestoreReceiver.ACTION_RESTORE);
        PendingIntent pi = PendingIntent.getBroadcast(
                this, 0, restoreIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notif = builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("FRANCIS OPTIMIZE ACTIVE (" + resPercent + "% BLUR)")
                .setContentText("Tap here anytime after playing to restore 100% stock graphics.")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();

        nm.notify(1001, notif);
    }

    private void appendLog(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        if (logBuf.length() > 0) logBuf.append("\n");
        logBuf.append("[").append(ts).append("] ").append(msg);
        txtLog.setText(logBuf.toString());
    }
}
