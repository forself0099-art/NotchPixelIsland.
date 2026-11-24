package com.notchpixel.island;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Sites, apps aur aakhri tab ki saving (SharedPreferences, koi library nahi). */
final class Store {

    static final class Site {
        final String name, url;
        Site(String name, String url) { this.name = name; this.url = url; }
        String letter() { return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(); }
        String host() {
            String h = url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
            int i = h.indexOf('/');
            return i > 0 ? h.substring(0, i) : h;
        }
    }

    private static final String K_TAB = "tab", K_SITES = "sites", K_APPS = "apps";
    private final SharedPreferences sp;

    Store(Context c) { sp = c.getSharedPreferences("island", Context.MODE_PRIVATE); }

    boolean edgeOn() { return sp.getBoolean("edge", true); }
    void setEdge(boolean on) { sp.edit().putBoolean("edge", on).apply(); }

    boolean notesOn() { return sp.getBoolean("notes", true); }
    void setNotes(boolean on) { sp.edit().putBoolean("notes", on).apply(); }

    int getTab() { return Math.max(0, Math.min(3, sp.getInt(K_TAB, 1))); }
    void setTab(int t) { sp.edit().putInt(K_TAB, t).apply(); }

    List<Site> sites() {
        List<Site> out = new ArrayList<>();
        String raw = sp.getString(K_SITES, null);
        if (raw == null) {
            out.add(new Site("Google", "https://www.google.com"));
            out.add(new Site("YouTube", "https://www.youtube.com"));
            out.add(new Site("Gmail", "https://mail.google.com"));
            out.add(new Site("WhatsApp", "https://web.whatsapp.com"));
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Site(o.getString("n"), o.getString("u")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    void saveSites(List<Site> list) {
        try {
            JSONArray a = new JSONArray();
            for (Site s : list) a.put(new JSONObject().put("n", s.name).put("u", s.url));
            sp.edit().putString(K_SITES, a.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Chune hue apps ke package names. */
    List<String> apps() {
        List<String> out = new ArrayList<>();
        String raw = sp.getString(K_APPS, null);
        if (raw == null) {
            // pehli baar: aam apps (jo install hon wahi dikhengi)
            for (String p : new String[]{"com.android.chrome", "com.whatsapp",
                    "com.google.android.youtube", "com.google.android.apps.photos",
                    "com.google.android.apps.maps", "com.android.settings",
                    "com.google.android.gm", "com.google.android.dialer"}) out.add(p);
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    void saveApps(List<String> pkgs) {
        sp.edit().putString(K_APPS, new JSONArray(pkgs).toString()).apply();
    }
}
