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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.eost.packer.net.ApiClient;
import com.eost.packer.svc.PackService;
import com.eost.packer.ui.HomePage;
import com.eost.packer.ui.PackPage;
import com.eost.packer.ui.SettingsPage;
import com.eost.packer.ui.WebPage;
import com.eost.packer.util.Prefs;

public class MainActivity extends Activity implements View.OnClickListener {

    private View homeView, packView, webView, settingsView;
    private Button tabHome, tabPack, tabWeb, tabSettings;
    private Prefs prefs;
    private HomePage homePage;
    private PackPage packPage;
    private WebPage webPage;
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
            webView = getLayoutInflater().inflate(R.layout.web_fragment, null);
            settingsView = getLayoutInflater().inflate(R.layout.settings_fragment, null);

            homePage = new HomePage(this, homeView);
            packPage = new PackPage(this, packView);
            webPage = new WebPage(this, webView);
            settingsPage = new SettingsPage(this, settingsView);

            FrameLayout_replace(R.id.container, homeView);
            applyDarkColors();

            tabHome = findViewById(R.id.tab_home);
            tabPack = findViewById(R.id.tab_pack);
            tabWeb = findViewById(R.id.tab_web);
            tabSettings = findViewById(R.id.tab_settings);
            tabHome.setOnClickListener(this);
            tabPack.setOnClickListener(this);
            tabWeb.setOnClickListener(this);
            tabSettings.setOnClickListener(this);

            requestPermissions();
            try {
                startService(new Intent(this, PackService.class));
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
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

    public boolean isDark() {
        int m = prefs.themeMode();
        return (m == 1) || (m == 2 && (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES);
    }

    private void applyTheme() {
        setTheme(isDark() ? R.style.Theme_EOST_Dark : R.style.Theme_EOST);
        try {
            getWindow().setStatusBarColor(prefs.accent());
        } catch (Throwable ignored) {}
    }

    /** 暗黑模式真正生效：递归把所有卡片/文字/滚动条切成暗色 */
    public void applyDarkColors() {
        try {
            boolean dark = isDark();
            int pageBg = dark ? 0xFF121416 : 0xFFF2F3F5;
            int cardBg = dark ? 0xFF1E2125 : 0xFFFFFFFF;
            int textP = dark ? 0xFFE8EAED : 0xFF1A1C1E;
            int textS = dark ? 0xFF9AA0A6 : 0xFF5F6368;
            View root = ((ViewGroup) getWindow().getDecorView()).getChildAt(0);
            if (root == null) return;
            // 如果用户自定义了背景图，不覆盖 root 背景
            String bg = prefs.bgPath();
            if (bg == null || bg.isEmpty()) root.setBackgroundColor(pageBg);
            applyNode(root, dark, pageBg, cardBg, textP, textS, 0);
        } catch (Throwable ignored) {}
    }

    private void applyNode(View v, boolean dark, int pageBg, int cardBg, int textP, int textS, int depth) {
        // tabbar 保持白底
        if (v.getId() == R.id.tabbar) {
            v.setBackgroundColor(0xFFFFFFFF);
            return;
        }
        // 容器（ScrollView/外层 LinearLayout）背景
        if (v instanceof ScrollView) {
            v.setBackgroundColor(pageBg);
        } else if (v instanceof android.widget.LinearLayout && !(v instanceof android.widget.Button)
                && v.getId() != R.id.container && v.getId() != R.id.tabbar) {
            // 卡片式 LinearLayout：统一卡片色
            v.setBackgroundColor(cardBg);
        }
        // 文字变色：跳过 Button（按钮有自己的样式，浅蓝底配深色字）
        if (v instanceof TextView && !(v instanceof android.widget.Button)) {
            TextView t = (TextView) v;
            int c = t.getCurrentTextColor();
            if (c == prefs.accent() || c == 0xFF1F6FEB || c == 0xFF188038) {
                // 主题色/绿色保持
            } else {
                if (dark) {
                    if (c == 0xFF5F6368 || c == 0xFF80868B || c == 0xFF9AA0A6) t.setTextColor(textS);
                    else if (c != 0xFFFFFFFF && c != 0xFFE8EAED) t.setTextColor(textP);
                } else {
                    if (c == 0xFF9AA0A6 || c == 0xFFE8EAED || c == 0xFFFFFFFF) t.setTextColor(textP);
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyNode(g.getChildAt(i), dark, pageBg, cardBg, textP, textS, depth + 1);
        }
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
        } else if (id == R.id.tab_web) {
            FrameLayout_replace(R.id.container, webView);
            setTab(tabWeb);
        } else if (id == R.id.tab_settings) {
            FrameLayout_replace(R.id.container, settingsView);
            setTab(tabSettings);
            settingsPage.onShow();
        }
        applyDarkColors();
    }

    private void setTab(Button active) {
        int on = prefs.accent();
        int off = getResources().getColor(R.color.text_sub);
        tabHome.setTextColor(active == tabHome ? on : off);
        tabPack.setTextColor(active == tabPack ? on : off);
        tabWeb.setTextColor(active == tabWeb ? on : off);
        tabSettings.setTextColor(active == tabSettings ? on : off);
    }

    public Prefs prefs() { return prefs; }
    public PackPage packPage() { return packPage; }

    public void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (packPage != null) packPage.onActivityResult(req, res, data);
        if (webPage != null) webPage.onActivityResult(req, res, data);
        if (req == 2001 && data != null && data.getData() != null) {
            if (settingsPage != null) settingsPage.onSignPicked(data.getData());
        }
        if (req == 3001) {
            Uri u = data == null ? null : data.getData();
            if (settingsPage != null) settingsPage.onBgPicked(u);
        }
    }
}
