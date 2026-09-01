package com.dcp.punch.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the island is allowed to show.
 *
 * Two lists you build up rather than two lists you prune. Add the functions you
 * want and the apps you want, and those are what reaches the cutout.
 *
 * EMPTY MEANS EVERYTHING, and that matters. A fresh install with two empty
 * allow-lists would be an island that never appears, and the user would
 * reasonably conclude it was broken. So an empty list is "no restriction" and
 * the control panel says exactly that; the moment the first entry is added, the
 * list becomes a filter. This is the only sane reading of an additive picker
 * that starts out empty.
 *
 * Apps are stored as package names. The picker offers everything with a launcher
 * icon, via a <queries> declaration in the manifest — not QUERY_ALL_PACKAGES,
 * which this app still does not request and does not need.
 */
public final class Sources {

    /** Everything the island can be told about. */
    public enum Source {
        MESSAGES("messages"),
        MEDIA("media"),
        CALLS("calls"),
        TIMERS("timers"),
        NAVIGATION("navigation"),
        PROGRESS("progress"),
        OTHER("other"),
        CHARGING("charging"),
        BATTERY("battery"),
        RINGER("ringer"),
        HEADPHONES("headphones");

        public final String key;
        Source(String key) { this.key = key; }

        public static Source byKey(String k) {
            for (Source s : values()) if (s.key.equals(k)) return s;
            return null;
        }

        /** True for the four that need no permission whatsoever. */
        public boolean isSystemFunction() {
            return this == CHARGING || this == BATTERY || this == RINGER || this == HEADPHONES;
        }
    }

    private static final String FILE = "dcp_sources";
    private static final String K_FUNCTIONS = "allowed_functions";
    private static final String K_APPS = "allowed_apps";
    private static final String K_SEEN = "seen_packages";

    private static final int MAX_SEEN = 200;

    private static Sources instance;

    private final SharedPreferences sp;

    private Sources(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Sources get(Context c) {
        if (instance == null) instance = new Sources(c);
        return instance;
    }

    /* ── Functions ───────────────────────────────────────────────────── */

    public Set<String> allowedFunctionKeys() {
        return unmodifiable(sp.getStringSet(K_FUNCTIONS, Collections.emptySet()));
    }

    /** True when nothing has been picked, so everything is allowed. */
    public boolean functionsUnrestricted() { return allowedFunctionKeys().isEmpty(); }

    public boolean isEnabled(Source s) {
        Set<String> allowed = allowedFunctionKeys();
        return allowed.isEmpty() || allowed.contains(s.key);
    }

    public void addFunction(Source s) {
        Set<String> next = new LinkedHashSet<>(allowedFunctionKeys());
        next.add(s.key);
        sp.edit().putStringSet(K_FUNCTIONS, next).apply();
    }

    public void removeFunction(Source s) {
        Set<String> next = new LinkedHashSet<>(allowedFunctionKeys());
        next.remove(s.key);
        sp.edit().putStringSet(K_FUNCTIONS, next).apply();
    }

    /* ── Apps ────────────────────────────────────────────────────────── */

    public Set<String> allowedApps() {
        return unmodifiable(sp.getStringSet(K_APPS, Collections.emptySet()));
    }

    public boolean appsUnrestricted() { return allowedApps().isEmpty(); }

    public boolean isAppAllowed(String pkg) {
        if (pkg == null) return true;
        Set<String> allowed = allowedApps();
        return allowed.isEmpty() || allowed.contains(pkg);
    }

    public void addApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        Set<String> next = new LinkedHashSet<>(allowedApps());
        next.add(pkg);
        sp.edit().putStringSet(K_APPS, next).apply();
    }

    public void removeApp(String pkg) {
        Set<String> next = new LinkedHashSet<>(allowedApps());
        next.remove(pkg);
        sp.edit().putStringSet(K_APPS, next).apply();
    }

    /* ── Learned packages ────────────────────────────────────────────── */

    /**
     * Note that this package posted something. No longer needed for the picker,
     * which lists installed apps directly, but it is what lets the panel show
     * "heard from" next to an app the user has actually received something from.
     */
    public void remember(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        Set<String> seen = sp.getStringSet(K_SEEN, Collections.emptySet());
        if (seen.contains(pkg)) return;
        Set<String> next = new TreeSet<>(seen);
        next.add(pkg);
        while (next.size() > MAX_SEEN) next.remove(next.iterator().next());
        sp.edit().putStringSet(K_SEEN, next).apply();
    }

    public Set<String> seen() {
        return unmodifiable(new TreeSet<>(sp.getStringSet(K_SEEN, Collections.emptySet())));
    }

    public void clearAll() {
        sp.edit().remove(K_APPS).remove(K_FUNCTIONS).apply();
    }

    /**
     * getStringSet hands back an instance the docs forbid modifying and whose
     * contents are undefined after an edit, so every read is copied out first.
     */
    private static Set<String> unmodifiable(Set<String> s) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(s));
    }
}
