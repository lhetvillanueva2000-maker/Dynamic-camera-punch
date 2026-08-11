package com.dcp.punch;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.dcp.punch.data.DcpNotificationListener;
import com.dcp.punch.data.IslandStore;
import com.dcp.punch.mem.MemoryBudget;

/**
 * Process-wide singletons.
 *
 * The store and the memory budget outlive the service window, so that toggling
 * the theme off and on does not lose what is currently playing, and so the
 * ballast survives a service restart.
 */
public class DcpApp extends Application {

    private static DcpApp instance;

    private IslandStore store;
    private MemoryBudget memory;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        store = new IslandStore();
        memory = new MemoryBudget(this);
    }

    public static DcpApp get() { return instance; }

    public IslandStore store() { return store; }
    public MemoryBudget memory() { return memory; }

    /**
     * The system is running out. The ballast is discretionary; the phone's
     * responsiveness is not, so hand it all back immediately.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            memory.shedForPressure();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        memory.shedForPressure();
    }

    /* ── Permission checks used by the UI and the service ─────────────── */

    public static boolean canDrawOverlay(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** Notification access is granted in Settings, not by a runtime dialog. */
    public static boolean hasNotificationAccess(Context c) {
        String enabled = Settings.Secure.getString(
                c.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        String me = new ComponentName(c, DcpNotificationListener.class).flattenToString();
        String meShort = new ComponentName(c, DcpNotificationListener.class).flattenToShortString();
        for (String part : enabled.split(":")) {
            if (part.equals(me) || part.equals(meShort)) return true;
        }
        return false;
    }
}
