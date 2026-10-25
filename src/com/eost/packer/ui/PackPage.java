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
        root.findViewById(R.id.btn_ai).setOnClickListener(v -> engine.aiHelp());
        btnPack.setOnClickListener(v -> engine.startPack());

        String saved = act.prefs().projectPath();
        if (saved != null && !saved.isEmpty()) tvPath.setText(saved);
    }

    private void chooseZip() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/zip");
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
        tvPath.setText(path);
    }

    private boolean isZipUri(Uri uri) {
        try {
            String mime = act.getContentResolver().getType(uri);
            if (mime != null && mime.contains("zip")) return true;
        } catch (Exception ignored) {}
        try {
            android.database.Cursor c = act.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) {
                    String n = c.getString(idx);
                    c.close();
                    return n != null && (n.endsWith(".zip") || n.endsWith(".apk"));
                }
                c.close();
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isTreeUri(Uri uri) {
        String s = uri.toString();
        return s.contains("/tree/");
    }

    private void showSettings() {
        View box = LayoutInflater.from(act).inflate(R.layout.dialog_perms, null);
        final EditText verEdit = box.findViewById(R.id.ver_edit);
        final ListView lvHas = box.findViewById(R.id.lv_has);
        final ListView lvAll = box.findViewById(R.id.lv_all);

        final List<String> has = new ArrayList<>();
        final ArrayAdapter<String> hasAdp = new ArrayAdapter<String>(act,
                android.R.layout.simple_list_item_1, has) {
            @Override
            public View getView(int pos, View cv, ViewGroup p) {
                TextView t = (TextView) super.getView(pos, cv, p);
                t.setTextColor(0xFF1A1C1E);
                t.setTextSize(13);
                return t;
            }
        };
        lvHas.setAdapter(hasAdp);

        final List<String> all = new ArrayList<>();
        for (String[] p : PERM_LIB) all.add(p[1]);
        final ArrayAdapter<String> allAdp = new ArrayAdapter<String>(act,
                android.R.layout.simple_list_item_1, all) {
            @Override
            public View getView(int pos, View cv, ViewGroup p) {
                TextView t = (TextView) super.getView(pos, cv, p);
                t.setTextColor(0xFF1A1C1E);
                t.setTextSize(13);
                return t;
            }
        };
        lvAll.setAdapter(allAdp);

        // 异步扫描源码中的权限与版本号
        new Thread(() -> {
            final List<String> scanned = engine.scanPermissions();
            final String ver = engine.readVersion();
            ui.post(() -> {
                has.clear();
                for (String s : scanned) {
                    for (String[] p : PERM_LIB) {
                        if (p[0].equals(s)) { has.add(p[1] + "（" + p[0] + "）"); break; }
                    }
                    if (has.size() < scanned.size()) {
                        boolean found = false;
                        for (String[] p : PERM_LIB) {
                            if (p[0].equals(s)) { found = true; break; }
                        }
                        if (!found) has.add(s);
                    }
                }
                hasAdp.notifyDataSetChanged();
                verEdit.setText(ver);
            });
        }).start();

        // 已添加列表：点击删除（真实修改源码）
        lvHas.setOnItemClickListener((p, v, pos, id) -> {
            String raw = has.get(pos);
            String key = raw;
            int lb = raw.indexOf('（');
            if (lb > 0) key = raw.substring(lb + 1, raw.indexOf('）'));
            final String fk = key;
            has.remove(pos);
            hasAdp.notifyDataSetChanged();
            new Thread(() -> engine.removePermissionFromSource(fk)).start();
            act.toast("已从源码移除: " + fk);
        });

        // 权限库：点击添加（真实写入源码）
        lvAll.setOnItemClickListener((p, v, pos, id) -> {
            String cn = all.get(pos);
            String key = null;
            for (String[] pe : PERM_LIB) {
                if (pe[1].equals(cn)) { key = pe[0]; break; }
            }
            if (key == null) return;
            final String fk2 = key;
            for (int i = 0; i < has.size(); i++) {
                if (has.get(i).startsWith(cn)) {
                    act.toast("该权限已添加");
                    return;
                }
            }
            has.add(cn + "（" + fk2 + "）");
            hasAdp.notifyDataSetChanged();
            new Thread(() -> engine.addPermissionToSource(fk2)).start();
            act.toast("已写入源码: " + fk2);
        });

        box.findViewById(R.id.btn_ver_save).setOnClickListener(v -> {
            String nv = verEdit.getText().toString().trim();
            if (nv.isEmpty()) { act.toast("版本号不能为空"); return; }
            new Thread(() -> engine.writeVersion(nv)).start();
            act.toast("版本号已保存: " + nv);
        });

        new AlertDialog.Builder(act)
                .setView(box)
                .setPositiveButton(R.string.close, null)
                .show();
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
}
