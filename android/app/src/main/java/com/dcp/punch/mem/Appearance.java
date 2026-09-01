package com.dcp.punch.mem;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything about how the island looks and moves, plus the named presets that
 * store a whole configuration at once.
 *
 * Kept apart from Prefs deliberately. Prefs holds the handful of switches that
 * decide *whether* the theme runs; this holds the dozens of numbers that decide
 * what it looks like while it does, and it is the only thing a preset has to
 * capture. One file, one JSON blob per preset, no schema migration to write when
 * a new dial is added — an unknown key is ignored and a missing one falls back
 * to its default.
 */
public final class Appearance {

    private static final String FILE = "dcp_look";
    private static final String K_PRESETS = "presets";

    /* ── The settings, with their ranges ─────────────────────────────── */

    public static final String VARIANT = "variant";            // "a" | "b"
    public static final String OFFSET_X = "offsetX";           // dp, -60..60
    public static final String OFFSET_Y = "offsetY";           // dp, -20..80
    public static final String CORNER_RADIUS = "cornerRadius"; // dp, 0..40, compact
    public static final String EXP_RADIUS = "expRadius";       // dp, 12..48, expanded
    public static final String EXP_WIDTH = "expWidth";         // dp, 240..400
    public static final String IDLE_SCALE = "idleScale";       // %, 60..200
    public static final String COLLAPSED_ALPHA = "collapsedAlpha";  // %, 0..100
    public static final String BORDER_WIDTH = "borderWidth";   // dp, 0..3
    public static final String BG_COLOR = "bgColor";
    public static final String OUTLINE_COLOR = "outlineColor";
    public static final String SHADOW_ALPHA = "shadowAlpha";   // %, 0..100
    public static final String ANIM_SPEED = "animSpeed";       // %, 50..200 (1x = 100)
    public static final String BOUNCE = "bounce";              // 0..3
    public static final String REDUCE_ANIM = "reduceAnim";     // 0/1
    public static final String GLOW = "glow";                  // 0/1
    public static final String NOTIF_MODE = "notifMode";       // 0 minimised, 1 expanded
    public static final String AUTO_HIDE = "autoHide";         // seconds, 1..30, 0 = until opened

