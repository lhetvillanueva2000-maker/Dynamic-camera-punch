package com.dcp.punch.data;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

/**
 * One thing the island can show.
 *
 * Deliberately dumb: the renderer reads these fields and nothing else, so a new
 * source of content (a new broadcast, a new notification category) only has to
 * produce one of these. Mirrors the presentation contract in the web build's
 * registry.js.
 */
public class Presentation {

    public enum Kind {
        /** Brief and self-dismissing — charging, ringer, headphones. */
        ALERT,
        /** Persistent and interactive — music, a call, a timer. */
        ACTIVITY
    }

    /** Extra treatment the renderer knows how to draw. */
    public enum Motif {
        NONE,
        EQUALISER,      // animated bars — audio is playing
        WAVEFORM,       // recording
        PULSE,          // a live dot
        RING            // determinate progress ring
    }

    /** Dedupe key. Re-posting the same id updates in place instead of stacking. */
    public String id;
    public Kind kind = Kind.ALERT;
    public Motif motif = Motif.NONE;

    public int accent = 0xFF0A84FF;
    public Drawable icon;               // leading glyph
    public Bitmap art;                  // album / avatar art, optional

    public String title = "";           // expanded headline
    public String subtitle = "";        // expanded second line
    public String compactText = "";     // trailing text on the pill

    /** 0..1, or negative for "no progress to show". */
    public float progress = -1f;

    /** Alerts only: how long before the island hands the screen back. */
    public long durationMs = 3000L;

    /** Wall clock at which this was presented. */
    public long createdAt = System.currentTimeMillis();

    /** Tapping the island opens the source app, when there is one. */
    public PendingIntent contentIntent;

    /** Up to three buttons in the expanded view. */
    public Action[] actions = new Action[0];

    /** Primary action for a plain tap — play/pause, answer, stop. */
    public Action tapAction;

    /** Set when the content is live and the renderer should keep animating. */
    public boolean animating;

    public static class Action {
        public final String label;
        public final int iconRes;       // one of R.drawable.ic_*
        public final Runnable run;

        public Action(String label, int iconRes, Runnable run) {
            this.label = label;
            this.iconRes = iconRes;
            this.run = run;
        }
    }

    public Presentation(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    /** Alerts expire; activities stay until their source goes away. */
    public boolean isExpired(long now, boolean heldOpen) {
        return kind == Kind.ALERT && !heldOpen && now - createdAt > durationMs;
    }
}
