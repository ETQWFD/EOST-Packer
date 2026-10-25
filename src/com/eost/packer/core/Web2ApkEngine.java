package com.eost.packer.core;

import android.app.Activity;
import android.net.Uri;
import com.eost.packer.ui.WebPage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * HTML → APK 转换：把含 index.html 的网页包成 WebView 壳 APK。
 * 模板（AndroidManifest + res + WebViewActivity dex）从 assets/template 复制，
 * 用户网页放到 assets/www/，调用 aapt2 / d8 / apksigner 完成打包。
 */
public class Web2ApkEngine {
    private final Activity act;
    private final WebPage page;
    private final File work;

    public Web2ApkEngine(Activity act, WebPage page) {
        this.act = act;
        this.page = page;
        this.work = new File(act.getCacheDir(), "web2apk");
    }

    public void start() {
        String path = ((com.eost.packer.MainActivity) act).prefs().webPath();
        if (path == null || path.isEmpty()) { page.log("[错误] 请先选择含 index.html 的 zip 或文件夹"); return; }
        new Thread(() -> {
            try {
                page.log("== Web2APK 开始 ==");
                if (work.exists()) delete(work);
                work.mkdirs();

                // 1. 解压用户网页
                page.log("[1/5] 解压网页包…");
                File wwwDir = new File(work, "assets/www");
                wwwDir.mkdirs();
                File src = extractTo(path, work);
                if (src == null) { page.log("[错误] 无法读取所选目录/压缩包"); return; }
                copyAll(src, wwwDir);
                File idx = new File(wwwDir, "index.html");
                if (!idx.exists()) {
                    // 可能根目录本身有 index.html
                    File tryIdx = new File(src, "index.html");
                    if (tryIdx.exists()) copyFile(tryIdx, idx);
                }
                if (!idx.exists()) { page.log(act.getString(com.eost.packer.R.string.web_no_index)); return; }
                page.log("[OK] index.html 已就绪");

                // 2. 复制模板
                page.log("[2/5] 写入模板 (Manifest / res / WebView)…");
                copyAssetDir("template", work);

                // 3. 工具链
                ToolExtractor te = new ToolExtractor(act);
                te.ensure();
                if (!te.hasTools()) { page.log("[错误] 当前设备架构未内置工具链"); return; }

                // 4. aapt2 compile+link
                page.log("[3/5] aapt2 编译资源…");
                File resDir = new File(work, "res");
                File resZip = new File(work, "res.zip");
                run(te.bin("aapt2").getAbsolutePath(), "compile", "--dir", resDir.getAbsolutePath(), "-o", resZip.getAbsolutePath());
                File base = new File(work, "base.apk");
                run(te.bin("aapt2").getAbsolutePath(), "link",
                        "-I", te.androidJar().getAbsolutePath(),
                        "--manifest", new File(work, "AndroidManifest.xml").getAbsolutePath(),
                        "-A", new File(work, "assets").getAbsolutePath(),
                        "-o", base.getAbsolutePath(),
                        resZip.getAbsolutePath());

                // 5. 加入 webview.dex → zipalign → 签名
                page.log("[4/5] 合并 WebView dex…");
                File webDex = new File(work, "classes.dex");
                copyAsset("template/classes.dex", webDex);
                new ProcessBuilder(zipCmd(), "-u", base.getAbsolutePath(), "classes.dex")
                        .directory(work).start().waitFor();

                page.log("[5/5] 对齐并签名…");
                File aligned = new File(work, "aligned.apk");
                run(te.bin("zipalign").getAbsolutePath(), "-f", "4", base.getAbsolutePath(), aligned.getAbsolutePath());

                File out = new File(android.os.Environment.getExternalStorageDirectory(),
                        "etdo/web-output.apk");
                out.getParentFile().mkdirs();
                File ks = new File(act.getFilesDir(), "tools/etckey.jks");
                if (!ks.exists()) extractAssetTo("etckey.jks", ks);
                run(te.bin("apksigner").getAbsolutePath(), "sign",
                        "--ks", ks.getAbsolutePath(), "--ks-pass", "pass:etc123",
                        "--ks-key-alias", "etc", "--key-pass", "pass:etc123",
                        "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
                        "--out", out.getAbsolutePath(), aligned.getAbsolutePath());
                page.log("== 完成: " + out.getAbsolutePath() + " ==");
            } catch (Exception e) {
                page.log("[错误] " + e.getMessage());
            }
        }).start();
    }