    /** Default for every key above. Anything absent here is not a setting. */
    private static final Map<String, Integer> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put(OFFSET_X, 0);
        DEFAULTS.put(OFFSET_Y, 0);
        DEFAULTS.put(CORNER_RADIUS, 18);
        DEFAULTS.put(EXP_RADIUS, 34);
        DEFAULTS.put(EXP_WIDTH, 340);
        DEFAULTS.put(IDLE_SCALE, 100);
        DEFAULTS.put(COLLAPSED_ALPHA, 0);
        DEFAULTS.put(BORDER_WIDTH, 0);
        DEFAULTS.put(BG_COLOR, 0xFF000000);
        DEFAULTS.put(OUTLINE_COLOR, 0x1AFFFFFF);
        DEFAULTS.put(SHADOW_ALPHA, 55);
        DEFAULTS.put(ANIM_SPEED, 100);
        DEFAULTS.put(BOUNCE, 1);
        DEFAULTS.put(REDUCE_ANIM, 0);
        DEFAULTS.put(GLOW, 1);
        DEFAULTS.put(NOTIF_MODE, 0);
        DEFAULTS.put(AUTO_HIDE, 5);
    }

    /** Inclusive slider bounds, for the UI to build itself from. */
    private static final Map<String, int[]> RANGES = new LinkedHashMap<>();
    static {
        RANGES.put(OFFSET_X, new int[]{-60, 60});
        RANGES.put(OFFSET_Y, new int[]{-20, 80});
        RANGES.put(CORNER_RADIUS, new int[]{0, 40});
        RANGES.put(EXP_RADIUS, new int[]{12, 48});
        RANGES.put(EXP_WIDTH, new int[]{240, 400});
        RANGES.put(IDLE_SCALE, new int[]{60, 200});
        RANGES.put(COLLAPSED_ALPHA, new int[]{0, 100});
        RANGES.put(BORDER_WIDTH, new int[]{0, 3});
        RANGES.put(SHADOW_ALPHA, new int[]{0, 100});
        RANGES.put(ANIM_SPEED, new int[]{50, 200});
        RANGES.put(BOUNCE, new int[]{0, 3});
        RANGES.put(AUTO_HIDE, new int[]{0, 30});
    }

    private static Appearance instance;
    private final SharedPreferences sp;
    /** Needed because the cutout variant lives in Prefs, not in this file. */
    private final Context app;

    /** Bumped on every write, so views can tell whether they are stale. */
    private volatile int revision;

    private Appearance(Context c) {
        app = c.getApplicationContext();
        sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Appearance get(Context c) {
        if (instance == null) instance = new Appearance(c);
        return instance;
    }

    public int revision() { return revision; }

    /* ── Reads and writes ────────────────────────────────────────────── */

    public int get(String key) {
        Integer def = DEFAULTS.get(key);
        return sp.getInt(key, def == null ? 0 : def);
    }

    public void set(String key, int value) {
        int[] r = RANGES.get(key);
        if (r != null) value = Math.max(r[0], Math.min(r[1], value));
        sp.edit().putInt(key, value).apply();
        revision++;
    }

    public boolean flag(String key) { return get(key) != 0; }
    public void setFlag(String key, boolean on) { set(key, on ? 1 : 0); }

    public int min(String key) { int[] r = RANGES.get(key); return r == null ? 0 : r[0]; }
    public int max(String key) { int[] r = RANGES.get(key); return r == null ? 100 : r[1]; }
    public int def(String key) { Integer d = DEFAULTS.get(key); return d == null ? 0 : d; }

    /** Put every dial back where it started. */
    public void resetAll() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : DEFAULTS.keySet()) e.remove(k);
        e.apply();
        revision++;
    }

    /* ── Derived values the renderer actually wants ──────────────────── */

    /**
     * Multiplier on every animation duration. A higher "speed" is a shorter
     * duration, which is the opposite of what a naive read of the number would
     * do, so the conversion lives here rather than at each call site.
     */
    public float animScale() {
        if (flag(REDUCE_ANIM)) return 0.01f;      // effectively instant
        return 100f / Math.max(1, get(ANIM_SPEED));
    }

    /** 0 = no overshoot, 3 = the springiest the interpolator will go. */
    public float bounceTension() {
        switch (get(BOUNCE)) {
            case 0:  return 0f;
            case 2:  return 1.9f;
            case 3:  return 3.1f;
            default: return 1.0f;
        }
    }

    public float collapsedAlpha() { return get(COLLAPSED_ALPHA) / 100f; }
    public float shadowAlpha() { return get(SHADOW_ALPHA) / 100f; }
    public float idleScale() { return get(IDLE_SCALE) / 100f; }

    /** Seconds an alert stays up; 0 means "until it is opened or swiped". */
    public long autoHideMs() {
        int s = get(AUTO_HIDE);
        return s <= 0 ? Long.MAX_VALUE : s * 1000L;
    }

    /* ── Presets ─────────────────────────────────────────────────────── */

    /**
     * A preset is the whole settings map under a name. Stored as one JSON array
     * so adding a dial never needs a migration: saving writes whatever exists
     * now, and applying only sets keys it recognises.
     */
    public List<String> presetNames() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(K_PRESETS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getJSONObject(i).optString("name", "Preset " + (i + 1)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void savePreset(String name) {
        if (name == null || name.trim().isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(sp.getString(K_PRESETS, "[]"));
            JSONObject values = new JSONObject();
            for (String k : DEFAULTS.keySet()) values.put(k, get(k));
            // The cutout variant is a Prefs setting, not one of ours. Reading it
            // out of this file would have quietly stored nothing and applied
            // nothing — a preset that silently forgot half of what it promised.
            values.put(VARIANT, Prefs.get(app).getVariant());

            JSONObject entry = new JSONObject();
            entry.put("name", name.trim());
            entry.put("values", values);

            // Saving over an existing name replaces it rather than stacking.
            int at = indexOf(arr, name.trim());
            if (at >= 0) arr.put(at, entry); else arr.put(entry);

            sp.edit().putString(K_PRESETS, arr.toString()).apply();
            revision++;
        } catch (Exception ignored) { }
    }

    public boolean applyPreset(String name) {
        try {
            JSONArray arr = new JSONArray(sp.getString(K_PRESETS, "[]"));
            int at = indexOf(arr, name);
            if (at < 0) return false;
            JSONObject values = arr.getJSONObject(at).getJSONObject("values");
            SharedPreferences.Editor e = sp.edit();
            for (String k : DEFAULTS.keySet()) {
                if (values.has(k)) e.putInt(k, values.getInt(k));
            }
            e.apply();
            if (values.has(VARIANT)) Prefs.get(app).setVariant(values.getString(VARIANT));
            revision++;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void deletePreset(String name) {
        try {
            JSONArray arr = new JSONArray(sp.getString(K_PRESETS, "[]"));
            int at = indexOf(arr, name);
            if (at < 0) return;
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) if (i != at) next.put(arr.get(i));
            sp.edit().putString(K_PRESETS, next.toString()).apply();
            revision++;
        } catch (Exception ignored) { }
    }

    private static int indexOf(JSONArray arr, String name) {
        for (int i = 0; i < arr.length(); i++) {
            if (name.equals(arr.optJSONObject(i) == null
                    ? null : arr.optJSONObject(i).optString("name"))) return i;
        }
        return -1;
    }
}
