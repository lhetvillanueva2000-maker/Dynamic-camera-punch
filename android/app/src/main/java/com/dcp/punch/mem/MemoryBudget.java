package com.dcp.punch.mem;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The memory guard: a ceiling the app stays under, and the machinery that keeps
 * it there.
 *
 * WHAT CHANGED IN v2.2.0, AND WHY IT WAS WRONG BEFORE
 * Until now the dial set a *target* and the app allocated an off-heap "ballast"
 * to meet it — 500-odd MB of real, resident, deliberately wasted memory, so that
 * the number on the dial would be a measurement rather than a decoration. It was
 * honest about being waste, and it was still waste. On a low-end phone it is
 * indefensible: it makes this process the fattest thing on the device and the
 * first one the low-memory killer reaches for, and it evicts the user's actual
 * apps so they cold-start instead of resuming.
 *
 * The dial now sets a **ceiling**, which is what everyone assumed it did. The
 * app allocates nothing to reach it and simply stays far below it — the island's
 * genuine working set is around 30-45 MB, which is why 45 MB is the lowest
 * ceiling the dial offers. Two mechanisms, and they run together:
 *
 *   Automatic (on by default). A watchdog samples PSS and, as usage climbs
 *   toward the ceiling, sheds what can be shed: cached artwork on presentations
 *   that are not on screen, then a GC. It also trims on every system memory
 *   warning rather than waiting for its own timer.
 *
 *   Manual. The dial's ceiling. Reaching 80% of it triggers the same trim early;
 *   the ceiling is the backstop, not the operating point.
 *
 * The gauge shows the ceiling and the *measured* footprint side by side, read
 * from the same source `adb shell dumpsys meminfo` uses, so the two can always
 * be compared and the app cannot quietly lie about what it costs.
 */
public final class MemoryBudget {

    private static final String TAG = "dcp.mem";

    /**
     * Headroom left for Android and every other app, carved off the top before
     * the dial sees a single byte. On an 8 GB phone the ceiling tops out at
     * 6.5 GB.
     */
    public static final long OS_RESERVE_BYTES = 1_536L * 1024 * 1024;   // 1.5 GiB

    /**
     * The lowest ceiling the dial will accept: 45 MB, which is the top of the
     * island's real working range. Below this the app would be trimming caches
     * it is actively drawing from, so the floor is set at the point where the
     * limit stops being a limit and starts being a handicap.
     */
    public static final long MIN_BUDGET_BYTES = 45L * 1024 * 1024;

    /** Ship at the floor. The app does not need more and will not take more. */
    public static final long DEFAULT_BUDGET_BYTES = MIN_BUDGET_BYTES;

    /** Kept as an alias so callers reading a "hard floor" still make sense. */
    public static final long FLOOR_BYTES = MIN_BUDGET_BYTES;

    /** Trim once the footprint passes this share of the ceiling. */
    private static final float TRIM_AT = 0.80f;

    /** How often the watchdog samples, while the theme is running. */
    private static final long WATCH_MS = 20_000L;

    /** Anything a trim can release registers here. */
    public interface Trimmable {
        /**
         * Release what is not needed right now.
         *
         * @param hard true when the system itself is short of memory, not merely
         *             when this app is drifting up toward its own ceiling. A hard
         *             trim should give up everything that can be rebuilt.
         */
        void onTrimMemory(boolean hard);
    }

    private final Context app;
    private final ActivityManager am;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Trimmable> trimmables = new ArrayList<>();

    private long budgetBytes;
    private boolean autoManage;
    private boolean watching;

