package com.eost.packer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.eost.packer.net.ApiClient;
import com.eost.packer.svc.PackService;
import com.eost.packer.ui.HomePage;
import com.eost.packer.ui.PackPage;
import com.eost.packer.ui.SettingsPage;
import com.eost.packer.util.Prefs;

public class MainActivity extends Activity implements View.OnClickListener {

    private View homeView, packView, settingsView;
    private Button tabHome, tabPack, tabSettings;
    private Prefs prefs;
    private HomePage homePage;
    private PackPage packPage;
    private SettingsPage settingsPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            prefs = new Prefs(this);
            applyLocale();
            applyTheme();
            setContentView(R.layout.main);
            applyBackground();

            homeView = getLayoutInflater().inflate(R.layout.home_fragment, null);
            packView = getLayoutInflater().inflate(R.layout.pack_fragment, null);
            settingsView = getLayoutInflater().inflate(R.layout.settings_fragment, null);

            homePage = new HomePage(this, homeView);
            packPage = new PackPage(this, packView);
            settingsPage = new SettingsPage(this, settingsView);

            FrameLayout_replace(R.id.container, homeView);

            tabHome = findViewById(R.id.tab_home);
            tabPack = findViewById(R.id.tab_pack);
            tabSettings = findViewById(R.id.tab_settings);
            tabHome.setOnClickListener(this);
            tabPack.setOnClickListener(this);
            tabSettings.setOnClickListener(this);

            requestPermissions();
            try {
                startService(new Intent(this, PackService.class));
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            // 启动绝不闪退：任何异常记录到 etdo/crash.log 并给出提示
            try {
                android.widget.Toast.makeText(this,
                        "启动异常已记录到 etdo/crash.log: " + t.getMessage(),
                        android.widget.Toast.LENGTH_LONG).show();
                com.eost.packer.util.CrashLog.save(this, t);
            } catch (Throwable ignored) {}
        }
    }

    private void applyLocale() {
        try {
            String lang = prefs.lang();
            if (lang == null || lang.isEmpty()) lang = "zh";
            java.util.Locale loc = new java.util.Locale(lang);
            android.content.res.Configuration cfg = getResources().getConfiguration();
            cfg.setLocale(loc);
            getResources().updateConfiguration(cfg, getResources().getDisplayMetrics());
        } catch (Throwable ignored) {}
    }

    private void applyBackground() {
        try {
            String bg = prefs.bgPath();
            if (bg != null && !bg.isEmpty()) {
                android.graphics.drawable.Drawable d = null;
                try {
                    if (bg.startsWith("content://")) {
                        d = android.graphics.drawable.Drawable.createFromStream(
                                getContentResolver().openInputStream(Uri.parse(bg)), "bg");
                    }
                } catch (Exception ignored) {}
                if (d != null) {
                    findViewById(R.id.container).setBackground(d);
                    return;
                }
            }
            findViewById(R.id.container).setBackgroundColor(
                    getResources().getColor(R.color.bg_default));
        } catch (Throwable ignored) {}
    }

    private void applyTheme() {
        int m = prefs.themeMode();
        boolean dark = (m == 1) || (m == 2 && (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark ? R.style.Theme_EOST_Dark : R.style.Theme_EOST);
        try {
            getWindow().setStatusBarColor(prefs.accent());
        } catch (Throwable ignored) {}
    }

    private void requestPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception ignored) {}
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    }, 1);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            }
        } catch (Throwable ignored) {
        }
    }

    private void FrameLayout_replace(int id, View v) {
        ViewGroup container = findViewById(id);
        container.removeAllViews();
        container.addView(v, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.tab_home) {
            FrameLayout_replace(R.id.container, homeView);
            setTab(tabHome);
        } else if (id == R.id.tab_pack) {
            FrameLayout_replace(R.id.container, packView);
            setTab(tabPack);
        } else if (id == R.id.tab_settings) {
            FrameLayout_replace(R.id.container, settingsView);
            setTab(tabSettings);
            settingsPage.onShow();
        }
    }

    private void setTab(Button active) {
        int on = prefs.accent();
        int off = getResources().getColor(R.color.text_sub);
        tabHome.setTextColor(active == tabHome ? on : off);
        tabPack.setTextColor(active == tabPack ? on : off);
        tabSettings.setTextColor(active == tabSettings ? on : off);
    }

    public Prefs prefs() { return prefs; }
    public PackPage packPage() { return packPage; }

    public void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (packPage != null) packPage.onActivityResult(req, res, data);
        if (req == 2001 && data != null && data.getData() != null) {
            if (settingsPage != null) settingsPage.onSignPicked(data.getData());
        }
        if (req == 3001) {
            Uri u = data == null ? null : data.getData();
            if (settingsPage != null) settingsPage.onBgPicked(u);
        }
    }
}
