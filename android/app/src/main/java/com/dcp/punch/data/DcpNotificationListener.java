package com.dcp.punch.data;

import android.app.Notification;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
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

        // "App is running" notices. Every foreground service posts one — file
        // sync, a VPN, a launcher, our own overlay — and none of them is an event
        // anybody wants their camera cutout to announce. This is what used to put
        // "Dynamic Camera Punch" on the island and leave it there.
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;

        // Group summaries duplicate their children; media notifications are
        // already covered, and far better, by MediaMonitor.
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return;
        if (n.extras != null && n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return;

        String title = text(n, Notification.EXTRA_TITLE);
        String body = bodyOf(n);
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

        // A message is the case the pill exists for, so it gets its own shape:
        // who it is from on the headline, what they actually said underneath,
        // and the message — not the sender — on the compact pill. Anything
        // carrying MessagingStyle counts, which is every mainstream chat app;
        // CATEGORY_MESSAGE catches the ones that only set a category.
        Msg msg = latestMessage(n);
        if (msg != null || Notification.CATEGORY_MESSAGE.equals(cat)) {
            applyMessage(p, n, msg, title, body);
        }

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
            // Deliberately NOT p.tapAction. A plain tap has to open the app that
            // posted the notification — tapping a message opens the conversation,
            // exactly as it does on iOS. Wiring tap to actions[0] instead made it
            // fire whatever the app happened to list first, which for most chat
            // apps is "Mark as read": the one outcome a tap should never have.
            // The actions are still one tap away, in the expanded view.
        }

        DcpApp.get().store().present(p);
    }

    /* ── Messages ────────────────────────────────────────────────────── */

    /** One line of a conversation, flattened out of whichever style carried it. */
    private static class Msg {
        String sender = "";
        String text = "";
        String conversation = "";
        boolean group;
    }

    /**
     * The newest line of the conversation, or null if this is not a chat.
     *
     * MessagingStyle is why the pill can show what was actually said. The plain
     * EXTRA_TEXT of a chat notification is unreliable — apps put a summary there
     * ("3 new messages"), or the sender's name, or nothing at all — whereas
     * EXTRA_MESSAGES is the structured record of the conversation itself.
     */
    /* Bundle keys inside EXTRA_MESSAGES. MessagingStyle.Message writes these in
       toBundle(); the reader for them, extractMessagingStyleFromNotification, is
       @hide in the platform SDK and public only in AndroidX, which this app does
       not depend on. So the bundle is read directly. Every access below is
       defensive — a malformed entry costs us the message, not the process. */
    private static final String KEY_TEXT = "text";
    private static final String KEY_SENDER = "sender";
    private static final String KEY_SENDER_PERSON = "sender_person";

    private Msg latestMessage(Notification n) {
        if (n.extras == null) return null;
        try {
            android.os.Parcelable[] raw =
                    n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (raw == null || raw.length == 0) return null;

            // Chronological, so the last entry is the one that just arrived and
            // the only one worth the pill.
            android.os.Bundle last = null;
            for (int i = raw.length - 1; i >= 0; i--) {
                if (raw[i] instanceof android.os.Bundle) {
                    android.os.Bundle b = (android.os.Bundle) raw[i];
                    if (!TextUtils.isEmpty(b.getCharSequence(KEY_TEXT))) { last = b; break; }
                }
            }
            if (last == null) return null;

            Msg m = new Msg();
            m.text = last.getCharSequence(KEY_TEXT).toString().trim();
            m.sender = senderOf(last);

            CharSequence conv = n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
            m.conversation = conv == null ? "" : conv.toString().trim();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                m.group = n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION,
                        !TextUtils.isEmpty(m.conversation));
            } else {
                // Before API 28 there is no flag: a conversation title is the
                // only signal that this is a group rather than one person.
                m.group = !TextUtils.isEmpty(m.conversation);
            }
            return m;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String senderOf(android.os.Bundle msg) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Object person = msg.getParcelable(KEY_SENDER_PERSON);
                if (person instanceof android.app.Person) {
                    CharSequence nm = ((android.app.Person) person).getName();
                    if (!TextUtils.isEmpty(nm)) return nm.toString().trim();
                }
            }
        } catch (Exception ignored) { }
        CharSequence legacy = msg.getCharSequence(KEY_SENDER);
        return legacy == null ? "" : legacy.toString().trim();
    }

    /**
     * Shape a chat notification.
     *
     * Group:   "Family GC"  /  "Mum · Did you get the toolbox?"
     * Direct:  "Mum"        /  "Did you get the toolbox?"
     *
     * Either way the compact pill carries the message rather than the name,
     * because the name is already the avatar sitting next to it.
     */
    private void applyMessage(Presentation p, Notification n, Msg m,
                              String fallbackTitle, String fallbackBody) {
        String sender = m == null ? "" : m.sender;
        String said = m == null ? fallbackBody : m.text;
        String conversation = m == null ? "" : m.conversation;

        if (TextUtils.isEmpty(said)) said = fallbackBody;
        if (TextUtils.isEmpty(sender)) sender = fallbackTitle;

        boolean group = m != null && m.group && !TextUtils.isEmpty(conversation);

        if (group) {
            p.title = conversation;
            p.subtitle = TextUtils.isEmpty(sender) ? said : sender + " · " + said;
        } else {
            p.title = TextUtils.isEmpty(sender) ? fallbackTitle : sender;
            p.subtitle = said;
        }

        // The pill is a glance, not an inbox: one sentence, and never a wall of
        // text. The expanded view still has the fuller version.
        p.compactText = shorten(firstSentence(said));
        p.accent = 0xFF0A84FF;
        p.motif = Presentation.Motif.NONE;
        // Long enough to actually read a line before it shrinks back into the
        // cutout; 3.2s is fine for "charging" and far too quick for a sentence.
        p.durationMs = 5200L;

        // The avatar, when the app supplies one. This is the sender's picture in
        // every chat app worth the name, and it is already in the notification —
        // no package querying required.
        Bitmap avatar = largeIcon(n);
        if (avatar != null) p.art = avatar;
    }

    /**
     * Enough of the message to be worth reading, cut at a sentence boundary so a
     * five-paragraph email does not try to fit on a pill.
     *
     * A break is only taken once there is a sentence's worth of text in front of
     * it. Cutting at the first one unconditionally turns "Hey! Are you home
     * yet?" into "Hey!" — grammatically the first sentence and completely
     * useless as a notification.
     */
    private static final int MIN_SENTENCE_CHARS = 16;

    private static String firstSentence(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && s.charAt(i + 1) == ' '
                    && i + 1 >= MIN_SENTENCE_CHARS) {
                return s.substring(0, i + 1);
            }
        }
        return s;
    }

    /** The notification's large icon — the sender's avatar, in a chat app. */
    private Bitmap largeIcon(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && n.getLargeIcon() != null) {
                Drawable dr = n.getLargeIcon().loadDrawable(this);
                if (dr != null) return toBitmap(dr);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static Bitmap toBitmap(Drawable dr) {
        if (dr instanceof BitmapDrawable && ((BitmapDrawable) dr).getBitmap() != null) {
            return ((BitmapDrawable) dr).getBitmap();
        }
        int w = Math.max(1, Math.min(192, dr.getIntrinsicWidth()));
        int h = Math.max(1, Math.min(192, dr.getIntrinsicHeight()));
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        dr.setBounds(0, 0, w, h);
        dr.draw(c);
        return bm;
    }

    /**
     * The best available body text. BigTextStyle and InboxStyle both carry more
     * than EXTRA_TEXT does, and an app that uses them usually leaves EXTRA_TEXT
     * as a truncated teaser.
     */
    private static String bodyOf(Notification n) {
        if (n.extras == null) return "";
        String big = text(n, Notification.EXTRA_BIG_TEXT);
        if (!TextUtils.isEmpty(big)) return big;

        CharSequence[] lines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            CharSequence last = lines[lines.length - 1];
            if (last != null && last.length() > 0) return last.toString().trim();
        }
        return text(n, Notification.EXTRA_TEXT);
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
        return s.length() <= 34 ? s : s.substring(0, 33).trim() + "…";
    }

    private static String key(StatusBarNotification sbn) {
        return "n:" + sbn.getKey();
    }
}