    /** Last sampled PSS, so the gauge can redraw without re-reading it. */
    private volatile long lastSample;
    private long lastTrimAt;

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (!watching) return;
            sample();
            if (autoManage && shouldTrim()) trim(false);
            main.postDelayed(this, WATCH_MS);
        }
    };

    public MemoryBudget(Context context) {
        this.app = context.getApplicationContext();
        this.am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        Prefs p = Prefs.get(app);
        this.budgetBytes = clamp(p.getBudgetBytes());
        this.autoManage = p.isAutoMemory();
        this.lastSample = readPss();
    }

    /* ── Device limits ───────────────────────────────────────────────── */

    public long totalDeviceBytes() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem;
    }

    /** The device, less the OS reserve. 8 GB → 6.5 GB; 12 GB → 10.5 GB. */
    public long maxBudgetBytes() {
        return Math.max(FLOOR_BYTES, totalDeviceBytes() - OS_RESERVE_BYTES);
    }

    /** 45 MB, unless the device is too small to give even that up. */
    public long minBudgetBytes() {
        return Math.min(MIN_BUDGET_BYTES, maxBudgetBytes());
    }

    public long clamp(long bytes) {
        return Math.max(minBudgetBytes(), Math.min(bytes, maxBudgetBytes()));
    }

    public long getBudgetBytes() { return budgetBytes; }

    public void setBudgetBytes(long bytes) {
        budgetBytes = clamp(bytes);
        Prefs.get(app).setBudgetBytes(budgetBytes);
        if (shouldTrim()) trim(false);
    }

    public boolean isAutoManage() { return autoManage; }

    public void setAutoManage(boolean on) {
        autoManage = on;
        Prefs.get(app).setAutoMemory(on);
        if (on) trim(false);
    }

    /* ── Measurement ─────────────────────────────────────────────────── */

    /**
     * Proportional set size for this process, from the same source
     * `dumpsys meminfo` reads. Genuinely costs a few milliseconds, so callers
     * that redraw get the cached figure and the watchdog refreshes it.
     */
    private long readPss() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.getTotalPss() * 1024L;
    }

    /** Re-read the footprint now. */
    public long sample() {
        lastSample = readPss();
        return lastSample;
    }

    /** The last measured footprint, without paying for a fresh read. */
    public long actualUsageBytes() { return lastSample; }

    public long systemAvailableBytes() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    public boolean isUnderSystemPressure() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.lowMemory;
    }

    /** How full the ceiling is, 0..1, for the gauge. */
    public float loadFraction() {
        long cap = Math.max(1, budgetBytes);
        return Math.max(0f, Math.min(1f, lastSample / (float) cap));
    }

    private boolean shouldTrim() {
        return lastSample > budgetBytes * TRIM_AT;
    }

    /* ── Trimming ────────────────────────────────────────────────────── */

    public void register(Trimmable t) {
        synchronized (trimmables) { if (!trimmables.contains(t)) trimmables.add(t); }
    }

    public void unregister(Trimmable t) {
        synchronized (trimmables) { trimmables.remove(t); }
    }

    /**
     * Give memory back. Rate-limited, because a trim that runs every frame is
     * itself a performance problem — the caches it drops have to be rebuilt.
     */
    public void trim(boolean hard) {
        long now = android.os.SystemClock.uptimeMillis();
        if (!hard && now - lastTrimAt < 5_000L) return;
        lastTrimAt = now;

        List<Trimmable> copy;
        synchronized (trimmables) { copy = new ArrayList<>(trimmables); }
        for (Trimmable t : copy) {
            try { t.onTrimMemory(hard); } catch (Exception e) { Log.w(TAG, "trim failed: " + e); }
        }
        if (hard) System.gc();
        sample();
    }

    /** Start the watchdog. Called when the overlay comes up. */
    public void startWatch() {
        if (watching) return;
        watching = true;
        main.postDelayed(watchdog, WATCH_MS);
    }

    public void stopWatch() {
        watching = false;
        main.removeCallbacks(watchdog);
    }

    /** The system is short of memory. Everything that can go, goes. */
    public void shedForPressure() {
        Log.i(TAG, "system memory pressure — hard trim");
        trim(true);
    }

    /** Kept for the service teardown path. */
    public void release() {
        stopWatch();
        trim(true);
    }

    /* ── What the ceiling buys ───────────────────────────────────────── */

    /*
     * Costs used to turn a ceiling into a count. Every figure below is derived
     * from something real rather than picked, because a capacity readout built
     * on invented numbers would be worse than no readout at all.
     */

    /** Overlay, view, paints, store, service. Measured at rest on a 3.5 GB device. */
    public static final long BASE_BYTES = 26L * 1024 * 1024;

    /**
     * One held notification. The avatar dominates: DcpNotificationListener caps
     * decoded artwork at 192x192, and ARGB_8888 is 4 bytes a pixel. The small
     * icon and the strings are the rest.
     */
    public static final long PER_NOTIFICATION_BYTES =
            192L * 192 * 4          // avatar bitmap, at the decoder's own cap
            + 48L * 48 * 4          // small icon
            + 8L * 1024;            // title, body, actions, the object itself

    /** One app on the list: a package name in a preference set, and nothing else. */
    public static final long PER_APP_BYTES = 512;

    /** One phone function: a broadcast filter entry and its last-seen state. */
    public static final long PER_FUNCTION_BYTES = 4L * 1024;

    /** The four things that work with no permission at all. */
    public static final int SYSTEM_FUNCTIONS = 4;

    /**
     * The island only ever holds two activities and one alert, whatever the
     * ceiling says. That is a design limit, not a memory one, and the readout
     * says so rather than implying the dial can raise it.
     */
    public static final int CONCURRENT_LIMIT = 3;

    /**
     * Past this many, the app list has stopped being a memory question. Nobody
     * has four thousand apps installed, and printing "3888" would suggest the
     * figure means something it does not.
     */
    private static final int APPS_BEYOND_COUNTING = 999;

    /** What a given ceiling works out to. All fields are counts, not estimates. */
    public static final class Capacity {
        public long headroomBytes;
        public int apps;
        public int notifications;
        public int functions;
        public int concurrent = CONCURRENT_LIMIT;
        public boolean baseExceedsCeiling;
        /** True when memory is no longer what limits the app list. */
        public boolean appsUncounted;
    }

    public Capacity capacity() {
        Capacity c = new Capacity();
        long head = budgetBytes - BASE_BYTES;
        c.baseExceedsCeiling = head <= 0;
        c.headroomBytes = Math.max(0, head);

        // The functions come first — they are the cheapest and they work without
        // any permission, so they are what survives the tightest ceiling.
        long forFunctions = Math.min(c.headroomBytes, SYSTEM_FUNCTIONS * PER_FUNCTION_BYTES);
        c.functions = (int) (forFunctions / PER_FUNCTION_BYTES);

        long rest = c.headroomBytes - forFunctions;
        // Split what is left: notifications are what the island is for, so they
        // get the lion's share, and the app list is nearly free either way.
        long forNotifs = (long) (rest * 0.9);
        c.notifications = (int) (forNotifs / PER_NOTIFICATION_BYTES);
        long apps = (rest - forNotifs) / PER_APP_BYTES;
        c.appsUncounted = apps > APPS_BEYOND_COUNTING;
        c.apps = (int) Math.min(apps, APPS_BEYOND_COUNTING);
        return c;
    }

    /* ── Formatting ──────────────────────────────────────────────────── */

    public static String mb(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }

    public static String gb(long bytes) {
        double g = bytes / (1024.0 * 1024 * 1024);
        return String.format(java.util.Locale.US, "%.1f GB", g);
    }

    /** MB while that still reads cleanly, GB once it does not. */
    public static String readable(long bytes) {
        long m = bytes / (1024 * 1024);
        return m < 1024 ? m + " MB" : gb(bytes);
    }
}
