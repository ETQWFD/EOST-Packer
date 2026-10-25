package com.eost.packer.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashLog {
    public static void save(Context ctx, Throwable t) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "etdo");
            if (!dir.exists()) dir.mkdirs();
            File log = new File(dir, "crash.log");
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            try (FileOutputStream fo = new FileOutputStream(log, true)) {
                fo.write(("\n=== " + System.currentTimeMillis() + " ===\n"
                        + sw + "\n").getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {}
    }
}
