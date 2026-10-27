package com.eost.packer.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.eost.packer.MainActivity;
import com.eost.packer.R;
import com.eost.packer.core.PackEngine;

import java.util.ArrayList;
import java.util.List;

public class PackPage {
    private final MainActivity act;
    private final View root;
    private final TextView tvPath;
    private final TextView tvLog;
    private final ScrollView logScroll;
    private final Button btnPack;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final PackEngine engine;

    private static final int REQ_ZIP = 1001;
    private static final int REQ_FOLDER = 1002;

    /** 全量权限库（中文名，含特殊权限），android.permission. 前缀省略 */
    public static final String[][] PERM_LIB = {
            {"INTERNET", "网络访问"},
            {"ACCESS_NETWORK_STATE", "网络状态"},
            {"ACCESS_WIFI_STATE", "WiFi 状态"},
            {"CHANGE_WIFI_STATE", "修改 WiFi"},
            {"WRITE_SETTINGS", "修改系统设置（特殊）"},
            {"CAMERA", "相机"},
            {"FLASHLIGHT", "闪光灯"},
            {"RECORD_AUDIO", "录音 / 麦克风"},
            {"READ_EXTERNAL_STORAGE", "读取存储"},
            {"WRITE_EXTERNAL_STORAGE", "写入存储"},
            {"MANAGE_EXTERNAL_STORAGE", "管理所有文件（特殊）"},
            {"READ_MEDIA_IMAGES", "读取媒体图片"},
            {"READ_MEDIA_VIDEO", "读取媒体视频"},
            {"READ_MEDIA_AUDIO", "读取媒体音频"},
            {"ACCESS_FINE_LOCATION", "精确定位"},
            {"ACCESS_COARSE_LOCATION", "粗略定位"},
            {"ACCESS_BACKGROUND_LOCATION", "后台定位"},
            {"READ_CONTACTS", "读取联系人"},
            {"WRITE_CONTACTS", "写入联系人"},
            {"GET_ACCOUNTS", "获取账户"},
            {"CALL_PHONE", "拨打电话"},
            {"READ_CALL_LOG", "读取通话记录"},
            {"WRITE_CALL_LOG", "写入通话记录"},
            {"READ_PHONE_STATE", "读取手机状态"},
            {"READ_PHONE_NUMBERS", "读取手机号码"},
            {"READ_SMS", "读取短信"},
            {"SEND_SMS", "发送短信"},
            {"RECEIVE_SMS", "接收短信"},
            {"BLUETOOTH", "蓝牙"},
            {"BLUETOOTH_ADMIN", "蓝牙管理"},
            {"BLUETOOTH_CONNECT", "连接蓝牙设备"},
            {"BLUETOOTH_SCAN", "扫描蓝牙设备"},
            {"NFC", "近场通信 NFC"},
            {"VIBRATE", "振动"},
            {"WAKE_LOCK", "唤醒锁（保持亮屏）"},
            {"RECEIVE_BOOT_COMPLETED", "开机自启动"},
            {"USE_FINGERPRINT", "指纹识别"},
            {"USE_BIOMETRIC", "生物识别"},
            {"BODY_SENSORS", "身体传感器"},
            {"SYSTEM_ALERT_WINDOW", "悬浮窗（特殊）"},
            {"REQUEST_INSTALL_PACKAGES", "安装应用（特殊）"},
            {"REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "忽略电池优化（特殊）"},
            {"ACCESS_NOTIFICATION_POLICY", "通知使用权（特殊）"},
            {"FOREGROUND_SERVICE", "前台服务"},
            {"POST_NOTIFICATIONS", "发送通知"},
            {"QUERY_ALL_PACKAGES", "查询所有应用（特殊）"},
            {"PACKAGE_USAGE_STATS", "使用情况统计（特殊）"},
            {"ACTIVITY_RECOGNITION", "活动识别"},
            {"BIND_ACCESSIBILITY_SERVICE", "无障碍服务（需系统授权）"},
            {"KILL_BACKGROUND_PROCESSES", "关闭后台进程"}
    };

    public PackPage(MainActivity act, View root) {
        this.act = act;
        this.root = root;
        tvPath = root.findViewById(R.id.tv_path);
        tvLog = root.findViewById(R.id.tv_log);
        logScroll = root.findViewById(R.id.log_scroll);
        btnPack = root.findViewById(R.id.btn_pack);
        engine = new PackEngine(act, this);

        root.findViewById(R.id.btn_zip).setOnClickListener(v -> chooseZip());
        root.findViewById(R.id.btn_folder).setOnClickListener(v -> chooseFolder());
        root.findViewById(R.id.btn_pack_settings).setOnClickListener(v -> showSettings());
        root.findViewById(R.id.btn_fix).setOnClickListener(v -> engine.fixDeps());
        root.findViewById(R.id.btn_ai).setOnClickListener(v -> showAiChat());
        btnPack.setOnClickListener(v -> engine.startPack());

        String saved = act.prefs().projectPath();
        if (saved != null && !saved.isEmpty()) {
            // 启动时校验已保存路径：必须是 zip 或目录 tree，否则清空
            boolean ok = saved.contains("/tree/");
            if (!ok) {
                String low = saved.toLowerCase();
                ok = low.contains(".zip") || low.contains(".apk");
            }
            // 不回显 URI，只显示友好提示（用户重新选择后会更新）
            if (ok) tvPath.setText(R.string.project_path);
            else {
                act.prefs().setProjectPath("");
                tvPath.setText(R.string.project_path);
            }
        }
    }

    private void chooseZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        act.startActivityForResult(Intent.createChooser(i, "选择项目压缩包"), REQ_ZIP);
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        act.startActivityForResult(i, REQ_FOLDER);
    }

