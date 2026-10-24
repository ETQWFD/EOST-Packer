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

    public static String checkUpdate() throws Exception {
        String s = httpGet(BASE + "api/latest.json");
        JSONObject o = new JSONObject(s);
        return o.optString("version") + "|" + o.optString("url");
    }

    public static String aiChat(String endpoint, String key, String model, String prompt) throws Exception {
        URL u = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + key);
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
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
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
