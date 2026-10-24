package com.eost.packer.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.eost.packer.MainActivity;
import com.eost.packer.R;
import com.eost.packer.net.ApiClient;
import com.eost.packer.util.AgeCalc;

public class SettingsPage {
    private final MainActivity act;
    private final View root;

    public SettingsPage(MainActivity act, View root) {
        this.act = act;
        this.root = root;
        bindAI();
        bindSign();
        bindTheme();
        bindLang();
        bindUpdate();
        bindAbout();
    }

    private void bindAI() {
        EditText ep = root.findViewById(R.id.ai_endpoint);
        EditText key = root.findViewById(R.id.ai_key);
        EditText model = root.findViewById(R.id.ai_model);
        ep.setText(act.prefs().aiEndpoint());
        key.setText(act.prefs().aiKey());
        model.setText(act.prefs().aiModel());
        root.findViewById(R.id.btn_save_ai).setOnClickListener(v -> {
            act.prefs().set("ai_endpoint", ep.getText().toString());
            act.prefs().set("ai_key", key.getText().toString());
            act.prefs().set("ai_model", model.getText().toString());
            act.toast("AI 配置已保存");
        });
    }

    private void bindSign() {
        RadioGroup g = root.findViewById(R.id.sign_mode);
        int m = act.prefs().signMode();
        ((RadioButton) (m == 1 ? root.findViewById(R.id.sign_v1)
                : m == 2 ? root.findViewById(R.id.sign_v2)
                : root.findViewById(R.id.sign_both))).setChecked(true);
        g.setOnCheckedChangeListener((gr, id) -> {
            int mode = id == R.id.sign_v1 ? 1 : id == R.id.sign_v2 ? 2 : 3;
            act.prefs().setSignMode(mode);
        });
        root.findViewById(R.id.btn_import_sign).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            act.startActivityForResult(Intent.createChooser(i, "导入签名文件(.jks/.keystore)"), 2001);
        });
    }

    private void bindTheme() {
        RadioGroup g = root.findViewById(R.id.theme_mode);
        int m = act.prefs().themeMode();
        ((RadioButton) (m == 0 ? root.findViewById(R.id.theme_light)
                : m == 1 ? root.findViewById(R.id.theme_dark)
                : root.findViewById(R.id.theme_auto))).setChecked(true);
        g.setOnCheckedChangeListener((gr, id) -> {
            int mode = id == R.id.theme_light ? 0 : id == R.id.theme_dark ? 1 : 2;
            act.prefs().setThemeMode(mode);
            act.recreate();
        });
        int[] colors = {R.id.color_blue, R.id.color_green, R.id.color_red, R.id.color_purple};
        int[] vals = {0xFF1F6FEB, 0xFF188038, 0xFFD93025, 0xFF7B1FA2};
        for (int i = 0; i < colors.length; i++) {
            final int c = vals[i];
            root.findViewById(colors[i]).setOnClickListener(v -> {
                act.prefs().setAccent(c);
                act.toast("主题色已切换");
            });
        }
    }

    private void bindLang() {
        RadioGroup g = root.findViewById(R.id.lang_mode);
        if ("en".equals(act.prefs().lang())) {
            ((RadioButton) root.findViewById(R.id.lang_en)).setChecked(true);
        }
        g.setOnCheckedChangeListener((gr, id) -> {
            act.prefs().setLang(id == R.id.lang_en ? "en" : "zh");
            act.toast("语言已切换（重启后完全生效）");
        });
    }

    private void bindUpdate() {
        root.findViewById(R.id.btn_check_update).setOnClickListener(v -> {
            act.toast("正在检查更新…");
            new Thread(() -> {
                try {
                    String r = ApiClient.checkUpdate();
                    String[] parts = r.split("\\|", 2);
                    new Handler(Looper.getMainLooper()).post(() ->
                            new AlertDialog.Builder(act)
                                    .setTitle("发现新版本 " + parts[0])
                                    .setMessage("是否前往官网下载？")
                                    .setPositiveButton("前往", (d, i) ->
                                            act.startActivity(new Intent(Intent.ACTION_VIEW,
                                                    Uri.parse(parts[1]))))
                                    .setNegativeButton("取消", null)
                                    .show());
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> act.toast("已是最新版本"));
                }
            }).start();
        });
    }

    private void bindAbout() {
        ((TextView) root.findViewById(R.id.tv_grade)).setText(AgeCalc.gradeNow());
    }

    public void onShow() {
        ((TextView) root.findViewById(R.id.tv_grade)).setText(AgeCalc.gradeNow());
    }
}
