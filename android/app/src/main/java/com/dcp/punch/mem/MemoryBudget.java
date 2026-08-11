package com.dcp.punch.mem;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The thing the right-edge dial actually controls.
 *
 * HONESTY NOTE, because this is easy to fake and worth being straight about:
 * an app cannot simply "decide" to use a given amount of RAM through ordinary
 * caching. The island's genuine working set is roughly 25-45 MB. To make the
 * number on the dial a real measurement rather than decoration, the budget is
 * met by allocating a **ballast** — real memory, really resident — on top of
 * whatever the app is actually using.
 *
 * Two consequences the UI states plainly:
 *   • Raising the budget does not make the island faster. Nothing here is
 *     starved for memory.
 *   • A large resident footprint makes the process a *bigger* target for the
 *     low-memory killer, not a smaller one, and evicts other apps from RAM.
 *
 * Implementation choices that matter:
 *
 *   Direct buffers, not byte[]. The Dalvik heap is capped per-process
 *   (getLargeMemoryClass() is commonly 256-512 MB), so a 728 MB Java array
 *   would OOM long before it got there. ByteBuffer.allocateDirect is off-heap
 *   and bounded only by the device.
 *
 *   Pages are touched. Allocating address space does not make it resident;
 *   the kernel only backs a page once written. Writing one byte per 4 KiB page
 *   is what turns the allocation into real RSS that shows up in `dumpsys`.
 *
 *   There is a safety valve. onTrimMemory at COMPLETE/CRITICAL sheds the whole
 *   ballast immediately. Holding memory hostage while the system thrashes would
 *   be indefensible, so the budget yields and re-arms once pressure clears.
 */
public final class MemoryBudget {

    private static final String TAG = "dcp.mem";

    /**
     * Headroom left for Android and every other app, carved off the top before
     * the dial sees a single byte. On an 8 GB phone the dial's ceiling is 6.5 GB.
     */
    public static final long OS_RESERVE_BYTES = 1_536L * 1024 * 1024;   // 1.5 GiB

    /** The specified minimum for the theme to run smoothly, and the default. */
    public static final long MIN_BUDGET_BYTES = 762L * 1024 * 1024;

    public static final long DEFAULT_BUDGET_BYTES = MIN_BUDGET_BYTES;

    /**
     * Absolute floor, used only where the device cannot afford the 762 MB
     * minimum once the OS reserve is taken out — a 2 GB phone has 500 MB left,
     * and honouring the nominal minimum there would mean allocating a third of
     * the machine and getting killed for it. The dial reports the real ceiling
     * instead of pretending.
     */
    public static final long FLOOR_BYTES = 64L * 1024 * 1024;

    private static final int CHUNK_BYTES = 16 * 1024 * 1024;   // 16 MiB per buffer
    private static final int PAGE = 4096;

    private final Context app;
    private final ActivityManager am;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<ByteBuffer> ballast = new ArrayList<>();

    private long budgetBytes;
    private boolean shed;              // ballast dropped under system pressure

    public MemoryBudget(Context context) {
        this.app = context.getApplicationContext();
        this.am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        this.budgetBytes = clamp(Prefs.get(app).getBudgetBytes());
    }

    /* ── Device limits ───────────────────────────────────────────────── */

    /** Physical RAM in the device. */
    public long totalDeviceBytes() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem;
    }

    /**
     * The most the dial may ask for: the device, less the OS reserve. 8 GB gives
     * 6.5 GB; 12 GB gives 10.5 GB. The reserve is taken off the top and the dial
     * can never reach into it.
     */
    public long maxBudgetBytes() {
        return Math.max(FLOOR_BYTES, totalDeviceBytes() - OS_RESERVE_BYTES);
    }

    /**
     * The bottom of the dial's range: 762 MB, except on a device too small to
     * give that up after the reserve, where the ceiling becomes the floor too.
     */
    public long minBudgetBytes() {
        return Math.min(MIN_BUDGET_BYTES, maxBudgetBytes());
    }

    public long clamp(long bytes) {
        return Math.max(minBudgetBytes(), Math.min(bytes, maxBudgetBytes()));
    }

    public long getBudgetBytes() { return budgetBytes; }

    /* ── Live measurement ────────────────────────────────────────────── */

    /**
     * Actual proportional set size for this process, straight from the same
     * source `adb shell dumpsys meminfo` reads. This is what the gauge shows
     * as "actual" — never an estimate, never the budget echoed back.
     */
    public long actualUsageBytes() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.getTotalPss() * 1024L;
    }

    /** Free memory left to the system as a whole. */
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

    public long ballastBytes() {
        synchronized (ballast) {
            return (long) ballast.size() * CHUNK_BYTES;
        }
    }

    /* ── Applying the budget ─────────────────────────────────────────── */

    /**
     * Set the budget and reshape the ballast to match. Called from the dial as
     * the finger moves, so it is cheap when the target has not changed by a
     * whole chunk.
     */
    public void setBudgetBytes(long bytes) {
        budgetBytes = clamp(bytes);
        Prefs.get(app).setBudgetBytes(budgetBytes);
        if (!shed) applyBallast();
    }

    /**
     * Grow or shrink the ballast so that (real usage + ballast) lands on the
     * budget. Measured against live PSS rather than a running total, so the
     * island's own churn is absorbed instead of stacked on top.
     */
    public synchronized void applyBallast() {
        if (shed) return;

        long realWithoutBallast = actualUsageBytes() - ballastBytes();
        long wanted = budgetBytes - realWithoutBallast;
        int wantChunks = (int) Math.max(0, wanted / CHUNK_BYTES);

        synchronized (ballast) {
            while (ballast.size() > wantChunks) {
                ballast.remove(ballast.size() - 1);
            }
            while (ballast.size() < wantChunks) {
                try {
                    ByteBuffer b = ByteBuffer.allocateDirect(CHUNK_BYTES);
                    // Touch one byte per page: allocation alone is only address
                    // space, and the dial would report memory that isn't there.
                    for (int off = 0; off < CHUNK_BYTES; off += PAGE) {
                        b.put(off, (byte) 1);
                    }
                    ballast.add(b);
                } catch (OutOfMemoryError | RuntimeException e) {
                    // The device said no. Stop where we are and let the gauge
                    // report the honest number rather than the requested one.
                    Log.w(TAG, "ballast capped at " + ballast.size() + " chunks: " + e);
                    break;
                }
            }
        }
        if (wantChunks > 0) System.gc();
    }

    /**
     * System is critically short. Drop everything immediately — the ballast is
     * discretionary and the phone's responsiveness is not.
     */
    public void shedForPressure() {
        synchronized (ballast) {
            if (ballast.isEmpty() && shed) return;
            ballast.clear();
        }
        shed = true;
        Log.i(TAG, "ballast shed under memory pressure");
        // Re-arm later; if pressure persists the next attempt sheds again.
        main.postDelayed(() -> {
            shed = false;
            if (!isUnderSystemPressure()) applyBallast();
        }, 60_000L);
    }

    public boolean isShed() { return shed; }

    /** Release everything, e.g. when the theme is switched off. */
    public void release() {
        synchronized (ballast) { ballast.clear(); }
        System.gc();
    }

    /* ── Formatting ──────────────────────────────────────────────────── */

    public static String mb(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }

    public static String gb(long bytes) {
        double g = bytes / (1024.0 * 1024 * 1024);
        return String.format(java.util.Locale.US, "%.1f GB", g);
    }
}
