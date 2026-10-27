package com.eost.packer.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.eost.packer.R;
import com.eost.packer.core.Web2ApkEngine;

public class WebPage {
    private final Activity act;
    private final View root;
    private final TextView tvPath, tvLog;
    private final Web2ApkEngine engine;
    private static final int REQ_ZIP = 4001, REQ_FOLDER = 4002;

    public WebPage(Activity act, View root) {
        this.act = act;
        this.root = root;
        tvPath = root.findViewById(R.id.web_path);
        tvLog = root.findViewById(R.id.web_log);
        engine = new Web2ApkEngine(act, this);

        ((Button) root.findViewById(R.id.web_zip)).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            act.startActivityForResult(Intent.createChooser(i, act.getString(R.string.choose_zip)), REQ_ZIP);
        });
        ((Button) root.findViewById(R.id.web_folder)).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            act.startActivityForResult(i, REQ_FOLDER);
        });
        ((Button) root.findViewById(R.id.web_pack)).setOnClickListener(v -> engine.start());
    }

    public void onActivityResult(int req, int res, Intent data) {
        if (data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        String path = uri.toString();
        if (req == REQ_FOLDER && !path.contains("/tree/")) { ((com.eost.packer.MainActivity) act).toast("请选择文件夹"); return; }
        ((com.eost.packer.MainActivity) act).prefs().setWebPath(path);
        tvPath.setText(req == REQ_FOLDER ? "已选择文件夹 ✓" : "已选择压缩包 ✓");
    }

    public void log(String s) {
        act.runOnUiThread(() -> tvLog.append("\n" + s));
    }
}
