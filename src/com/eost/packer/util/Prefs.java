package com.eost.packer.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = "eost";
    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String get(String k, String def) { return sp.getString(k, def); }
    public void set(String k, String v) { sp.edit().putString(k, v).apply(); }
    public boolean getBool(String k, boolean def) { return sp.getBoolean(k, def); }
    public void setBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    public int getInt(String k, int def) { return sp.getInt(k, def); }
    public void setInt(String k, int v) { sp.edit().putInt(k, v).apply(); }

    public String aiEndpoint() { return get("ai_endpoint", ""); }
    public String aiKey() { return get("ai_key", ""); }
    public String aiModel() { return get("ai_model", "gpt-4o-mini"); }

    public int themeMode() { return getInt("theme_mode", 2); } // 0 light 1 dark 2 auto
    public void setThemeMode(int m) { setInt("theme_mode", m); }

    public int accent() { return getInt("accent", 0xFF1F6FEB); }
    public void setAccent(int c) { setInt("accent", c); }

    public String lang() { return get("lang", "zh"); }
    public void setLang(String l) { set("lang", l); }

    public int signMode() { return getInt("sign_mode", 2); } // 1 v1, 2 v2, 3 both
    public void setSignMode(int m) { setInt("sign_mode", m); }

    public String signPath() { return get("sign_path", ""); }
    public void setSignPath(String p) { set("sign_path", p); }

    public String projectPath() { return get("project_path", ""); }
    public void setProjectPath(String p) { set("project_path", p); }

    public String bgPath() { return get("bg_path", ""); }
    public void setBgPath(String p) { set("bg_path", p); }

    public String signStorePass() { return get("sign_store_pass", ""); }
    public void setSignStorePass(String p) { set("sign_store_pass", p); }
    public String signKeyPass() { return get("sign_key_pass", ""); }
    public void setSignKeyPass(String p) { set("sign_key_pass", p); }
    public String signAlias() { return get("sign_alias", ""); }
    public void setSignAlias(String p) { set("sign_alias", p); }
}
