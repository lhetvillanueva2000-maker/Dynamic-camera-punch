package com.dcp.punch.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.dcp.punch.mem.Appearance;

import java.util.ArrayList;
import java.util.List;

/**
 * The island's state, with no rendering in it.
 *
 * Same model as the web build: a transient alert layered over a stack of at most
 * two background activities, plus an expanded flag. Sources push presentations
 * in; the view observes and draws.
 */
public class IslandStore {

    public interface Listener {
        /** State changed enough to need a re-layout / re-draw. */
        void onIslandChanged(boolean animate);
    }

    public static final int MAX_ACTIVITIES = 2;

    private final List<Presentation> activities = new ArrayList<>();
    private Presentation alert;
    private boolean expanded;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;

    /**
     * Application context, only ever used to read settings. Held because the
     * store outlives every activity and service that touches it; an activity
     * context here would be a leak.
     */
    private Context ctx;

    public void attach(Context c) {
        if (ctx == null && c != null) ctx = c.getApplicationContext();
    }

    /** True when the user asked for arriving notifications to open themselves. */
    private boolean arriveExpanded() {
        return ctx != null && Appearance.get(ctx).get(Appearance.NOTIF_MODE) == 1;
    }

    /** Sweeps expired alerts. Only runs while an alert is actually up. */
    private final Runnable expiryTick = new Runnable() {
        @Override public void run() {
            if (alert != null && alert.isExpired(System.currentTimeMillis(), expanded)) {
                alert = null;
                notifyChanged(true);
            }
            if (alert != null) main.postDelayed(this, 200L);
        }
    };

    public void setListener(Listener l) { this.listener = l; }

    /* ── Reads ───────────────────────────────────────────────────────── */

    /** What is on the pill right now. */
    public Presentation current() {
        if (alert != null) return alert;
        return activities.isEmpty() ? null : activities.get(0);
    }

    /** The activity riding in the detached minimal indicator, if any. */
    public Presentation secondary() {
        if (alert != null || activities.size() < 2) return null;
        return activities.get(1);
    }

    public boolean isExpanded() { return expanded; }
    public boolean isEmpty() { return alert == null && activities.isEmpty(); }
    public int activityCount() { return activities.size(); }

    /* ── Writes ──────────────────────────────────────────────────────── */

    /** Show a presentation. Alerts interrupt; activities stack (newest first). */
    public void present(Presentation p) {
        if (p == null) return;

        if (p.kind == Presentation.Kind.ALERT) {
            alert = p;
            expanded = arriveExpanded();
            main.removeCallbacks(expiryTick);
            main.postDelayed(expiryTick, 200L);
            notifyChanged(true);
            return;
        }

        int existing = indexOf(p.id);
        if (existing >= 0) {
            // Update in place: a track change should not re-trigger the morph
            // or bounce the activity to the front of the stack.
            boolean wasPrimary = existing == 0;
            activities.set(existing, p);
            notifyChanged(!wasPrimary);
            return;
        }

        activities.add(0, p);
        while (activities.size() > MAX_ACTIVITIES) {
            activities.remove(activities.size() - 1);
        }
        notifyChanged(true);
    }

    /** Remove by id. Silently ignores ids that are not present. */
    public void dismiss(String id) {
        boolean changed = false;
        if (alert != null && alert.id.equals(id)) { alert = null; changed = true; }
        int i = indexOf(id);
        if (i >= 0) { activities.remove(i); changed = true; }
        if (isEmpty()) expanded = false;
        if (changed) notifyChanged(true);
    }

    /** Remove whatever is on the pill right now. */
    public void dismissCurrent() {
        Presentation p = current();
        if (p != null) dismiss(p.id);
    }

    public void clear() {
        alert = null;
        activities.clear();
        expanded = false;
        main.removeCallbacks(expiryTick);
        notifyChanged(true);
    }

    public void setExpanded(boolean v) {
        if (expanded == v || (v && current() == null)) return;
        expanded = v;
        // Holding the island open pauses an alert's countdown, so reset its
        // clock on collapse rather than letting it vanish the instant it closes.
        if (!v && alert != null) alert.createdAt = System.currentTimeMillis();
        notifyChanged(true);
    }

    public void toggleExpanded() { setExpanded(!expanded); }

    /** Promote the minimal indicator to the pill. */
    public void swap() {
        if (alert != null || activities.size() < 2) return;
        activities.add(activities.remove(0));
        notifyChanged(true);
    }

    /**
     * Give back what can be given back.
     *
     * Artwork is the only thing here with real weight — a decoded avatar or album
     * bitmap — and only the presentation actually on the pill needs its own. On a
     * hard trim (the system itself is short) even that goes; the renderer falls
     * back to the notification's small icon, which is a drawable the platform
     * already holds, and the bitmap is re-supplied on the next post.
     */
    public void trim(boolean hard) {
        Presentation visible = hard ? null : current();
        if (alert != null && alert != visible) alert.art = null;
        for (int i = 0; i < activities.size(); i++) {
            Presentation p = activities.get(i);
            if (p != visible) p.art = null;
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < activities.size(); i++) {
            if (activities.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private void notifyChanged(boolean animate) {
        if (listener != null) listener.onIslandChanged(animate);
    }
}
