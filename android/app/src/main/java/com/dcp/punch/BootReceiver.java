package com.dcp.punch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dcp.punch.mem.Prefs;
import com.dcp.punch.overlay.IslandService;

/**
 * Brings the island back after a restart — but only if the user asked for it
 * and the overlay permission is still granted. Both conditions are re-checked
 * here rather than assumed, because either can change while the phone is off.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Prefs prefs = Prefs.get(context);
        if (!prefs.isStartOnBoot() || !prefs.isThemeEnabled()) return;
        if (!DcpApp.canDrawOverlay(context)) return;

        IslandService.start(context);
    }
}