    public void onActivityResult(int req, int res, Intent data) {
        if (data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (req == REQ_ZIP) {
            // 必须是 zip 压缩包
            if (!isZipUri(uri)) {
                act.toast("请选择 .zip 压缩包（当前选择不是压缩包）");
                return;
            }
        } else if (req == REQ_FOLDER) {
            // 必须是目录树（tree/）
            if (!isTreeUri(uri)) {
                act.toast("请选择文件夹（当前选择不是文件夹）");
                return;
            }
        }
        String path = uri.toString();
        act.prefs().setProjectPath(path);
        // 显示友好名称而不是 content:// URI
        String name = friendlyName(uri, req);
        tvPath.setText(name);
    }

    private String friendlyName(Uri uri, int req) {
        if (req == REQ_FOLDER) return "已选择文件夹 ✓";
        // zip：取文件名
        try {
            android.database.Cursor c = act.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) {
                    String n = c.getString(idx);
                    c.close();
                    return "已选择压缩包: " + n;
                }
                c.close();
            }
        } catch (Exception ignored) {}
        return "已选择压缩包 ✓";
    }

    private boolean isZipUri(Uri uri) {
        // 放宽：只要能打开就接受（不强制 mime/扩展名）
        try {
            String mime = act.getContentResolver().getType(uri);
            if (mime != null && (mime.contains("zip") || mime.contains("octet-stream") || mime.contains("binary"))) return true;
        } catch (Exception ignored) {}
        try {
            android.database.Cursor c = act.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) {
                    String n = c.getString(idx);
                    c.close();
                    if (n != null) return true; // 只要有文件名就接受
                }
                c.close();
            }
        } catch (Exception ignored) {}
        String s = uri.toString();
        return s.startsWith("content://") || s.startsWith("file://");
    }

    private boolean isTreeUri(Uri uri) {
        String s = uri.toString();
        return s.contains("/tree/");
    }

    private void showSettings() {
        View box = LayoutInflater.from(act).inflate(R.layout.dialog_perms, null);
        final EditText verEdit = box.findViewById(R.id.ver_edit);
        final EditText pkgEdit = box.findViewById(R.id.pkg_edit);
        final ListView lvHas = box.findViewById(R.id.lv_has);
        final ListView lvAll = box.findViewById(R.id.lv_all);

        final List<String> hasKeys = new ArrayList<>();
        final ArrayAdapter<String> hasAdp = new ArrayAdapter<String>(act,
                android.R.layout.simple_list_item_1, hasKeys) {
            @Override
            public View getView(int pos, View cv, ViewGroup p) {
                TextView t = (TextView) super.getView(pos, cv, p);
                t.setTextColor(0xFF1A1C1E); t.setTextSize(13);
                return t;
            }
        };
        lvHas.setAdapter(hasAdp);

        final List<String> allKeys = new ArrayList<>();
        final ArrayAdapter<String> allAdp = new ArrayAdapter<String>(act,
                android.R.layout.simple_list_item_1, allKeys) {
            @Override
            public View getView(int pos, View cv, ViewGroup p) {
                TextView t = (TextView) super.getView(pos, cv, p);
                t.setTextColor(0xFF1A1C1E); t.setTextSize(13);
                return t;
            }
        };
        lvAll.setAdapter(allAdp);

        // 双向同步：根据 hasKeys 刷新两个列表
        final Runnable syncLists = () -> {
            hasAdp.clear(); allAdp.clear();
            for (String k : PERM_LIB_KEYS) {
                if (hasKeys.contains(k)) hasAdp.add(labelOf(k) + "  [" + k + "]");
                else allAdp.add(labelOf(k) + "  [" + k + "]");
            }
            for (String k : hasKeys) {
                if (!listContains(PERM_LIB_KEYS, k)) hasAdp.add(k);
            }
            hasAdp.notifyDataSetChanged(); allAdp.notifyDataSetChanged();
        };

        // 异步扫描源码中的权限/版本/包名
        new Thread(() -> {
            final List<String> scanned = engine.scanPermissions();
            final String ver = engine.readVersion();
            final String pkg = engine.readPackageName();
            ui.post(() -> {
                hasKeys.clear(); hasKeys.addAll(scanned);
                syncLists.run();
                verEdit.setText(ver);
                pkgEdit.setText(pkg);
            });
        }).start();

        // 已添加：点击删除 → 回到未添加列表
        lvHas.setOnItemClickListener((p, v, pos, id) -> {
            String key = extractKey((String) hasAdp.getItem(pos));
            hasKeys.remove(key);
            new Thread(() -> engine.removePermissionFromSource(key)).start();
            act.toast("已从源码移除: " + key);
            syncLists.run();
        });

        // 未添加：点击添加 → 从未添加消失进入已添加
        lvAll.setOnItemClickListener((p, v, pos, id) -> {
            String key = extractKey((String) allAdp.getItem(pos));
            if (hasKeys.contains(key)) return;
            hasKeys.add(key);
            new Thread(() -> engine.addPermissionToSource(key)).start();
            act.toast("已写入源码: " + key);
            syncLists.run();
        });

        box.findViewById(R.id.btn_ver_save).setOnClickListener(v -> {
            String nv = verEdit.getText().toString().trim();
            if (nv.isEmpty()) { act.toast("版本号不能为空"); return; }
            new Thread(() -> engine.writeVersion(nv)).start();
            act.toast("版本号已保存: " + nv);
        });
        box.findViewById(R.id.btn_pkg_save).setOnClickListener(v -> {
            String nv = pkgEdit.getText().toString().trim();
            if (nv.isEmpty()) { act.toast("包名不能为空"); return; }
            if (!nv.matches("[a-zA-Z][a-zA-Z0-9_\\.]*")) { act.toast("包名格式不正确"); return; }
            new Thread(() -> engine.writePackageName(nv)).start();
            act.toast("包名已保存: " + nv);
        });

        new AlertDialog.Builder(act)
                .setView(box)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private static boolean listContains(String[] arr, String v) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }
    private static String labelOf(String key) {
        for (String[] p : PERM_LIB) if (p[0].equals(key)) return p[1];
        return key;
    }
    private static String extractKey(String item) {
        int lb = item.lastIndexOf('[');
        if (lb >= 0 && item.endsWith("]")) return item.substring(lb + 1, item.length() - 1);
        return item.trim();
    }
    private static final String[] PERM_LIB_KEYS;
    static {
        PERM_LIB_KEYS = new String[PERM_LIB.length];
        for (int i = 0; i < PERM_LIB.length; i++) PERM_LIB_KEYS[i] = PERM_LIB[i][0];
    }

    /** 依赖缺失时弹出的下载窗口 */
    public void showDepsDialog(com.eost.packer.core.DependencyManager dm, java.util.List<String> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("检测到项目需要以下运行时依赖：\n\n");
        for (String s : missing) sb.append(" · ").append(s).append("\n");
        sb.append("\n点击下载后将自动从 Termux 官方仓库下载并解包安装，安装完成后即可正常使用。\n\n注意：Java 完整运行时较大（约 200MB+），若项目为已编译安卓工程（含 classes.dex），可无需下载。");
        new AlertDialog.Builder(act)
                .setTitle("自动下载依赖")
                .setMessage(sb.toString())
                .setPositiveButton("自动下载", (d, i) -> {
                    d.dismiss();
                    log("[依赖] 开始自动下载…");
                    new Thread(() -> {
                        boolean all = true;
                        for (String s : missing) {
                            String key = s.startsWith("Node") ? "nodejs-lts"
                                    : s.startsWith("Java") ? "openjdk-17"
                                    : s.startsWith("Python") ? "python" : null;
                            if (key == null) continue;
                            log("[依赖] 下载安装 " + key + " …");
                            boolean ok = dm.install(key, PackPage.this::log);
                            log(ok ? "[依赖] " + key + " 安装完成" : "[依赖] " + key + " 安装失败，请检查网络后重试");
                            if (!ok) all = false;
                        }
                        log(all ? "[依赖] 全部依赖已就绪" : "[依赖] 部分依赖安装未完成，可重新点击补全重试");
                    }).start();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void log(String s) {
        ui.post(() -> {
            tvLog.append(s + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    public void setPackEnabled(boolean e) {
        ui.post(() -> btnPack.setEnabled(e));
    }

    public String projectPath() { return tvPath.getText().toString(); }

    /** AI 聊天弹窗：用户输入问题，AI 响应显示在弹窗内 */
    private void showAiChat() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(act);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(24, 24, 24, 24);
        final android.widget.ScrollView sv = new android.widget.ScrollView(act);
        final android.widget.TextView tv = new android.widget.TextView(act);
        tv.setTextSize(14);
        tv.setText("你好！我是 EOST AI 助手。输入你的问题（如：aapt2 报错怎么办？帮我修 build.gradle），我会帮你分析并给出修复建议。\n\n");
        sv.addView(tv);
        final android.widget.EditText et = new android.widget.EditText(act);
        et.setHint("输入问题...");
        et.setMinLines(2);
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = 16;
        et.setLayoutParams(p);
        box.addView(sv);
        box.addView(et);
        new android.app.AlertDialog.Builder(act)
                .setTitle("AI 助手")
                .setView(box)
                .setPositiveButton("发送", (d, w) -> {
                    String q = et.getText().toString().trim();
                    if (q.isEmpty()) return;
                    tv.append("你: " + q + "\n\n");
                    et.setText("");
                    // 调用 engine.aiHelp() 获取响应（在后台线程）
                    new Thread(() -> {
                        String resp = engine.aiHelpSync(q);
                        ui.post(() -> {
                            tv.append("AI: " + resp + "\n\n");
                            sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
                        });
                    }).start();
                })
                .setNegativeButton("关闭", null)
                .show();
    }
}
