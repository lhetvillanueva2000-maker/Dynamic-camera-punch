package com.dcp.punch.mem;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Memory, processor and storage, read from the device rather than guessed.
 *
 * WHAT IS AND IS NOT AVAILABLE, because a stats panel that quietly invents a
 * number is worse than one that admits a gap:
 *
 *   RAM      total and available come from ActivityManager.MemoryInfo, the same
 *            figures the system's own memory screen uses.
 *
 *   Storage  StatFs on the data directory. Note this is what the *app* can see,
 *            which on a device with an SD card or multiple users is not the
 *            whole disk, and the panel says so.
 *
 *   CPU      cores, ABI and model are readable. A live system-wide CPU
 *            percentage is NOT: /proc/stat has been unreadable to ordinary apps
 *            since Android 8, and every app still showing one is either reading
 *            its own process or making it up. So this reports the honest thing —
 *            how much CPU time *this* app has used since it started — and the
 *            panel labels it as such.
 */
public final class DeviceStats {

    private final Context app;
    private final ActivityManager am;

    public DeviceStats(Context c) {
        this.app = c.getApplicationContext();
        this.am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
    }

    /* ── Memory ──────────────────────────────────────────────────────── */

    public long ramTotal() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem;
    }

    public long ramAvailable() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    public long ramUsed() { return Math.max(0, ramTotal() - ramAvailable()); }

    public boolean ramLow() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.lowMemory;
    }

    /* ── Storage ─────────────────────────────────────────────────────── */

    private StatFs fs() {
        return new StatFs(Environment.getDataDirectory().getAbsolutePath());
    }

    public long storageTotal() {
        try {
            StatFs f = fs();
            return f.getBlockSizeLong() * f.getBlockCountLong();
        } catch (Exception e) { return 0; }
    }

    public long storageFree() {
        try {
            StatFs f = fs();
            return f.getBlockSizeLong() * f.getAvailableBlocksLong();
        } catch (Exception e) { return 0; }
    }

    public long storageUsed() { return Math.max(0, storageTotal() - storageFree()); }

    /* ── Processor ───────────────────────────────────────────────────── */

    public int cpuCores() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    public String cpuAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        return abis != null && abis.length > 0 ? abis[0] : "unknown";
    }

    /**
     * A readable chip name. /proc/cpuinfo still exposes "Hardware" on most ARM
     * devices; Build.SOC_MODEL is the supported route but only exists from
     * Android 12. Falls back to the board name, then to the core count.
     */
    public String cpuModel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String soc = Build.SOC_MODEL;
            if (soc != null && !soc.isEmpty() && !"unknown".equalsIgnoreCase(soc)) {
                String maker = Build.SOC_MANUFACTURER;
                return (maker == null || maker.isEmpty() || "unknown".equalsIgnoreCase(maker))
                        ? soc : maker + " " + soc;
            }
        }
        String fromProc = readCpuInfoField();
        if (fromProc != null) return fromProc;
        if (Build.BOARD != null && !Build.BOARD.isEmpty()) return Build.BOARD;
        return cpuCores() + " cores";
    }

    private String readCpuInfoField() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/cpuinfo"), 2048);
            String line;
            while ((line = r.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon).trim();
                if (key.equalsIgnoreCase("Hardware") || key.equalsIgnoreCase("model name")) {
                    String v = line.substring(colon + 1).trim();
                    if (!v.isEmpty()) return v;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
        return null;
    }

    /** Peak clock in MHz, or 0 where the kernel does not publish it. */
    public int cpuMaxMhz() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(
                    "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"), 64);
            String line = r.readLine();
            if (line != null) return (int) (Long.parseLong(line.trim()) / 1000);
        } catch (Exception ignored) {
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
        return 0;
    }

    /**
     * CPU milliseconds this process has used since it started. Deliberately not
     * dressed up as a system-wide percentage — see the class note.
     */
    public long ownCpuMillis() {
        try { return Process.getElapsedCpuTime(); }
        catch (Exception e) { return 0; }
    }

    /** Wall-clock milliseconds this process has been alive. */
    public long ownUptimeMillis() {
        return android.os.SystemClock.elapsedRealtime() - startedAt;
    }

    private static final long startedAt = android.os.SystemClock.elapsedRealtime();

    /** Share of one core this process has averaged over its lifetime, 0..1. */
    public float ownCpuShare() {
        long up = ownUptimeMillis();
        if (up <= 0) return 0f;
        return Math.max(0f, Math.min(1f, ownCpuMillis() / (float) up));
    }

    /* ── Formatting ──────────────────────────────────────────────────── */

    public static String bytes(long b) {
        if (b <= 0) return "—";
        double gb = b / (1024.0 * 1024 * 1024);
        if (gb >= 1) return String.format(java.util.Locale.US, "%.1f GB", gb);
        return (b / (1024 * 1024)) + " MB";
    }

    public static String mhz(int m) {
        if (m <= 0) return "—";
        return m >= 1000
                ? String.format(java.util.Locale.US, "%.2f GHz", m / 1000.0)
                : m + " MHz";
    }
}
