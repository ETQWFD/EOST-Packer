package com.eost.packer.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.eost.packer.MainActivity;
import com.eost.packer.R;
import com.eost.packer.core.PackEngine;

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
        String path = uri.toString();
        act.prefs().setProjectPath(path);
        tvPath.setText(path);
    }

    private void showSettings() {
        String[] perms = {"INTERNET","ACCESS_NETWORK_STATE","WRITE_EXTERNAL_STORAGE",
                "READ_EXTERNAL_STORAGE","CAMERA","RECORD_AUDIO","ACCESS_FINE_LOCATION",
                "ACCESS_COARSE_LOCATION","BLUETOOTH","NFC","CALL_PHONE","READ_CONTACTS",
                "READ_SMS","SEND_SMS","RECEIVE_SMS","GET_ACCOUNTS","USE_FINGERPRINT",
                "WAKE_LOCK","VIBRATE","RECEIVE_BOOT_COMPLETED","ACCESS_NOTIFICATION_POLICY",
                "SYSTEM_ALERT_WINDOW","REQUEST_INSTALL_PACKAGES","MANAGE_EXTERNAL_STORAGE"};
        new AlertDialog.Builder(act)
                .setTitle(R.string.perm_title)
                .setItems(perms, (d, i) -> engine.addPermission(perms[i]))
                .setPositiveButton(R.string.perm_add, null)
                .setNegativeButton(R.string.perm_delete, (d,i)-> engine.removePermission())
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
