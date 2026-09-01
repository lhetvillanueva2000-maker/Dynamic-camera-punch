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

import com.dcp.punch.BuildConfig;
import com.dcp.punch.DcpApp;
import com.dcp.punch.R;
import com.dcp.punch.data.IslandStore;
import com.dcp.punch.data.Presentation;
import com.dcp.punch.data.SystemMonitor;
import com.dcp.punch.mem.Appearance;
import com.dcp.punch.mem.MemoryBudget;
import com.dcp.punch.mem.Prefs;
import com.dcp.punch.ui.MainActivity;

/**
 * Owns the overlay for as long as the theme is enabled.
 *
 * Exactly one window, and only while it has something to say. It is
 * TOP|CENTER_HORIZONTAL and re-measured on every animation frame: being exactly
 * island-sized is what lets every other pixel of the screen keep receiving
 * touches, since an overlay that covered the display would break every app
 * underneath it.
 *
 * The window is added when content arrives and taken down again once the island
 * has collapsed back into the cutout — unless "keep on screen" is switched on in
 * the app. There used to be a second overlay as well, a pull tab down the right
 * edge holding the memory dial. Nobody asked for a permanent handle on the side
 * of their screen; the dial now lives in the app, where a control panel belongs.
 *
 * TYPE_APPLICATION_OVERLAY is the only window type a normal app may use over
 * other apps, and only after the user grants "Display over other apps".
 */
public class IslandService extends Service implements IslandStore.Listener {

    private static final String TAG = "dcp.svc";
    private static final String CHANNEL = "dcp_theme";
    private static final int NOTIF_ID = 42;

    /* These three are scoped to the application id rather than hard-coded to
       com.dcp.punch, so the debug build owns its own private IPC surface and can
       sit on the device next to the release build. See AndroidManifest for what
       a shared custom permission name costs. */

    /** Broadcast the demo activity listens for, so it can free its WebView. */
    public static final String ACTION_CLOSE_DEMO = BuildConfig.APPLICATION_ID + ".CLOSE_DEMO";

    /** Signature-level permission gating the above; see AndroidManifest. */
    public static final String PERMISSION_INTERNAL =
            BuildConfig.APPLICATION_ID + ".permission.INTERNAL";

    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".STOP_THEME";

    /** Re-read the settings that change the overlay's shape or presence. */
    public static final String ACTION_REFRESH = BuildConfig.APPLICATION_ID + ".REFRESH";

    private static volatile boolean running;

    private WindowManager wm;
    private IslandView island;
    private WindowManager.LayoutParams islandLp;

    /** Whether the overlay window is currently attached. */
    private boolean islandAttached;

    /** Bumped whenever a close is superseded, so its callback can bow out. */
    private int closeToken;

    /** Held so it can be unregistered — MemoryBudget outlives this service. */
    private MemoryBudget.Trimmable trimmable;

    private IslandStore store;
    private SystemMonitor system;
    private MemoryBudget memory;

    private final Handler main = new Handler(Looper.getMainLooper());

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

    /** Nudge a running service to re-read its display settings. */
    public static void refresh(Context c) {
        if (!isRunning()) return;
        try {
            c.startService(new Intent(c, IslandService.class).setAction(ACTION_REFRESH));
        } catch (Exception ignored) { }
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

        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            // A setting changed in the control panel while we were running —
            // "keep on screen", or any dial on the Island tab. There is no store
            // change to ride on, so it is applied here.
            if (running) main.post(() -> {
                if (island != null) island.applyAppearance();
                refreshIslandWindow();
                syncIslandWindow(true);
            });
            return START_STICKY;
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

        memory.startWatch();

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
        trimmable = hard -> {
            store.trim(hard);
            if (island != null) island.trim(hard);
        };
        memory.register(trimmable);

        islandLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(), baseFlags(), PixelFormat.TRANSLUCENT);
        islandLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        applyOffsets();
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
        // The window is not added here. It goes up when there is something to
        // show and comes down again afterwards — see syncIslandWindow().
        island.resetToIdle();
        syncIslandWindow(false);
    }

    /* ── The island window comes and goes ────────────────────────────── */

    /**
     * Add or remove the overlay so that it exists only when it has a reason to.
     *
     * An overlay window is not free even when it is drawing a small black
     * circle: it is a surface the compositor blends on every frame, and it sits
     * on top of the launcher whether or not anything is happening. With nothing
     * to show and "keep on screen" switched off, the right number of overlay
     * windows is zero.
     */
    private void syncIslandWindow(boolean animate) {
        if (island == null) return;
        boolean want = !store.isEmpty() || Prefs.get(this).isAlwaysVisible();
        if (want) attachIsland(animate);
        else detachIsland();
    }

    private void attachIsland(boolean animate) {
        // Cancels any close still in flight — a notification that lands mid-exit
        // must not be followed by the window being torn down behind it.
        closeToken++;
        if (!islandAttached) {
            island.setAlpha(0f);
            island.resetToIdle();
            try {
                wm.addView(island, islandLp);
                islandAttached = true;
            } catch (Exception e) {
                Log.w(TAG, "could not add the island window: " + e);
                return;
            }
            island.animate().alpha(1f).setDuration(140).start();
            island.applyState(true);      // grow out of the cutout
            return;
        }
        island.setAlpha(1f);
        island.applyState(animate);
        refreshIslandWindow();
    }

    private void detachIsland() {
        if (!islandAttached) return;
        final int token = ++closeToken;
        island.playClose(() -> {
            if (token != closeToken || !islandAttached) return;   // superseded
            removeView(island);
            islandAttached = false;
            island.setAlpha(1f);
            island.resetToIdle();
        });
    }

    /**
     * Position the window from the island's own top offset plus whatever the
     * control panel's nudge dials say.
     *
     * X is a deliberate exception to "the camera never moves": the window is
     * centre-anchored, so a horizontal offset shifts the island *and* its lens
     * together. That is what makes it useful — a cutout that is not perfectly
     * centred on a given phone can be lined up — but it also means the dial
     * should stay at zero unless the hole genuinely is off-centre.
     */
    private void applyOffsets() {
        if (island == null || islandLp == null) return;
        float d = getResources().getDisplayMetrics().density;
        Appearance look = Appearance.get(this);
        islandLp.x = Math.round(look.get(Appearance.OFFSET_X) * d);
        islandLp.y = Math.round(island.islandTopPx() + look.get(Appearance.OFFSET_Y) * d);
    }

    /** Keep the window's flags and offset in step with the island's state. */
    private void refreshIslandWindow() {
        if (island == null || islandLp == null || !islandAttached) return;
        int flags = baseFlags();
        if (store.isExpanded()) {
            // Dim everything behind the expanded view, the way iOS does.
            flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            islandLp.dimAmount = 0.45f;
        } else {
            islandLp.dimAmount = 0f;
        }
        islandLp.flags = flags;
        applyOffsets();
        try { wm.updateViewLayout(island, islandLp); } catch (Exception ignored) { }
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
            syncIslandWindow(animate);
            if (islandAttached) refreshIslandWindow();
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
                        MemoryBudget.mb(memory.sample())))
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
        if (trimmable != null) { memory.unregister(trimmable); trimmable = null; }
        store.setListener(null);
        if (system != null) system.stop();
        if (islandAttached) removeView(island);
        islandAttached = false;
        island = null;
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
