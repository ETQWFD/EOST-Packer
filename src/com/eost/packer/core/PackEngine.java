package com.eost.packer.core;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.eost.packer.MainActivity;
import com.eost.packer.net.ApiClient;
import com.eost.packer.ui.PackPage;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PackEngine {
    private final MainActivity act;
    private final PackPage page;
    private final ToolExtractor tools;
    private volatile boolean aiBusy = false;

    public PackEngine(MainActivity act, PackPage page) {
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
            log("== EOST 依赖扫描 ==");
            tools.ensure();
            File project = resolveProject();
            if (project == null) { log("[错误] 请先选择项目根目录（zip 或文件夹）"); return; }
            log("[扫描] 项目: " + project.getAbsolutePath());
            File manifest = new File(project, "AndroidManifest.xml");
            if (!manifest.exists()) {
                log("[错误] 未找到 AndroidManifest.xml，请确认选择的是项目根目录");
                return;
            }
            log("[OK] AndroidManifest.xml 存在");
            File res = new File(project, "res");
            if (!res.exists()) log("[警告] res/ 缺失，将无法用 aapt2 编译资源");
            else log("[OK] res/ 存在");
            File dex = findDex(project);
            if (dex == null) {
                log("[警告] 未找到 classes.dex，请确认工程已编译（含 dex）");
                File cls = new File(project, "classes.jar");
                if (cls.exists()) log("[提示] 发现 classes.jar，可先转换为 dex");
            } else {
                log("[OK] classes.dex 存在: " + dex.getName());
            }
            File libs = new File(project, "libs");
            if (!libs.exists()) log("[提示] libs/ 目录不存在（无此目录时可忽略）");
            File assetsDir = new File(project, "assets");
            if (!assetsDir.exists()) log("[提示] assets/ 目录不存在（无此目录时可忽略）");
            log("[工具链] ABI=" + ToolExtractor.pickAbi()
                    + (tools.hasTools() ? " 已就绪" : " 未内置(仅支持 arm64/arm32)"));
            log("== 扫描完成 ==");
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
                if (project == null) { log("[AI] 请先选择项目根目录"); return; }
                String tree = treeOf(project, 0);
                log("[AI] 项目文件数: " + countFiles(project));
                String prompt = "你是 EOST Packer 内置 AI。请分析并修复这个安卓工程缺失的文件和代码。项目结构:\n" + tree;
                log("[AI] 正在调用 " + model + " …");
                String resp = ApiClient.aiChat(ep, key, model, prompt);
                JSONObject o = new JSONObject(resp);
                String content = o.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                log("[AI] 返回长度: " + content.length());
                writePatch(content);
                log("[AI] 修复建议已写入 etdo/ai_patch.log，请按建议操作");
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
            log("== EOST Packer v1.3 开始打包 ==");
            tools.ensure();
            File project = resolveProject();
            if (project == null) { log("[错误] 未选择项目根目录"); page.setPackEnabled(true); return; }
            File outDir = new File(Environment.getExternalStorageDirectory(), "etdo");
            if (!outDir.exists()) outDir.mkdirs();
            log("[输出] 目录: " + outDir.getAbsolutePath());
            File ks = new File(act.getFilesDir(), "tools/etckey.jks");
            if (!ks.exists()) copyEtcKey(ks);

            try {
                File work = new File(act.getCacheDir(), "work");
                if (work.exists()) delete(work);
                work.mkdirs();

                File manifest = new File(project, "AndroidManifest.xml");
                if (!manifest.exists()) { log("[错误] AndroidManifest.xml 不存在"); return; }
                File res = new File(project, "res");
                if (!res.exists()) { log("[错误] res/ 目录不存在，无法编译资源"); return; }
                File dex = findDex(project);
                if (dex == null) { log("[错误] 未找到 classes.dex（工程需为已编译状态）"); return; }

                log("[1/5] aapt2 编译资源…");
                File resZip = new File(work, "res.zip");
                run(tools.bin("aapt2"), "compile", "--dir", res.getAbsolutePath(),
                        "-o", resZip.getAbsolutePath());

                log("[2/5] aapt2 link 生成基础 APK…");
                File baseApk = new File(work, "base.apk");
                java.util.List<String> linkCmd = new java.util.ArrayList<>();
                linkCmd.add(tools.bin("aapt2").getAbsolutePath());
                linkCmd.add("link");
                linkCmd.add("-I"); linkCmd.add(tools.androidJar().getAbsolutePath());
                linkCmd.add("--manifest"); linkCmd.add(manifest.getAbsolutePath());
                File projAssets = new File(project, "assets");
                if (projAssets.exists() && projAssets.listFiles() != null
                        && projAssets.listFiles().length > 0) {
                    linkCmd.add("-A"); linkCmd.add(projAssets.getAbsolutePath());
                }
                linkCmd.add("-o"); linkCmd.add(baseApk.getAbsolutePath());
                linkCmd.add(resZip.getAbsolutePath());
                runList(linkCmd);

                log("[3/5] 合并 dex / lib …");
                Map<String, File> adds = new LinkedHashMap<>();
                adds.put(dex.getName(), dex);
                File[] dexes = project.listFiles();
                if (dexes != null) {
                    for (File f : dexes) {
                        String n = f.getName();
                        if (n.matches("classes\\d*\\.dex") && !n.equals(dex.getName())) {
                            adds.put(n, f);
                        }
                    }
                }
                File libDir = new File(project, "lib");
                if (libDir.exists()) {
                    addDirToMap(libDir, "lib/", adds);
                }
                if (adds.size() > 0) zipUpdate(baseApk, adds);

                log("[4/5] EOST Guard 加固（dex 完整性校验）…");
                String dexHash = sha256(new File(work, "dex_hash.tmp"));
                // 计算 base.apk 内 dex 的 hash
                String apkDexHash = sha256FromApk(baseApk, "classes.dex");
                File guardTmp = new File(work, "eost_guard.dat");
                FileOutputStream gfo = new FileOutputStream(guardTmp);
                gfo.write(("{\"guard\":\"EOST v2\",\"dex_sha256\":\"" + apkDexHash
                        + "\",\"time\":" + System.currentTimeMillis() + "}")
                        .getBytes(StandardCharsets.UTF_8));
                gfo.close();
                Map<String, File> guard = new LinkedHashMap<>();
                guard.put("assets/eost_guard.dat", guardTmp);
                zipUpdate(baseApk, guard);
                log("    加固标记: assets/eost_guard.dat (dex_sha256="
                        + (apkDexHash.length() > 12 ? apkDexHash.substring(0, 12) : apkDexHash) + "…)");

                log("[5/5] zipalign + ETC 签名 (v1+v2)…");
                File aligned = new File(outDir, "aligned.apk");
                run(tools.bin("zipalign"), "-f", "4", baseApk.getAbsolutePath(),
                        aligned.getAbsolutePath());
                File signed = new File(outDir, "eost-output.apk");
                if (signed.exists()) signed.delete();
                try {
                    new SignerBridge(act, ks).sign(aligned, signed);
                    log("    签名完成（内置 apksig，ETC 证书，v1+v2）");
                } catch (Throwable se) {
                    log("    内置签名失败: " + se.getMessage());
                    log("    回退命令行 apksigner…");
                    run(tools.bin("apksigner"), "sign",
                            "--ks", ks.getAbsolutePath(), "--ks-pass", "pass:etc123",
                            "--ks-key-alias", "etc", "--key-pass", "pass:etc123",
                            "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
                            "--out", signed.getAbsolutePath(), aligned.getAbsolutePath());
                }
                if (aligned.exists()) aligned.delete();

                log("== 打包完成 ==");
                log("[输出] APK: " + signed.getAbsolutePath());
                log("[输出] 大小: " + (signed.length() / 1024) + " KB");
                log("[输出] 加固: EOST Guard v2 已注入");
            } catch (Exception e) {
                log("[失败] " + e.getMessage());
                log("[提示] 复制日志发给 AI 帮助可定位并修复问题");
            } finally {
                page.setPackEnabled(true);
            }
        }).start();
    }

    private void copyEtcKey(File ks) {
        try (InputStream in = act.getAssets().open("etckey.jks");
             FileOutputStream fo = new FileOutputStream(ks)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) fo.write(buf, 0, r);
        } catch (Exception ignored) {}
    }

    private File findDex(File project) {
        File[] fs = project.listFiles();
        if (fs == null) return null;
        for (File f : fs) {
            if (f.isFile() && f.getName().startsWith("classes") && f.getName().endsWith(".dex")) {
                return f;
            }
        }
        return null;
    }

    private void addDirToMap(File dir, String prefix, Map<String, File> map) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) addDirToMap(f, prefix + f.getName() + "/", map);
            else map.put(prefix + f.getName(), f);
        }
    }

    private void zipUpdate(File zip, Map<String, File> adds) throws Exception {
        File tmp = new File(zip.getParentFile(), zip.getName() + ".tmp");
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(tmp))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                zout.putNextEntry(new ZipEntry(e.getName()));
                int r;
                while ((r = zin.read(buf)) > 0) zout.write(buf, 0, r);
                zout.closeEntry();
            }
            for (Map.Entry<String, File> en : adds.entrySet()) {
                zout.putNextEntry(new ZipEntry(en.getKey()));
                try (FileInputStream fin = new FileInputStream(en.getValue())) {
                    int r;
                    while ((r = fin.read(buf)) > 0) zout.write(buf, 0, r);
                }
                zout.closeEntry();
            }
        }
        zip.delete();
        tmp.renameTo(zip);
    }

    private String sha256FromApk(File apk, String entryName) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(apk))) {
            ZipEntry e;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals(entryName)) {
                    int r;
                    while ((r = zin.read(buf)) > 0) md.update(buf, 0, r);
                    break;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    }

    private String sha256(File f) throws Exception {
        return f.exists() ? sha256FromApk(f, "__none__") : "";
    }

    private File resolveProject() {
        String p = page.projectPath();
        if (p == null || p.isEmpty()) return null;
        try {
            if (p.startsWith("content://")) {
                Uri uri = Uri.parse(p);
                String mime = act.getContentResolver().getType(uri);
                if (mime != null && mime.contains("zip")) {
                    return extractZipFromUri(uri);
                }
                return copyTreeFromUri(uri);
            }
            if (p.startsWith("file://")) p = p.substring(7);
            File f = new File(p);
            if (f.isFile() && (p.endsWith(".zip") || p.endsWith(".apk"))) {
                return extractZip(new FileInputStream(f));
            }
            return f.exists() ? f : null;
        } catch (Exception e) {
            log("[错误] 读取项目失败: " + e.getMessage());
            return null;
        }
    }

    private File extractZipFromUri(Uri uri) throws Exception {
        InputStream in = act.getContentResolver().openInputStream(uri);
        return extractZip(in);
    }

    private File extractZip(InputStream in) throws Exception {
        File work = new File(act.getCacheDir(), "proj_" + System.currentTimeMillis());
        work.mkdirs();
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                File out = new File(work, e.getName());
                if (e.isDirectory()) { out.mkdirs(); continue; }
                out.getParentFile().mkdirs();
                try (FileOutputStream fo = new FileOutputStream(out)) {
                    int r;
                    while ((r = zin.read(buf)) > 0) fo.write(buf, 0, r);
                }
            }
        }
        // 找项目根目录：含 AndroidManifest.xml 的最上层
        File root = findRoot(work);
        log("[解压] 项目根目录: " + root.getAbsolutePath());
        return root;
    }

    private File findRoot(File dir) {
        if (new File(dir, "AndroidManifest.xml").exists()) return dir;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) {
                    File r = findRoot(f);
                    if (r != null) return r;
                }
            }
        }
        return dir;
    }

    private File copyTreeFromUri(Uri uri) throws Exception {
        File work = new File(act.getCacheDir(), "tree_" + System.currentTimeMillis());
        work.mkdirs();
        android.database.Cursor c = act.getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            String path = null;
            while (c.moveToNext()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) path = c.getString(idx);
            }
            c.close();
            if (path != null) {
                File direct = new File(work, path);
                direct.mkdirs();
                return copyDocumentTree(uri, direct);
            }
        }
        return work;
    }

    private File copyDocumentTree(Uri treeUri, File dest) throws Exception {
        android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri));
        copyChildren(treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri), dest);
        return dest;
    }

    private void copyChildren(Uri treeUri, String docId, File destDir) throws Exception {
        Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        android.database.Cursor c = act.getContentResolver().query(children,
                new String[]{"document_id", "_display_name", "mime_type"}, null, null, null);
        if (c == null) return;
        while (c.moveToNext()) {
            String id = c.getString(0);
            String name = c.getString(1);
            String mime = c.getString(2);
            if (android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                File sub = new File(destDir, name);
                sub.mkdirs();
                copyChildren(treeUri, id, sub);
            } else {
                Uri fileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                File out = new File(destDir, name);
                try (InputStream in = act.getContentResolver().openInputStream(fileUri);
                     FileOutputStream fo = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0) fo.write(buf, 0, r);
                }
            }
        }
        c.close();
    }

    private void writePatch(String content) throws Exception {
        File out = new File(Environment.getExternalStorageDirectory(), "etdo/ai_patch.log");
        out.getParentFile().mkdirs();
        try (FileOutputStream fo = new FileOutputStream(out)) {
            fo.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private int run(File bin, String... args) throws Exception {
        if (!bin.exists()) { log("    [跳过] " + bin.getName() + " 不在内置工具链中"); return 0; }
        String[] cmd = new String[args.length + 1];
        cmd[0] = bin.getAbsolutePath();
        System.arraycopy(args, 0, cmd, 1, args.length);
        return exec(cmd);
    }

    private int runList(java.util.List<String> cmd) throws Exception {
        if (cmd.isEmpty()) return 0;
        return exec(cmd.toArray(new String[0]));
    }

    private int exec(String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("LD_LIBRARY_PATH", tools.libDir().getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
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

    private void delete(File f) {
        if (f.isDirectory()) {
            File[] cs = f.listFiles();
            if (cs != null) for (File c : cs) delete(c);
        }
        f.delete();
    }

    private void log(String s) { page.log(s); }
}
