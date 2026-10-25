package com.eost.packer.core;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ToolExtractor {
    private final Context ctx;
    private final File baseDir;
    public final File abiDir;
    private final String abi;

    public ToolExtractor(Context ctx) {
        this.ctx = ctx;
        this.baseDir = new File(ctx.getFilesDir(), "tools");
        if (!baseDir.exists()) baseDir.mkdirs();
        this.abi = pickAbi();
        this.abiDir = new File(baseDir, abi);
    }

    public static String pickAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null) return "armeabi-v7a";
        for (String a : abis) {
            if (a.startsWith("arm64-v8a")) return "arm64-v8a";
        }
        for (String a : abis) {
            if (a.startsWith("armeabi-v7a") || a.startsWith("armeabi")) return "armeabi-v7a";
        }
        for (String a : abis) {
            if (a.startsWith("x86_64")) return "x86_64";
        }
        return "armeabi-v7a";
    }

    public boolean hasTools() {
        return new File(abiDir, "bin/aapt2").exists()
                && new File(abiDir, "bin/zipalign").exists();
    }

    public void ensure() {
        AssetManager am = ctx.getAssets();
        String[] names;
        try {
            names = am.list("tools");
        } catch (Exception e) { return; }
        if (names == null) return;
        for (String n : names) {
            if (!n.equals(abi) && !n.equals("android.jar")) continue;
            File target = new File(baseDir, n);
            if (n.equals("android.jar")) {
                if (target.exists() && target.length() > 0) continue;
                copyAsset(am, "tools/" + n, target);
            } else {
                copyDir(am, "tools/" + n, target);
            }
        }
        // 强制 chmod 755 所有 bin 下二进制（SELinux 兼容）
        chmodBin(new File(baseDir, abi + "/bin"));
        File dexDir = new File(ctx.getFilesDir(), "dex");
        dexDir.mkdirs();
        String[] dexes;
        try {
            dexes = am.list("dex");
        } catch (Exception e) { return; }
        if (dexes == null) return;
        for (String d : dexes) {
            File f = new File(dexDir, d);
            if (f.exists() && f.length() > 0) continue;
            copyAsset(am, "dex/" + d, f);
        }
    }

    private void chmodBin(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) { chmodBin(f); continue; }
            try {
                f.setExecutable(true, false);
                f.setReadable(true, false);
                f.setWritable(true);
                // 兜底：用 chmod 命令
                Runtime.getRuntime().exec(new String[]{"chmod", "755", f.getAbsolutePath()})
                        .waitFor();
            } catch (Exception ignored) {}
        }
    }

    private void copyDir(AssetManager am, String assetPath, File dir) {
        try {
            String[] children = am.list(assetPath);
            if (children != null && children.length > 0) {
                dir.mkdirs();
                for (String c : children) {
                    copyDir(am, assetPath + "/" + c, new File(dir, c));
                }
            } else {
                copyAsset(am, assetPath, dir);
            }
        } catch (Exception e) {
            copyAsset(am, assetPath, dir);
        }
    }

    private void copyAsset(AssetManager am, String path, File target) {
        try (InputStream in = am.open(path); OutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            target.setExecutable(true);
        } catch (Exception ignored) {}
    }

    public File bin(String name) { return new File(abiDir, "bin/" + name); }
    public File libDir() { return new File(abiDir, "lib"); }
    public File androidJar() { return new File(baseDir, "android.jar"); }
    public File dexDir() { return new File(ctx.getFilesDir(), "dex"); }
}