    private File extractTo(String uri, File dest) throws Exception {
        if (uri.contains("/tree/")) {
            File out = new File(dest, "src");
            out.mkdirs();
            copyTree(Uri.parse(uri), out);
            return out;
        } else {
            File tmpZip = new File(dest, "in.zip");
            try (InputStream in = act.getContentResolver().openInputStream(Uri.parse(uri));
                 FileOutputStream fo = new FileOutputStream(tmpZip)) {
                byte[] b = new byte[8192]; int r; while ((r = in.read(b)) > 0) fo.write(b, 0, r);
            }
            File out = new File(dest, "src");
            out.mkdirs();
            unzip(tmpZip, out);
            return out;
        }
    }

    private void unzip(File zip, File out) throws Exception {
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zip))) {
            java.util.zip.ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                File f = new File(out, e.getName());
                if (e.isDirectory()) { f.mkdirs(); continue; }
                f.getParentFile().mkdirs();
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    int r; while ((r = zin.read(buf)) > 0) fo.write(buf, 0, r);
                }
            }
        }
    }

    private void copyTree(Uri tree, File out) throws Exception {
        android.net.Uri doc = android.provider.DocumentsContract.getTreeDocumentId(tree) != null
                ? android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
                        android.provider.DocumentsContract.getTreeDocumentId(tree)) : tree;
        String childDocId = android.provider.DocumentsContract.getDocumentId(doc);
        recurseDocs(doc, childDocId, out);
    }

    private void recurseDocs(Uri parent, String docId, File target) throws Exception {
        Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(parent, docId);
        android.database.Cursor c = act.getContentResolver().query(children,
                new String[]{android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (c == null) return;
        try {
            int idI = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameI = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeI = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (c.moveToNext()) {
                String cid = c.getString(idI);
                String name = c.getString(nameI);
                String mime = c.getString(mimeI);
                File f = new File(target, name);
                if (android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    f.mkdirs();
                    recurseDocs(parent, cid, f);
                } else {
                    f.getParentFile().mkdirs();
                    Uri fUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(parent, cid);
                    try (InputStream in = act.getContentResolver().openInputStream(fUri);
                         OutputStream fo = new FileOutputStream(f)) {
                        byte[] b = new byte[8192]; int r; while ((r = in.read(b)) > 0) fo.write(b, 0, r);
                    }
                }
            }
        } finally { c.close(); }
    }

    private void copyAssetDir(String asset, File dest) throws Exception {
        String[] list = act.getAssets().list(asset);
        if (list == null || list.length == 0) {
            File out = new File(dest, asset);
            out.getParentFile().mkdirs();
            copyAsset(asset, out);
            return;
        }
        File dir = new File(dest, asset);
        dir.mkdirs();
        for (String n : list) copyAssetDir(asset + "/" + n, dir);
    }

    private void copyAsset(String path, File out) throws Exception {
        out.getParentFile().mkdirs();
        try (InputStream in = act.getAssets().open(path);
             OutputStream fo = new FileOutputStream(out)) {
            byte[] b = new byte[8192]; int r; while ((r = in.read(b)) > 0) fo.write(b, 0, r);
        }
    }
    private void extractAssetTo(String asset, File out) throws Exception { copyAsset(asset, out); }

    private void copyAll(File src, File dst) {
        if (src == null || !src.exists()) return;
        if (src.isFile()) { copyFile(src, new File(dst, src.getName())); return; }
        File[] fs = src.listFiles();
        if (fs == null) return;
        for (File f : fs) copyAll(f, new File(dst, f.getName()));
    }
    private void copyFile(File src, File dst) {
        try {
            dst.getParentFile().mkdirs();
            try (InputStream in = new java.io.FileInputStream(src);
                 OutputStream fo = new FileOutputStream(dst)) {
                byte[] b = new byte[8192]; int r; while ((r = in.read(b)) > 0) fo.write(b, 0, r);
            }
        } catch (Exception ignored) {}
    }

    private String zipCmd() {
        return "zip";
    }
    private void run(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        byte[] buf = new byte[4096]; java.io.InputStream is = p.getInputStream();
        StringBuilder sb = new StringBuilder(); int r;
        while ((r = is.read(buf)) > 0) sb.append(new String(buf, 0, r));
        int code = p.waitFor();
        if (code != 0) throw new RuntimeException("命令失败(" + code + "): " + sb.substring(0, Math.min(200, sb.length())));
    }
    private void delete(File f) {
        if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) delete(x); }
        f.delete();
    }
}
