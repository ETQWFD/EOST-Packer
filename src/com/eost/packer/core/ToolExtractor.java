package com.eost.packer.core;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ToolExtractor {
    private final Context ctx;
    public final File dir;

    public ToolExtractor(Context ctx) {
        this.ctx = ctx;
        this.dir = new File(ctx.getFilesDir(), "tools");
        if (!dir.exists()) dir.mkdirs();
    }

    public void ensure() {
        AssetManager am = ctx.getAssets();
        String[] names;
        try {
            names = am.list("tools");
        } catch (Exception e) { return; }
        if (names == null) return;
        for (String n : names) {
            File f = new File(dir, n);
            if (f.exists() && f.length() > 0) continue;
            try (InputStream in = am.open("tools/" + n);
                 OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                f.setExecutable(true);
            } catch (Exception ignored) {}
        }
    }

    public File bin(String name) { return new File(dir, name); }
}
