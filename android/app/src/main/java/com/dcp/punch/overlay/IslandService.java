package com.dcp.punch.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.dcp.punch.DcpApp;
import com.dcp.punch.R;
import com.dcp.punch.data.IslandStore;
import com.dcp.punch.data.Presentation;
import com.dcp.punch.data.SystemMonitor;
import com.dcp.punch.mem.MemoryBudget;
import com.dcp.punch.mem.Prefs;
import com.dcp.punch.ui.MainActivity;

/**
 * Owns the overlay for as long as the theme is enabled.
 *
 * Two windows, each sized exactly to its own content:
 *
 *   island  TOP|CENTER_HORIZONTAL, re-measured every animation frame. Being
 *           exactly island-sized is what lets every other pixel of the screen
 *           keep receiving touches — an overlay that covered the whole display
 *           would break every app underneath it.
 *   panel   RIGHT|CENTER_VERTICAL, the pull tab and RAM dial.
 *
 * Both use TYPE_APPLICATION_OVERLAY, which is the only window type a normal app
 * may use over other apps, and only after the user grants "Display over other
 * apps" in Settings.
 */
public class IslandService extends Service implements IslandStore.Listener {

    private static final String TAG = "dcp.svc";
    private static final String CHANNEL = "dcp_theme";
    private static final int NOTIF_ID = 42;

    /** Broadcast the demo activity listens for, so it can free its WebView. */
    public static final String ACTION_CLOSE_DEMO = "com.dcp.punch.CLOSE_DEMO";

    /** Signature-level permission gating the above; see AndroidManifest. */
    public static final String PERMISSION_INTERNAL = "com.dcp.punch.permission.INTERNAL";

    public static final String ACTION_STOP = "com.dcp.punch.STOP_THEME";

    private static volatile boolean running;

    private WindowManager wm;
    private IslandView island;
    private SidePanelView panel;
    private WindowManager.LayoutParams islandLp, panelLp;

    private IslandStore store;
    private SystemMonitor system;
    private MemoryBudget memory;

    private final Handler main = new Handler(Looper.getMainLooper());

