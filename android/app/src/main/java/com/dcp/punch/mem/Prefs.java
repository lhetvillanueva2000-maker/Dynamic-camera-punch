package com.dcp.punch.mem;

import android.content.Context;
import android.content.SharedPreferences;

/** Every persisted setting, in one place. */
public final class Prefs {

    private static final String FILE = "dcp";
    private static final String K_ENABLED = "theme_enabled";
    private static final String K_VARIANT = "cutout_variant";     // "a" | "b"
    private static final String K_BUDGET = "ram_budget_bytes";
    private static final String K_BOOT = "start_on_boot";
    private static final String K_HANDLE = "handle_y_fraction";
    private static final String K_ALWAYS = "island_always_visible";
    private static final String K_AUTO_MEM = "auto_memory";

    private static Prefs instance;

    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context c) {
        if (instance == null) instance = new Prefs(c);
        return instance;
    }

    public boolean isThemeEnabled() { return sp.getBoolean(K_ENABLED, false); }
    public void setThemeEnabled(boolean v) { sp.edit().putBoolean(K_ENABLED, v).apply(); }

    /** "a" = fused to the bezel, "b" = free-floating. */
    public String getVariant() { return sp.getString(K_VARIANT, "b"); }
    public void setVariant(String v) { sp.edit().putString(K_VARIANT, v).apply(); }

    public long getBudgetBytes() {
        return sp.getLong(K_BUDGET, MemoryBudget.DEFAULT_BUDGET_BYTES);
    }
    public void setBudgetBytes(long v) { sp.edit().putLong(K_BUDGET, v).apply(); }

    public boolean isStartOnBoot() { return sp.getBoolean(K_BOOT, false); }
    public void setStartOnBoot(boolean v) { sp.edit().putBoolean(K_BOOT, v).apply(); }

    /** Vertical position of the pull handle, 0..1 down the screen. */
    public float getHandleY() { return sp.getFloat(K_HANDLE, 0.42f); }
    public void setHandleY(float v) { sp.edit().putFloat(K_HANDLE, v).apply(); }

    /**
     * Keep the island on screen when there is nothing to show.
     *
     * Off by default, which is the behaviour people expect: with no notification
     * the overlay window is taken down entirely rather than resting as a shape
     * on top of the launcher.
     */
    public boolean isAlwaysVisible() { return sp.getBoolean(K_ALWAYS, false); }
    public void setAlwaysVisible(boolean v) { sp.edit().putBoolean(K_ALWAYS, v).apply(); }

    /** Let the app manage its own footprint. On by default. */
    public boolean isAutoMemory() { return sp.getBoolean(K_AUTO_MEM, true); }
    public void setAutoMemory(boolean v) { sp.edit().putBoolean(K_AUTO_MEM, v).apply(); }
}
