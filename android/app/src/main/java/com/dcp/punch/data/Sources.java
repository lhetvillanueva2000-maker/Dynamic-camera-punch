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
 * Two independent filters, both applied before anything reaches the store:
 *
 *   By kind    — messages, media, calls, timers, navigation, downloads, other
 *                notifications, and the four permission-free system events.
 *                Everything is on by default; the island is opt-out, not opt-in.
 *
 *   By app     — a blocklist of package names. Apps are learned from the
 *                notifications that actually arrive, which is why this needs no
 *                QUERY_ALL_PACKAGES: an app the island has never heard from is
 *                not something the user needs to make a decision about.
 *
 * Kept separate from Prefs because this is the one setting group that grows —
 * a new source of content adds a constant here and a row in the control panel,
 * and nothing else has to change.
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
    }

    private static final String FILE = "dcp_sources";
    private static final String K_BLOCKED = "blocked_packages";
    private static final String K_SEEN = "seen_packages";

    /** Never let the learned-app list grow without bound. */
    private static final int MAX_SEEN = 120;

    private static Sources instance;

    private final SharedPreferences sp;

    private Sources(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Sources get(Context c) {
        if (instance == null) instance = new Sources(c);
        return instance;
    }

    /* ── By kind ─────────────────────────────────────────────────────── */

    public boolean isEnabled(Source s) {
        return sp.getBoolean("src_" + s.key, true);
    }

    public void setEnabled(Source s, boolean on) {
        sp.edit().putBoolean("src_" + s.key, on).apply();
    }

    /* ── By app ──────────────────────────────────────────────────────── */

    public boolean isAppAllowed(String pkg) {
        if (pkg == null) return true;
        return !blocked().contains(pkg);
    }

    public void setAppAllowed(String pkg, boolean allowed) {
        Set<String> b = new LinkedHashSet<>(blocked());
        if (allowed) b.remove(pkg); else b.add(pkg);
        sp.edit().putStringSet(K_BLOCKED, b).apply();
    }

    public Set<String> blocked() {
        // The returned set from getStringSet must not be modified — the docs are
        // explicit that the instance is shared and the result is undefined.
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(sp.getStringSet(K_BLOCKED, Collections.emptySet())));
    }

    /**
     * Note that this package posted something the island could have shown, so it
     * can be offered in the control panel. Cheap and idempotent: it only writes
     * when the package is new.
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

    /** Apps the island has heard from, alphabetically by package. */
    public Set<String> seen() {
        return Collections.unmodifiableSet(
                new TreeSet<>(sp.getStringSet(K_SEEN, Collections.emptySet())));
    }

    public void forgetAll() {
        sp.edit().remove(K_SEEN).remove(K_BLOCKED).apply();
    }
}
