package com.dcp.punch.data;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import com.dcp.punch.R;

import java.util.List;

/**
 * Turns whatever is playing on the device into a live island activity.
 *
 * Uses MediaSessionManager rather than scraping the media notification: the
 * session gives real metadata, real transport controls and real playback
 * position, so the island's play/pause button actually pauses Spotify instead
 * of firing a notification action and hoping.
 *
 * getActiveSessions() is gated on MEDIA_CONTENT_CONTROL, which is signature
 * level — but an enabled NotificationListenerService is granted the same access,
 * which is why this class is driven from DcpNotificationListener and needs no
 * extra permission of its own.
 */
public class MediaMonitor {

    private static final String ID = "media";

    private final Context ctx;
    private final IslandStore store;
    private final MediaSessionManager msm;
    private final ComponentName listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaController controller;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            this::onSessions;

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { publish(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { publish(); }
        @Override public void onSessionDestroyed() { detach(); store.dismiss(ID); }
    };

    public MediaMonitor(Context ctx, IslandStore store, ComponentName listener) {
        this.ctx = ctx.getApplicationContext();
        this.store = store;
        this.listener = listener;
        this.msm = (MediaSessionManager) this.ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public void start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listener, main);
            onSessions(msm.getActiveSessions(listener));
        } catch (SecurityException e) {
            // Notification access was revoked between the check and the call.
        }
    }

    public void stop() {
        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener);
        } catch (Exception ignored) { }
        detach();
    }

    /** Pick the session that is actually playing, else the first one offered. */
    private void onSessions(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) {
            detach();
            store.dismiss(ID);
            return;
        }
        MediaController pick = controllers.get(0);
        for (MediaController c : controllers) {
            PlaybackState st = c.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) { pick = c; break; }
        }
        if (controller != null && controller.getSessionToken().equals(pick.getSessionToken())) {
            publish();
            return;
        }
        detach();
        controller = pick;
        controller.registerCallback(controllerCallback, main);
        publish();
    }

    private void detach() {
        if (controller != null) {
            controller.unregisterCallback(controllerCallback);
            controller = null;
        }
    }

    private void publish() {
        if (controller == null) { store.dismiss(ID); return; }
        if (!Sources.get(ctx).isEnabled(Sources.Source.MEDIA)) { store.dismiss(ID); return; }

        MediaMetadata md = controller.getMetadata();
        PlaybackState st = controller.getPlaybackState();
        int state = st == null ? PlaybackState.STATE_NONE : st.getState();

        // Stopped or gone means the activity is over; paused stays on the
        // island, the way a paused track stays on iOS.
        if (state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) {
            store.dismiss(ID);
            return;
        }

        boolean playing = state == PlaybackState.STATE_PLAYING;
        Presentation p = new Presentation(ID, Presentation.Kind.ACTIVITY);
        p.accent = 0xFFFA233B;
        p.motif = playing ? Presentation.Motif.EQUALISER : Presentation.Motif.NONE;
        p.animating = playing;

        if (md != null) {
            p.title = str(md, MediaMetadata.METADATA_KEY_TITLE, "Playing");
            p.subtitle = str(md, MediaMetadata.METADATA_KEY_ARTIST,
                    str(md, MediaMetadata.METADATA_KEY_ALBUM, ""));
            p.art = art(md);
            long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (dur > 0 && st != null) {
                long pos = st.getPosition();
                if (state == PlaybackState.STATE_PLAYING) {
                    // getPosition() is a snapshot; extrapolate so the bar moves
                    // between callbacks instead of stepping once per event.
                    pos += (long) ((android.os.SystemClock.elapsedRealtime()
                            - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
                }
                p.progress = Math.max(0f, Math.min(1f, pos / (float) dur));
                p.trackPositionMs = Math.max(0, Math.min(dur, pos));
                p.trackDurationMs = dur;
            }
        } else {
            p.title = "Playing";
        }
        p.compactText = "";
        p.contentIntent = controller.getSessionActivity();

        MediaController.TransportControls tc = controller.getTransportControls();
        p.tapAction = new Presentation.Action(playing ? "Pause" : "Play",
                playing ? R.drawable.ic_pause : R.drawable.ic_play,
                () -> { if (playing) tc.pause(); else tc.play(); });
        // Always three, always in this order.
        //
        // A session declares which transports it supports, and it is tempting to
        // build the row from that — but then the first track of a queue drops
        // "previous", the row becomes two buttons, and everything shifts. That
        // is the "the back button is missing" report: a transport row that
        // changes shape reads as broken rather than as unavailable. So the row
        // is fixed and the declared actions only decide what is drawn dimmed.
        long acts = st == null ? 0L : st.getActions();
        Presentation.Action prev =
                new Presentation.Action("Previous", R.drawable.ic_prev, tc::skipToPrevious);
        Presentation.Action next =
                new Presentation.Action("Next", R.drawable.ic_next, tc::skipToNext);
        prev.enabled = supports(acts, PlaybackState.ACTION_SKIP_TO_PREVIOUS);
        next.enabled = supports(acts, PlaybackState.ACTION_SKIP_TO_NEXT);

        p.actions = new Presentation.Action[]{ prev, p.tapAction, next };

        store.present(p);
    }

    /**
     * Whether the session declares a transport.
     *
     * Sessions that declare nothing at all (actions == 0) are common — plenty
     * of players never populate the field — so "declared nothing" is treated as
     * "everything works", not as "nothing works". Greying out a control that
     * would in fact have worked is the worse failure.
     */
    private static boolean supports(long actions, long flag) {
        return actions == 0L || (actions & flag) != 0L;
    }

    private static String str(MediaMetadata md, String key, String fallback) {
        CharSequence cs = md.getText(key);
        return cs == null || cs.length() == 0 ? fallback : cs.toString();
    }

    /** Album art, preferring the smaller icon fields to keep bitmaps cheap. */
    private static Bitmap art(MediaMetadata md) {
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        return b;
    }
}
