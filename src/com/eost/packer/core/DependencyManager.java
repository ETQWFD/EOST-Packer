package com.eost.packer.core;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 运行时依赖管理：检测项目所需的 Node.js / npm / Java / Python，
 * 缺少时从 Termux 官方仓库下载 deb 包并解包安装到本机，供打包链路使用。
 */
public class DependencyManager {

    public interface Progress {
        void log(String s);
    }

    private final Context ctx;
    // 清华源（首选）→ 官方源（自动回退）
    private static final String REPO_PRIMARY =
            "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main/dists/stable/main/binary-%s/Packages.gz";
    private static final String REPO_FALLBACK =
            "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-%s/Packages.gz";
    private static final String POOL_PRIMARY =
            "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main/";
    private static final String POOL_FALLBACK =
            "https://packages.termux.dev/apt/termux-main/";
    private String repoBase = "";
    private String repoPool = "";
    private final String arch; // aarch64 / arm
    private Map<String, Map<String, String>> index;

    public DependencyManager(Context ctx) {
        this.ctx = ctx;
        String abi = pickAbi();
        this.arch = abi.contains("64") ? "aarch64" : "arm";
    }

    private String pickAbi() {
        try {
            for (String a : Build.SUPPORTED_ABIS) {
                if (a.startsWith("arm64")) return "arm64-v8a";
                if (a.startsWith("armeabi")) return "armeabi-v7a";
                if (a.startsWith("x86_64")) return "x86_64";
                if (a.startsWith("x86")) return "x86";
            }
        } catch (Throwable ignored) {}
        return "arm64-v8a";
    }

    /** 检测项目需要的运行时，返回缺失列表（含中文名） */
    public List<String> detectNeeds(File project) {
        List<String> missing = new ArrayList<>();
        if (project == null) return missing;
        File pkg = new File(project, "package.json");
        if (pkg.exists()) {
            if (!hasRuntime("nodejs")) missing.add("Node.js（含 npm）");
        }
        boolean javaNeed = new File(project, "build.gradle").exists()
                || new File(project, "build.gradle.kts").exists()
                || new File(project, "pom.xml").exists();
        if (javaNeed && !hasRuntime("java")) missing.add("Java (OpenJDK)");
        boolean pyNeed = new File(project, "requirements.txt").exists()
                || new File(project, "setup.py").exists()
                || new File(project, "pyproject.toml").exists();
        if (pyNeed && !hasRuntime("python")) missing.add("Python");
        return missing;
    }

    /** 运行时是否已安装到本机 */
    public boolean hasRuntime(String key) {
        File run = new File(ctx.getFilesDir(), "tools/run/" + key + "/.installed");
        return run.exists();
    }

    public File runtimeDir(String key) {
        return new File(ctx.getFilesDir(), "tools/run/" + key);
    }

    /** 加载包索引（清华优先，失败回退官方） */
    private void ensureIndex() throws Exception {
        if (index != null) return;
        index = new HashMap<>();
        byte[] raw = null;
        Exception last = null;
        String[] urls = { String.format(REPO_PRIMARY, arch), String.format(REPO_FALLBACK, arch) };
        String[] pools = { POOL_PRIMARY, POOL_FALLBACK };
        for (int i = 0; i < urls.length; i++) {
            try {
                URL u = new URL(urls[i]);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("User-Agent", "EOST-Packer/1.6");
                c.setConnectTimeout(15000);
                c.setReadTimeout(45000);
                if (c.getResponseCode() == 200) {
                    raw = readAll(c.getInputStream());
                    repoBase = urls[i]; repoPool = pools[i];
                    break;
                }
            } catch (Exception e) { last = e; }
        }
        if (raw == null) throw last != null ? last : new Exception("仓库不可达");
        String text = new String(raw, StandardCharsets.UTF_8);
        String[] blocks = text.split("\n\n");
        for (String b : blocks) {
            Map<String, String> m = new HashMap<>();
            String[] lines = b.split("\n");
            String cur = null;
            for (String ln : lines) {
                if (ln.startsWith(" ") && cur != null) { m.put(cur, m.get(cur) + "\n" + ln.trim()); continue; }
                int idx = ln.indexOf(':');
                if (idx > 0) { cur = ln.substring(0, idx); m.put(cur, ln.substring(idx + 1).trim()); }
                else cur = null;
            }
            String name = m.get("Package");
            if (name != null) index.put(name, m);
        }
    }

    /** 下载并安装一个包（含依赖，深度≤2），返回是否成功 */
    public boolean install(String pkgName, Progress progress) {
        try {
            ensureIndex();
            return downloadRecursive(pkgName, 0, new HashSet<>(), progress);
        } catch (Exception e) {
            if (progress != null) progress.log("[依赖] 安装失败: " + e.getMessage());
            return false;
        }
    }

    private boolean downloadRecursive(String name, int depth, Set<String> done, Progress p) throws Exception {
        Map<String, String> meta = index.get(name);
        if (meta == null) { if (p != null) p.log("[依赖] 包 " + name + " 不存在于仓库"); return false; }
        if (done.contains(name)) return true;
        done.add(name);
        String filename = meta.get("Filename");
        String deps = meta.get("Depends");
        long size = 0;
        try { size = Long.parseLong(meta.getOrDefault("Size", "0")); } catch (Exception ignored) {}
        if (p != null) p.log("[依赖] 下载 " + name + (size > 0 ? "（约 " + (size / 1024 / 1024) + " MB）" : ""));
        File deb = downloadToCache((repoPool.isEmpty() ? POOL_FALLBACK : repoPool) + filename, name);
        if (deb == null) {
            // 主源失败时试官方源
            deb = downloadToCache(POOL_FALLBACK + filename, name);
        }
        if (deb == null) return false;
        if (p != null) p.log("[依赖] 解包 " + name + " …");
        boolean ok = DebExtractor.extract(deb, runtimeDir(name));
        deb.delete();
        if (!ok) { if (p != null) p.log("[依赖] 解包失败: " + name); return false; }
        // 依赖递归
        if (deps != null && !deps.trim().isEmpty() && depth < 2) {
            for (String d : deps.split(",")) {
                String dep = d.trim();
                int sp = dep.indexOf(' ');
                if (sp > 0) dep = dep.substring(0, sp);
                if (!dep.isEmpty() && !dep.startsWith("$")) {
                    if (!done.contains(dep)) downloadRecursive(dep, depth + 1, done, p);
                }
            }
        }
        return true;
    }

    private File downloadToCache(String url, String tag) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "EOST-Packer/1.5");
            c.setConnectTimeout(30000);
            c.setReadTimeout(180000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code != 200) return null;
            File f = new File(ctx.getCacheDir(), tag.replaceAll("[^A-Za-z0-9._-]", "_") + ".deb");
            try (InputStream in = c.getInputStream();
                 FileOutputStream fo = new FileOutputStream(f)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) fo.write(buf, 0, r);
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        return bo.toByteArray();
    }
}
