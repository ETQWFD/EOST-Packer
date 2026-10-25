package com.eost.packer.svc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class PackService extends Service {
    private static final String CH = "eost_pack";
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "EOST Packer",
                        NotificationManager.IMPORTANCE_LOW);
                getSystemService(NotificationManager.class).createNotificationChannel(ch);
            }
            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                n = new Notification.Builder(this, CH)
                        .setContentTitle("EOST Packer")
                        .setContentText("后台运行中，打包进度将显示在这里")
                        .setSmallIcon(android.R.drawable.stat_notify_sync)
                        .build();
            } else {
                n = new Notification.Builder(this)
                        .setContentTitle("EOST Packer")
                        .setContentText("后台运行中")
                        .setSmallIcon(android.R.drawable.stat_notify_sync)
                        .build();
            }
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1001, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1001, n);
            }
        } catch (Throwable ignored) {
            // 后台服务启动失败不影响主界面，直接降级为普通启动
            try {
                startForeground(1001, Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(this, CH)
                            .setContentTitle("EOST Packer")
                            .setContentText("运行中")
                            .setSmallIcon(android.R.drawable.stat_notify_sync)
                            .build()
                        : new Notification.Builder(this)
                            .setContentTitle("EOST Packer")
                            .setContentText("运行中")
                            .setSmallIcon(android.R.drawable.stat_notify_sync)
                            .build());
            } catch (Throwable ignored2) {}
        }
    }
    @Override
    public IBinder onBind(Intent i) { return null; }
}
