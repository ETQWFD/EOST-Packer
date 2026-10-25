package com.eost.packer.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ApiClient {
    public static final String BASE = "https://etqwfd.github.io/EOST-Packer/";

    public static List<Announcement> fetchAnnouncements() throws Exception {
        List<Announcement> list = new ArrayList<>();
        String s = httpGet(BASE + "api/announcements.json");
        JSONArray arr = new JSONArray(s);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Announcement a = new Announcement();
            a.id = o.optInt("id");
            a.title = o.optString("title");
            a.body = o.optString("body");
            a.version = o.optString("version");
            a.date = o.optString("date");
            list.add(a);
        }
        return list;
    }

    /** 返回 [远程版本, 下载URL]；远程版本与当前相同时返回 null */
    public static String[] checkUpdate(String currentVersion) throws Exception {
        String s = httpGet(BASE + "api/latest.json");
        JSONObject o = new JSONObject(s);
        String remote = o.optString("version");
        String url = o.optString("url");
        if (remote == null || remote.isEmpty()) return null;
        if (remote.equalsIgnoreCase(currentVersion)) return null;
        return new String[]{remote, url};
    }

    public static String aiChat(String endpoint, String key, String model, String prompt) throws Exception {
        URL u = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setRequestProperty("User-Agent", "EOST-Packer/1.3");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray ms = new JSONArray();
        JSONObject m = new JSONObject();
        m.put("role", "user");
        m.put("content", prompt);
        ms.put(m);
        body.put("messages", ms);
        conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        conn.connect();
        return readAll(conn.getInputStream());
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "EOST-Packer/1.3");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        return readAll(c.getInputStream());
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }
}