    /** Re-tops the ballast periodically: the app's own usage drifts. */
    private final Runnable ballastTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            memory.applyBallast();
            main.postDelayed(this, 30_000L);
        }
    };

    public static boolean isRunning() { return running; }

    /**
     * Cross-process check. `running` is a static, and the demo activity lives in
     * its own process where that static is always false. Since Android 8,
     * getRunningServices only reports the caller's own services — which is
     * precisely what is being asked here.
     */
    public static boolean isRunning(Context c) {
        if (running) return true;
        android.app.ActivityManager am =
                (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningServiceInfo si
                : am.getRunningServices(Integer.MAX_VALUE)) {
            if (IslandService.class.getName().equals(si.service.getClassName())) return true;
        }
        return false;
    }

    public static void start(Context c) {
        Intent i = new Intent(c, IslandService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, IslandService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        store = DcpApp.get().store();
        memory = DcpApp.get().memory();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Prefs.get(this).setThemeEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }

        // The permission can be revoked while we are running; never assume.
        if (!DcpApp.canDrawOverlay(this)) {
            Log.w(TAG, "overlay permission missing — stopping");
            Prefs.get(this).setThemeEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (running) return START_STICKY;
        running = true;

        goForeground();
        addWindows();

        store.setListener(this);
        system = new SystemMonitor(this, store);
        system.start();

        // The live demonstration is a second WebView-sized process. Shut it down
        // the moment the theme takes over — that is the RAM the overlay uses.
        sendBroadcast(new Intent(ACTION_CLOSE_DEMO).setPackage(getPackageName()),
                PERMISSION_INTERNAL);

        memory.applyBallast();
        main.postDelayed(ballastTick, 30_000L);

        Prefs.get(this).setThemeEnabled(true);
        return START_STICKY;
    }

    /* ── Windows ─────────────────────────────────────────────────────── */

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int baseFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
    }

    private void addWindows() {
        String variant = Prefs.get(this).getVariant();

        island = new IslandView(this, store, variant, new IslandView.Callbacks() {
            @Override public void onGeometryChanged() { refreshIslandWindow(); }
            @Override public void onExpandedChanged(boolean expanded) { refreshIslandWindow(); }
            @Override public void onOpenContent(Presentation p) { openContent(p); }
        });

        islandLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(), baseFlags(), PixelFormat.TRANSLUCENT);
        islandLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        islandLp.y = Math.round(island.islandTopPx());
        // Draw into the punch-hole area rather than being pushed below it —
        // without this the island would float in the status bar instead of
        // straddling the camera.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            islandLp.layoutInDisplayCutoutMode = WindowManager.LayoutParams
                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ALWAYS only exists from API 30; SHORT_EDGES is the strongest
            // option on 28-29 and passing the newer value there is undefined.
            islandLp.layoutInDisplayCutoutMode = WindowManager.LayoutParams
                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        wm.addView(island, islandLp);

        panel = new SidePanelView(this, memory, new SidePanelView.Callbacks() {
            @Override public void onPanelOpenChanged(boolean open) { refreshPanelWindow(open); }
            @Override public void onGeometryChanged() { }
        });
        panelLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(), baseFlags(), PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        panelLp.y = 0;
        wm.addView(panel, panelLp);

        island.applyState(false);
    }

    /** Keep the window's flags and offset in step with the island's state. */
    private void refreshIslandWindow() {
        if (island == null || islandLp == null) return;
        int flags = baseFlags();
        if (store.isExpanded()) {
            // Dim everything behind the expanded view, the way iOS does.
            flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            islandLp.dimAmount = 0.45f;
        } else {
            islandLp.dimAmount = 0f;
        }
        islandLp.flags = flags;
        islandLp.y = Math.round(island.islandTopPx());
        try { wm.updateViewLayout(island, islandLp); } catch (Exception ignored) { }
    }

    private void refreshPanelWindow(boolean open) {
        if (panel == null || panelLp == null) return;
        int flags = baseFlags();
        if (open) {
            flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            panelLp.dimAmount = 0.35f;
        } else {
            panelLp.dimAmount = 0f;
        }
        panelLp.flags = flags;
        try { wm.updateViewLayout(panel, panelLp); } catch (Exception ignored) { }
    }

    /** Tapping the island opens whatever posted it. */
    private void openContent(Presentation p) {
        store.setExpanded(false);
        if (p == null || p.contentIntent == null) return;
        try {
            p.contentIntent.send();
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "content intent cancelled");
        }
    }

    @Override
    public void onIslandChanged(boolean animate) {
        if (island == null) return;
        main.post(() -> {
            island.applyState(animate);
            refreshIslandWindow();
        });
    }

    /* ── Foreground notification ─────────────────────────────────────── */

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    getString(R.string.channel_theme), NotificationManager.IMPORTANCE_MIN);
            ch.setDescription(getString(R.string.channel_theme_desc));
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, IslandService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        Notification n = b
                .setSmallIcon(R.drawable.ic_island)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text,
                        MemoryBudget.mb(memory.getBudgetBytes())))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_island),
                        getString(R.string.turn_off), stop).build())
                .setOngoing(true)
                .setShowWhen(false)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    /* ── Teardown ────────────────────────────────────────────────────── */

    @Override
    public void onDestroy() {
        running = false;
        main.removeCallbacks(ballastTick);
        store.setListener(null);
        if (system != null) system.stop();
        removeView(island);
        removeView(panel);
        island = null;
        panel = null;
        // Give every byte back the moment the theme is switched off.
        memory.release();
        super.onDestroy();
    }

    private void removeView(View v) {
        if (v == null) return;
        try { wm.removeViewImmediate(v); } catch (Exception ignored) { }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
