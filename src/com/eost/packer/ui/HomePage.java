package com.eost.packer.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eost.packer.MainActivity;
import com.eost.packer.R;
import com.eost.packer.net.Announcement;
import com.eost.packer.net.ApiClient;

import java.util.List;

public class HomePage {
    private final MainActivity act;
    private final View root;
    private final LinearLayout list;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public HomePage(MainActivity act, View root) {
        this.act = act;
        this.root = root;
        this.list = root.findViewById(R.id.ann_list);
        loadAnnouncements();
    }

    private void loadAnnouncements() {
        new Thread(() -> {
            try {
                List<Announcement> data = ApiClient.fetchAnnouncements();
                ui.post(() -> render(data));
            } catch (Exception e) {
                ui.post(() -> {
                    TextView t = new TextView(act);
                    t.setText("网络暂时不可用，公告加载失败");
                    t.setPadding(24, 24, 24, 24);
                    t.setTextColor(0xFF5F6368);
                    list.removeAllViews();
                    list.addView(t);
                });
            }
        }).start();
    }

    private void render(List<Announcement> data) {
        list.removeAllViews();
        if (data == null || data.isEmpty()) {
            TextView t = new TextView(act);
            t.setText(R.string.ann_empty);
            t.setPadding(24, 24, 24, 24);
            t.setTextColor(0xFF5F6368);
            list.addView(t);
            return;
        }
        for (Announcement a : data) {
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(48, 40, 48, 40);
            box.setBackgroundColor(0xFFFFFFFF);
            float d = act.getResources().getDisplayMetrics().density;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int)(12*d), (int)(8*d), (int)(12*d), (int)(8*d));
            box.setLayoutParams(lp);

            TextView title = new TextView(act);
            title.setText(a.title + "  (" + a.version + ")");
            title.setTextSize(16);
            title.setTextColor(0xFF1A1C1E);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            TextView date = new TextView(act);
            date.setText(a.date);
            date.setTextSize(11);
            date.setTextColor(0xFF5F6368);
            TextView body = new TextView(act);
            body.setText(a.body);
            body.setTextSize(13);
            body.setTextColor(0xFF3C4043);
            body.setPadding(0, 16, 0, 0);

            box.addView(title);
            box.addView(date);
            box.addView(body);
            box.setOnClickListener(v -> new AlertDialog.Builder(act)
                    .setTitle(a.title)
                    .setMessage(a.body)
                    .setPositiveButton("知道了", null)
                    .show());
            list.addView(box);
        }
    }
}
