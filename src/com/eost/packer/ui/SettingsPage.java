package com.eost.packer.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eost.packer.MainActivity;
import com.eost.packer.R;
import com.eost.packer.net.ApiClient;
import com.eost.packer.util.AgeCalc;

public class SettingsPage {
    private final MainActivity act;
    private final View root;
    private static final int REQ_BG = 3001;

    public SettingsPage(MainActivity act, View root) {
        this.act = act;
        this.root = root;
        bindAI();
        bindSign();
        bindTheme();
        bindColor();
        bindBg();
        bindLang();
        bindUpdate();
        bindAbout();
    }

    private android.app.Dialog dialog() {
        return null; // helper placeholder
    }

    private void bindAI() {
        root.findViewById(R.id.row_ai).setOnClickListener(v -> {
            View box = LayoutInflater.from(act).inflate(R.layout.dialog_ai, null);
            final EditText ep = box.findViewById(R.id.ai_endpoint);
            final EditText key = box.findViewById(R.id.ai_key);
            final EditText model = box.findViewById(R.id.ai_model);
            ep.setText(act.prefs().aiEndpoint());
            key.setText(act.prefs().aiKey());
            model.setText(act.prefs().aiModel());
            new AlertDialog.Builder(act)
                    .setView(box)
                    .setTitle(R.string.ai_config)
                    .setPositiveButton(R.string.save, (d, i) -> {
                        act.prefs().set("ai_endpoint", ep.getText().toString().trim());
                        act.prefs().set("ai_key", key.getText().toString().trim());
                        act.prefs().set("ai_model", model.getText().toString().trim());
                        act.toast("AI 配置已保存");
                        refreshVals();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void bindSign() {
        root.findViewById(R.id.row_sign).setOnClickListener(v -> {
            int cur = act.prefs().signMode();
            String[] items = {str(R.string.sign_v1), str(R.string.sign_v2), str(R.string.sign_v1v2)};
            new AlertDialog.Builder(act)
                    .setTitle(R.string.sign_title)
                    .setSingleChoiceItems(items, cur - 1, (d, i) -> {
                        act.prefs().setSignMode(i + 1);
                        d.dismiss();
                        act.toast("签名方案已保存: " + items[i]);
                        refreshVals();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void bindTheme() {
        root.findViewById(R.id.row_theme).setOnClickListener(v -> {
            int cur = act.prefs().themeMode();
            String[] items = {str(R.string.theme_light), str(R.string.theme_dark), str(R.string.theme_auto)};
            new AlertDialog.Builder(act)
                    .setTitle(R.string.theme_title)
                    .setSingleChoiceItems(items, cur, (d, i) -> {
                        act.prefs().setThemeMode(i);
                        d.dismiss();
                        act.toast("主题已切换");
                        act.recreate();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void bindColor() {
        root.findViewById(R.id.row_color).setOnClickListener(v -> {
            final int[] colors = {0xFF1F6FEB, 0xFF188038, 0xFFD93025, 0xFF7B1FA2};
            final String[] names = {str(R.string.accent_blue), str(R.string.accent_green),
                    str(R.string.accent_red), str(R.string.accent_purple)};
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setPadding(40, 16, 40, 16);
            for (int i = 0; i < colors.length; i++) {
                final int c = colors[i];
                View sw = new View(act);
                android.widget.LinearLayout.LayoutParams lp =
                        new android.widget.LinearLayout.LayoutParams(52, 52);
                lp.setMargins(10, 0, 10, 0);
                sw.setLayoutParams(lp);
                sw.setBackground(roundColor(c));
                final String nm = names[i];
                sw.setOnClickListener(sw2 -> {
                    act.prefs().setAccent(c);
                    act.toast("主题颜色已切换: " + nm);
                    act.recreate();
                });
                box.addView(sw);
            }
            new AlertDialog.Builder(act)
                    .setTitle(R.string.accent_title)
                    .setView(box)
                    .setPositiveButton(R.string.cancel, null)
                    .show();
        });
    }

    private void bindBg() {
        root.findViewById(R.id.row_bg).setOnClickListener(v -> {
            String[] items = {str(R.string.bg_choose), str(R.string.bg_reset)};
            new AlertDialog.Builder(act)
                    .setTitle(R.string.bg_title)
                    .setItems(items, (d, i) -> {
                        if (i == 0) {
                            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                            it.setType("image/*");
                            act.startActivityForResult(
                                    Intent.createChooser(it, "选择背景图片"), REQ_BG);
                        } else {
                            act.prefs().setBgPath("");
                            act.toast("已恢复默认背景");
                            act.recreate();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void bindLang() {
        root.findViewById(R.id.row_lang).setOnClickListener(v -> {
            int cur = "en".equals(act.prefs().lang()) ? 1 : 0;
            String[] items = {str(R.string.lang_zh), str(R.string.lang_en)};
            new AlertDialog.Builder(act)
                    .setTitle(R.string.lang_title)
                    .setSingleChoiceItems(items, cur, (d, i) -> {
                        act.prefs().setLang(i == 1 ? "en" : "zh");
                        d.dismiss();
                        act.toast("语言已切换");
                        act.recreate();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void bindUpdate() {
        root.findViewById(R.id.row_update).setOnClickListener(v -> {
            act.toast("正在检查更新…");
            new Thread(() -> {
                try {
                    String cur = "";
                    try {
                        cur = act.getPackageManager()
                                .getPackageInfo(act.getPackageName(), 0).versionName;
                    } catch (Exception ignored) {}
                    final String curVer = cur;
                    String[] r = ApiClient.checkUpdate(curVer);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (r == null) {
                            act.toast("当前已是最新版本 " + curVer);
                        } else {
                            new AlertDialog.Builder(act)
                                    .setTitle("发现新版本 " + r[0])
                                    .setMessage("当前版本 " + curVer + "，是否前往官网下载？")
                                    .setPositiveButton("前往", (d, i) ->
                                            act.startActivity(new Intent(Intent.ACTION_VIEW,
                                                    Uri.parse(r[1]))))
                                    .setNegativeButton(R.string.cancel, null)
                                    .show();
                        }
                    });
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            act.toast("检查更新失败：网络不可用或服务器未响应"));
                }
            }).start();
        });
    }

    private void bindAbout() {
        ((TextView) root.findViewById(R.id.tv_grade)).setText(AgeCalc.gradeNow());
    }

    public void onShow() {
        refreshVals();
        ((TextView) root.findViewById(R.id.tv_grade)).setText(AgeCalc.gradeNow());
    }

    public void onBgPicked(Uri uri) {
        if (uri != null) {
            act.prefs().setBgPath(uri.toString());
            act.toast("背景图片已更换");
            act.recreate();
        }
    }

    private void refreshVals() {
        TextView v1 = root.findViewById(R.id.val_ai);
        if (v1 != null) {
            String m = act.prefs().aiModel();
            v1.setText(m.isEmpty() ? "未配置" : m);
        }
        TextView v2 = root.findViewById(R.id.val_sign);
        if (v2 != null) {
            int m = act.prefs().signMode();
            v2.setText(m == 1 ? str(R.string.sign_v1) : m == 2 ? str(R.string.sign_v2) : str(R.string.sign_v1v2));
        }
        TextView v3 = root.findViewById(R.id.val_theme);
        if (v3 != null) {
            int m = act.prefs().themeMode();
            v3.setText(m == 0 ? str(R.string.theme_light) : m == 1 ? str(R.string.theme_dark) : str(R.string.theme_auto));
        }
        TextView v4 = root.findViewById(R.id.val_color);
        if (v4 != null) {
            int c = act.prefs().accent();
            v4.setText(c == 0xFF188038 ? str(R.string.accent_green)
                    : c == 0xFFD93025 ? str(R.string.accent_red)
                    : c == 0xFF7B1FA2 ? str(R.string.accent_purple) : str(R.string.accent_blue));
        }
        TextView v5 = root.findViewById(R.id.val_bg);
        if (v5 != null) {
            String p = act.prefs().bgPath();
            v5.setText(p.isEmpty() ? "默认" : "已自定义");
        }
        TextView v6 = root.findViewById(R.id.val_lang);
        if (v6 != null) {
            v6.setText("en".equals(act.prefs().lang()) ? str(R.string.lang_en) : str(R.string.lang_zh));
        }
    }

    private String str(int id) { return act.getString(id); }

    private android.graphics.drawable.Drawable roundColor(final int c) {
        return new android.graphics.drawable.GradientDrawable() {{
            setColor(c);
            setCornerRadius(14);
        }};
    }
}
