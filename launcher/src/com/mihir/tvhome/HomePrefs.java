package com.mihir.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Choices made on the TV (dock order, hidden apps, rows), over the defaults in config.xml. */
final class HomePrefs {
    private final SharedPreferences p;
    private final Context c;

    HomePrefs(Context c) {
        this.c = c;
        p = c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    List<String> dock() {
        String s = p.getString("dock", null);
        if (s == null) return new ArrayList<>(Arrays.asList(c.getResources().getStringArray(R.array.dock_apps)));
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    void setDock(List<String> pkgs) { p.edit().putString("dock", TextUtils.join(",", pkgs)).apply(); }

    void resetDock() { p.edit().remove("dock").apply(); }

    /** Apps hidden from the home screen with a long press (config.xml's hidden apps are separate). */
    Set<String> hiddenApps() { return new HashSet<>(p.getStringSet("hidden_apps", Collections.emptySet())); }

    void setAppHidden(String pkg, boolean hide) {
        Set<String> s = hiddenApps();
        if (hide) s.add(pkg); else s.remove(pkg);
        p.edit().putStringSet("hidden_apps", s).apply();
    }

    /** Continue watching entries removed from the row; they come back once the app replaces them. */
    Set<String> hiddenPrograms() { return new HashSet<>(p.getStringSet("hidden_programs", Collections.emptySet())); }

    void hideProgram(String key) {
        Set<String> s = hiddenPrograms();
        s.add(key);
        p.edit().putStringSet("hidden_programs", s).apply();
    }

    /** Forget removed entries the app no longer lists, so the set doesn't grow forever. */
    void keepHiddenPrograms(Collection<String> present) {
        Set<String> s = hiddenPrograms();
        if (s.retainAll(present)) p.edit().putStringSet("hidden_programs", s).apply();
    }

    boolean flag(String key, boolean def) { return p.getBoolean(key, def); }

    void setFlag(String key, boolean on) { p.edit().putBoolean(key, on).apply(); }

    /** Catalog rows switched on, as "type/id" keys; the config.xml rows until changed on the TV. */
    Set<String> catalogs(Collection<String> defaults) {
        Set<String> s = p.getStringSet("catalogs", null);
        return new HashSet<>(s != null ? s : defaults);
    }

    void setCatalogs(Set<String> keys) { p.edit().putStringSet("catalogs", new HashSet<>(keys)).apply(); }
}
