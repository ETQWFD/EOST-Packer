package com.eost.packer.core;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 解析 .deb（ar 归档）并解包 data.tar.xz 到目标目录（Termux 包结构） */
public class DebExtractor {

    public static boolean extract(File deb, File dest) {
        try (InputStream in = new FileInputStream(deb)) {
            byte[] magic = new byte[8];
            if (readFully(in, magic) < 8) return false;
            if (!new String(magic, "US-ASCII").equals("!<arch>\n")) return false;

            byte[] dataTar = null;
            while (true) {
                byte[] hdr = new byte[60];
                int hr = readFully(in, hdr);
                if (hr < 60) break;
                String name = new String(hdr, 0, 16, "US-ASCII").trim();
                long size = 0;
                try { size = Long.parseLong(new String(hdr, 48, 10, "US-ASCII").trim()); } catch (Exception ignored) {}
                // 读内容
                byte[] content = new byte[(int) size];
                int cr = readFully(in, content);
                if (cr < content.length) break;
                if (name.equals("data.tar.xz")) {
                    dataTar = content;
                    break;
                }
                // 偶数对齐
                if (size % 2 != 0) { byte[] pad = new byte[1]; readFully(in, pad); }
            }
            if (dataTar == null) return false;

            dest.mkdirs();
            return extractTarXz(new ByteArrayInputStream(dataTar), dest);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean extractTarXz(InputStream in, File dest) {
        try (XZInputStream xz = new XZInputStream(in);
             TarArchiveInputStream tar = new TarArchiveInputStream(xz)) {
            TarArchiveEntry e;
            byte[] buf = new byte[8192];
            while ((e = tar.getNextTarEntry()) != null) {
                String name = e.getName();
                if (name == null || name.isEmpty()) continue;
                // Termux 包内路径为 ./data/... 去掉前缀
                String rel = name;
                if (rel.startsWith("./")) rel = rel.substring(2);
                File out = new File(dest, rel);
                if (e.isDirectory()) { out.mkdirs(); continue; }
                if (e.isSymbolicLink()) { continue; }
                if (!e.isFile()) continue;
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream fo = new FileOutputStream(out)) {
                    int r;
                    while ((r = tar.read(buf)) > 0) fo.write(buf, 0, r);
                }
            }
            // 标记已安装
            File marker = new File(dest, ".installed");
            marker.getParentFile().mkdirs();
            try (FileOutputStream mo = new FileOutputStream(marker)) {
                mo.write("ok".getBytes("UTF-8"));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) break;
            off += r;
        }
        return off;
    }
}
