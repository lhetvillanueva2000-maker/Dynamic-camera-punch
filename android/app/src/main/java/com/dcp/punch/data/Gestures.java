package com.dcp.punch.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What each gesture on the island does.
 *
 * Five gestures, one action each, and every pairing is the user's to choose.
 * The defaults reproduce what the island did before this existed, so an install
 * that never opens this screen behaves exactly as it always has.
 *
 * Actions are stored by name rather than ordinal. An enum's ordinal shifts the
 * moment someone inserts a value in the middle, and a settings file that
 * silently remaps "open the app" to "play/pause" after an update is a bug
 * nobody would think to look for.
 */
public final class Gestures {

    public enum Gesture {
        TAP("tap"),
        DOUBLE_TAP("double_tap"),
        LONG_PRESS("long_press"),
        SWIPE_LEFT("swipe_left"),
        SWIPE_RIGHT("swipe_right");

        public final String key;
        Gesture(String key) { this.key = key; }
    }

    public enum Action {
        NOTHING,
        OPEN_APP,          // the notification's own content intent
        EXPAND,            // grow into the detail view
        COLLAPSE,
        DISMISS,           // send the current presentation away
        SWAP,              // promote the second activity
        PLAY_PAUSE,
        NEXT_TRACK,
        PREVIOUS_TRACK;

        public static Action byName(String n, Action fallback) {
            if (n == null) return fallback;
            for (Action a : values()) if (a.name().equals(n)) return a;
            return fallback;
        }
    }

    private static final String FILE = "dcp_gestures";

    private static Gestures instance;
    private final SharedPreferences sp;

    private Gestures(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Gestures get(Context c) {
        if (instance == null) instance = new Gestures(c);
        return instance;
    }

    /** The behaviour the island shipped with, before any of this was settable. */
    public static Action defaultFor(Gesture g) {
        switch (g) {
            case TAP:         return Action.OPEN_APP;
            case DOUBLE_TAP:  return Action.PLAY_PAUSE;
            case LONG_PRESS:  return Action.EXPAND;
            case SWIPE_LEFT:  return Action.SWAP;
            case SWIPE_RIGHT: return Action.SWAP;
            default:          return Action.NOTHING;
        }
    }

    public Action actionFor(Gesture g) {
        return Action.byName(sp.getString(g.key, null), defaultFor(g));
    }

    public void setAction(Gesture g, Action a) {
        sp.edit().putString(g.key, a.name()).apply();
    }

    public void resetAll() {
        SharedPreferences.Editor e = sp.edit();
        for (Gesture g : Gesture.values()) e.remove(g.key);
        e.apply();
    }
}
