package com.dcp.punch.data;

import android.app.Notification;
import android.content.ComponentName;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import com.dcp.punch.DcpApp;

/**
 * Real notifications become island presentations.
 *
 * This is what separates a theme from a mock-up: the pill shows the track that
 * is actually playing, the call that is actually ringing, the timer that is
 * actually counting down. Everything here is read-only — nothing is dismissed,
 * modified or forwarded anywhere.
 *
 * Icons come from the notification's own small icon, which means the app never
 * needs QUERY_ALL_PACKAGES to enumerate what is installed.
 */
public class DcpNotificationListener extends NotificationListenerService {

    private static boolean connected;

    private MediaMonitor media;

    public static boolean isConnected() { return connected; }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        IslandStore store = DcpApp.get().store();
        media = new MediaMonitor(this, store, new ComponentName(this, DcpNotificationListener.class));
        media.start();

        // Catch up on whatever was already posted before we connected.
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) handle(sbn, false);
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        if (media != null) { media.stop(); media = null; }
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn, true);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        DcpApp.get().store().dismiss(key(sbn));
    }

    /* ── Classification ──────────────────────────────────────────────── */

    private void handle(StatusBarNotification sbn, boolean isNew) {
        if (sbn == null) return;
        Notification n = sbn.getNotification();
        if (n == null) return;

        // Never react to our own ongoing service notification.
        if (getPackageName().equals(sbn.getPackageName())) return;

        // Group summaries duplicate their children; media notifications are
        // already covered, and far better, by MediaMonitor.
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return;
        if (n.extras != null && n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return;

        String title = text(n, Notification.EXTRA_TITLE);
        String body = text(n, Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(body)) return;

        String cat = n.category == null ? "" : n.category;
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;

        Presentation p = new Presentation(key(sbn),
                (ongoing || isActivityCategory(cat))
                        ? Presentation.Kind.ACTIVITY
                        : Presentation.Kind.ALERT);

        p.title = TextUtils.isEmpty(title) ? body : title;
        p.subtitle = TextUtils.isEmpty(title) ? "" : body;
        p.compactText = shorten(p.title);
        p.icon = smallIcon(n);
        p.accent = accentFor(cat, n);
        p.contentIntent = n.contentIntent;
        p.durationMs = 3200L;

        switch (cat) {
            case Notification.CATEGORY_CALL:
                p.motif = Presentation.Motif.PULSE;
                p.accent = 0xFF30D158;
                p.animating = true;
                break;
            case Notification.CATEGORY_ALARM:
            case Notification.CATEGORY_STOPWATCH:
                p.motif = Presentation.Motif.RING;
                p.accent = 0xFFFF9F0A;
                break;
            case Notification.CATEGORY_NAVIGATION:
                p.accent = 0xFF30D158;
                break;
            case Notification.CATEGORY_PROGRESS:
                p.progress = progressOf(n);
                p.motif = Presentation.Motif.RING;
                break;
            default:
                break;
        }

        // Notification actions become the expanded view's buttons, capped at
        // three so the row stays legible.
        if (n.actions != null && n.actions.length > 0) {
            int count = Math.min(3, n.actions.length);
            Presentation.Action[] acts = new Presentation.Action[count];
            for (int i = 0; i < count; i++) {
                final Notification.Action a = n.actions[i];
                acts[i] = new Presentation.Action(
                        a.title == null ? "Action" : a.title.toString(),
                        0,
                        () -> {
                            try { if (a.actionIntent != null) a.actionIntent.send(); }
                            catch (Exception ignored) { }
                        });
            }
            p.actions = acts;
            p.tapAction = acts[0];
        }

        DcpApp.get().store().present(p);
    }

    private static boolean isActivityCategory(String cat) {
        return Notification.CATEGORY_CALL.equals(cat)
                || Notification.CATEGORY_NAVIGATION.equals(cat)
                || Notification.CATEGORY_ALARM.equals(cat)
                || Notification.CATEGORY_STOPWATCH.equals(cat)
                || Notification.CATEGORY_PROGRESS.equals(cat);
    }

    private static float progressOf(Notification n) {
        if (n.extras == null) return -1f;
        int max = n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
        int cur = n.extras.getInt(Notification.EXTRA_PROGRESS, 0);
        return max > 0 ? Math.max(0f, Math.min(1f, cur / (float) max)) : -1f;
    }

    private Drawable smallIcon(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && n.getSmallIcon() != null) {
                return n.getSmallIcon().loadDrawable(this);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static int accentFor(String cat, Notification n) {
        if (n.color != 0 && (n.color & 0xFF000000) != 0) return n.color;
        return 0xFF0A84FF;
    }

    private static String text(Notification n, String key) {
        if (n.extras == null) return "";
        CharSequence cs = n.extras.getCharSequence(key);
        return cs == null ? "" : cs.toString().trim();
    }

    /** The pill has room for a few words, not a paragraph. */
    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() <= 28 ? s : s.substring(0, 27).trim() + "…";
    }

    private static String key(StatusBarNotification sbn) {
        return "n:" + sbn.getKey();
    }
}
