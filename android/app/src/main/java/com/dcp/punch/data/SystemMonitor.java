package com.dcp.punch.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.BatteryManager;

import com.dcp.punch.R;

/**
 * The alerts that need no permission at all — they are broadcasts the system
 * sends to everyone: power connected, battery level, ringer mode, headset.
 *
 * This is why the theme still does something useful before the user grants
 * notification access: plug in a charger and the island reacts.
 */
public class SystemMonitor extends BroadcastReceiver {

    private final Context ctx;
    private final IslandStore store;
    private boolean registered;

    private int lastLevel = -1;
    private int lastRinger = -1;

    public SystemMonitor(Context ctx, IslandStore store) {
        this.ctx = ctx.getApplicationContext();
        this.store = store;
    }

    public void start() {
        if (registered) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(Intent.ACTION_BATTERY_LOW);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_HEADSET_PLUG);
        f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        ctx.registerReceiver(this, f);
        registered = true;

        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) lastRinger = am.getRingerMode();
    }

    public void stop() {
        if (!registered) return;
        try { ctx.unregisterReceiver(this); } catch (Exception ignored) { }
        registered = false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case Intent.ACTION_BATTERY_CHANGED: {
                // Sticky and very chatty — only cache the level, never present.
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) lastLevel = level * 100 / scale;
                break;
            }
            case Intent.ACTION_POWER_CONNECTED: {
                Presentation p = new Presentation("charging", Presentation.Kind.ALERT);
                p.accent = 0xFF30D158;
                p.icon = ctx.getDrawable(R.drawable.ic_bolt);
                p.title = "Charging";
                p.subtitle = batteryLine();
                p.compactText = lastLevel >= 0 ? lastLevel + "%" : "Charging";
                p.progress = lastLevel >= 0 ? lastLevel / 100f : -1f;
                p.durationMs = 3200L;
                store.present(p);
                break;
            }
            case Intent.ACTION_BATTERY_LOW: {
                Presentation p = new Presentation("batterylow", Presentation.Kind.ALERT);
                p.accent = 0xFFFF453A;
                p.icon = ctx.getDrawable(R.drawable.ic_bolt);
                p.title = "Low Battery";
                p.subtitle = batteryLine();
                p.compactText = lastLevel >= 0 ? lastLevel + "%" : "Low";
                p.durationMs = 4000L;
                store.present(p);
                break;
            }
            case Intent.ACTION_HEADSET_PLUG: {
                int plugged = intent.getIntExtra("state", -1);
                if (plugged < 0) break;
                Presentation p = new Presentation("headset", Presentation.Kind.ALERT);
                p.accent = 0xFFFFFFFF;
                p.icon = ctx.getDrawable(R.drawable.ic_headphones);
                p.title = plugged == 1 ? "Headphones Connected" : "Headphones Disconnected";
                p.compactText = plugged == 1 ? "Connected" : "Disconnected";
                p.durationMs = 2600L;
                store.present(p);
                break;
            }
            case AudioManager.RINGER_MODE_CHANGED_ACTION: {
                int mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
                if (mode < 0 || mode == lastRinger) break;
                lastRinger = mode;
                Presentation p = new Presentation("ringer", Presentation.Kind.ALERT);
                boolean silent = mode == AudioManager.RINGER_MODE_SILENT;
                boolean vibrate = mode == AudioManager.RINGER_MODE_VIBRATE;
                p.accent = silent || vibrate ? 0xFFFF9F0A : 0xFF30D158;
                p.icon = ctx.getDrawable(silent || vibrate
                        ? R.drawable.ic_bell_slash : R.drawable.ic_bell);
                p.title = silent ? "Silent" : vibrate ? "Vibrate" : "Ring";
                p.compactText = p.title;
                p.durationMs = 1900L;
                store.present(p);
                break;
            }
            default:
                break;
        }
    }

    private String batteryLine() {
        return lastLevel >= 0 ? lastLevel + "% · tap for details" : "Connected";
    }
}
