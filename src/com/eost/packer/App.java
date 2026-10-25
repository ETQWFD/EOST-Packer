package com.eost.packer;

import android.app.Application;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                File dir = new File(Environment.getExternalStorageDirectory(), "etdo");
                if (!dir.exists()) dir.mkdirs();
                File log = new File(dir, "crash.log");
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                try (FileOutputStream fo = new FileOutputStream(log, true)) {
                    fo.write(("\n=== " + System.currentTimeMillis() + " ===\n"
                            + sw + "\n").getBytes("UTF-8"));
                }
            } catch (Exception ignored) {}
            if (def != null) def.uncaughtException(t, e);
        });
    }
}
