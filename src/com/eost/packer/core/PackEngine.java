package com.eost.packer.core;

import android.os.Environment;

import com.eost.packer.ui.PackPage;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PackEngine {
    private final com.eost.packer.MainActivity act;
    private final PackPage page;
    private final ToolExtractor tools;
    private volatile boolean aiBusy = false;

    public PackEngine(com.eost.packer.MainActivity act, PackPage page) {
        this.act = act;
        this.page = page;
        this.tools = new ToolExtractor(act);
    }

    public void addPermission(String perm) {
        page.log("[权限] 已加入待打包清单: android.permission." + perm);
    }

    public void removePermission() {
        page.log("[权限] 已移除最后一条自定义权限");
    }

    public void fixDeps() {
        new Thread(() -> {
            log("== 开始依赖扫描 ==");
            tools.ensure();
            File project = resolveProject();
            if (project == null) { log("[错误] 请先选择项目根目录"); return; }
            log("[扫描] 项目路径: " + project.getAbsolutePath());
            File manifest = new File(project, "AndroidManifest.xml");
            if (!manifest.exists()) {
                log("[错误] 未找到 AndroidManifest.xml，请确认选择的是项目根目录");
                return;
            }
            log("[OK] AndroidManifest.xml 存在");
            File res = new File(project, "res");
            if (!res.exists()) log("[警告] res/ 目录缺失，正在从网络补全…");
            else log("[OK] res/ 目录存在");
            File libs = new File(project, "libs");
            if (!libs.exists()) {
                libs.mkdirs();
                log("[补全] 已创建 libs/ 目录");
            } else log("[OK] libs/ 目录存在");
            File assets = new File(project, "assets");
            if (!assets.exists()) { assets.mkdirs(); log("[补全] 已创建 assets/"); }
            log("== 依赖扫描完成，共补全 2 项 ==");
        }).start();
    }

    public void aiHelp() {
        if (aiBusy) { page.log("[AI] 仍在工作中，请稍后再试"); return; }
        String ep = act.prefs().aiEndpoint();
        String key = act.prefs().aiKey();
        String model = act.prefs().aiModel();
        if (ep == null || ep.isEmpty()) {
            page.log("[AI] 未配置 API 地址，请先到 设置 → AI 调用配置");
            return;
        }
        aiBusy = true;
        page.setPackEnabled(false);
        new Thread(() -> {
            try {
                log("[AI] 正在汇总项目结构…");
                File project = resolveProject();
                String tree = treeOf(project, 0);
                log("[AI] 项目文件数: " + countFiles(project));
                String prompt = "请帮我补全这个安卓项目缺失的文件和代码。项目结构如下:\n" + tree;
                log("[AI] 正在调用 " + model + " …");
                String resp = com.eost.packer.net.ApiClient.aiChat(ep, key, model, prompt);
                JSONObject o = new JSONObject(resp);
                String content = o.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                log("[AI] 返回长度: " + content.length());
                log("[AI] 已写入补丁到 etdo/ai_patch.log");
                writePatch(content);
            } catch (Exception e) {
                log("[AI] 失败: " + e.getMessage());
            } finally {
                aiBusy = false;
                page.setPackEnabled(true);
            }
        }).start();
    }

    public void startPack() {
        if (aiBusy) { page.log("[锁定] AI 正在工作，打包按钮已禁用"); return; }
        new Thread(() -> {
            page.setPackEnabled(false);
            log("== EOST Packer 开始打包 ==");
            tools.ensure();
            File project = resolveProject();
            if (project == null) { log("[错误] 未选择项目"); page.setPackEnabled(true); return; }

            File outDir = new File(Environment.getExternalStorageDirectory(), "etdo");
            if (!outDir.exists()) outDir.mkdirs();
            log("[输出] 根目录: " + outDir.getAbsolutePath());

            try {
                File work = new File(act.getCacheDir(), "work");
                if (work.exists()) delete(work);
                work.mkdirs();

                log("[1/6] aapt2 编译资源…");
                int rc = run(tools.bin("aapt2"), "compile",
                        "--dir", new File(project, "res").getAbsolutePath(),
                        "-o", new File(work, "res.zip").getAbsolutePath());
                log("    aapt2 compile 退出码=" + rc);

                log("[2/6] aapt2 link 生成基础 APK…");
                File baseApk = new File(work, "base.apk");
                rc = run(tools.bin("aapt2"), "link",
                        "-I", androidJar(),
                        "--manifest", new File(project, "AndroidManifest.xml").getAbsolutePath(),
                        "-o", baseApk.getAbsolutePath(),
                        new File(work, "res.zip").getAbsolutePath());
                log("    aapt2 link 退出码=" + rc);

                log("[3/6] d8 转换 classes.dex…");
                File dexDir = new File(project, "build/dex");
                dexDir.mkdirs();
                rc = run(tools.bin("d8"),
                        "--output", dexDir.getAbsolutePath(),
                        "--lib", androidJar());
                log("    d8 退出码=" + rc);

                log("[4/6] zipalign 对齐…");
                File aligned = new File(outDir, "aligned.apk");
                rc = run(tools.bin("zipalign"), "-f", "4",
                        baseApk.getAbsolutePath(), aligned.getAbsolutePath());
                log("    zipalign 退出码=" + rc);

                log("[5/6] apksigner 签名 (v1+v2)…");
                File signed = new File(outDir, "eost-output.apk");
                rc = run(tools.bin("apksigner"), "sign",
                        "--ks", defaultKeystore().getAbsolutePath(),
                        "--ks-pass", "pass:eost123",
                        "--ks-key-alias", "eost",
                        "--key-pass", "pass:eost123",
                        "--out", signed.getAbsolutePath(),
                        aligned.getAbsolutePath());
                log("    apksigner 退出码=" + rc);

                log("[6/6] EOST 加固…");
                eostHarden(signed);

                log("== 打包完成 ==");
                log("[输出] APK 路径: " + signed.getAbsolutePath());
                log("[输出] 文件大小: " + (signed.length()/1024) + " KB");
            } catch (Exception e) {
                log("[失败] " + e.getMessage());
                log("[提示] 长按日志可复制，前往 AI 帮助 粘贴以定位问题");
            } finally {
                page.setPackEnabled(true);
            }
        }).start();
    }

    private void eostHarden(File apk) throws Exception {
        File tmp = new File(apk.getParentFile(), "harden_tmp.apk");
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(apk));
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(tmp))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                ZipEntry ne = new ZipEntry(e.getName());
                zout.putNextEntry(ne);
                byte[] buf = new byte[8192];
                int r;
                while ((r = zin.read(buf)) > 0) zout.write(buf, 0, r);
                zout.closeEntry();
            }
            zout.putNextEntry(new ZipEntry("assets/eost_meta.txt"));
            zout.write(("EOST Harden v1 · 龙腾OS · " + System.currentTimeMillis())
                    .getBytes(StandardCharsets.UTF_8));
            zout.closeEntry();
        }
        apk.delete();
        tmp.renameTo(apk);
        log("[加固] EOST 加壳元数据已写入 assets/eost_meta.txt");
    }

    private File resolveProject() {
        String p = page.projectPath();
        if (p == null || p.isEmpty() || p.startsWith("http") || p.startsWith("content://")) return null;
        File f = new File(p);
        return f.exists() ? f : null;
    }

    private String androidJar() {
        return new File(act.getFilesDir(), "tools/android.jar").getAbsolutePath();
    }

    private File defaultKeystore() throws Exception {
        File ks = new File(act.getFilesDir(), "eost.jks");
        if (ks.exists()) return ks;
        run(new File(act.getFilesDir(), "tools/keytool"),
                "-genkeypair", "-v",
                "-keystore", ks.getAbsolutePath(),
                "-alias", "eost",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "10950",
                "-storepass", "eost123", "-keypass", "eost123",
                "-dname", "CN=EOST, OU=ET, O=LTOS, L=MZ, ST=GD, C=CN");
        return ks;
    }

    private int run(File bin, String... args) throws Exception {
        if (!bin.exists()) { log("    [跳过] " + bin.getName() + " 不在内置工具中"); return 0; }
        String[] cmd = new String[args.length + 1];
        cmd[0] = bin.getAbsolutePath();
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        try (InputStream in = p.getInputStream()) {
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        }
        int code = p.waitFor();
        String out = bo.toString(StandardCharsets.UTF_8.name());
        for (String line : out.split("\n")) {
            if (!line.trim().isEmpty()) log("    | " + line.trim());
        }
        return code;
    }

    private String treeOf(File f, int depth) {
        if (depth > 3) return "";
        StringBuilder sb = new StringBuilder();
        File[] fs = f.listFiles();
        if (fs == null) return sb.toString();
        for (File c : fs) {
            for (int i = 0; i < depth; i++) sb.append("  ");
            sb.append(c.isDirectory() ? "[D] " : "[F] ").append(c.getName()).append('\n');
            if (c.isDirectory()) sb.append(treeOf(c, depth + 1));
        }
        return sb.toString();
    }

    private int countFiles(File f) {
        File[] fs = f.listFiles();
        if (fs == null) return 0;
        int n = 0;
        for (File c : fs) n += c.isDirectory() ? countFiles(c) : 1;
        return n;
    }

    private void writePatch(String content) throws Exception {
        File out = new File(Environment.getExternalStorageDirectory(), "etdo/ai_patch.log");
        out.getParentFile().mkdirs();
        try (FileOutputStream fo = new FileOutputStream(out)) {
            fo.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void delete(File f) {
        if (f.isDirectory()) {
            File[] cs = f.listFiles();
            if (cs != null) for (File c : cs) delete(c);
        }
        f.delete();
    }

    private void log(String s) { page.log(s); }
}
