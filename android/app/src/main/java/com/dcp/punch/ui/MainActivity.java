package com.dcp.punch.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.dcp.punch.BuildConfig;
import com.dcp.punch.DcpApp;
import com.dcp.punch.R;
import com.dcp.punch.data.Gestures;
import com.dcp.punch.data.Sources;
import com.dcp.punch.mem.Appearance;
import com.dcp.punch.mem.DeviceStats;
import com.dcp.punch.mem.MemoryBudget;
import com.dcp.punch.mem.Prefs;
import com.dcp.punch.overlay.IslandService;

import java.util.List;

/**
 * The control panel: three tabs, one activity.
 *
 *   Cards     what the island is allowed to show, and what your gestures do
 *   Island    how it looks and moves, with a live preview and named presets
 *   Settings  you, this device, and everything else
 *
 * Two things shape this file.
 *
 * The panes are inflated once and cross-faded rather than swapped, because a
 * tab switch that re-inflates is a stutter on exactly the low-end hardware this
 * is meant to run well on. Keeping all three costs less than rebuilding one.
 *
 * And the rows are built in code from {@link Rows} rather than declared in
 * layout XML. Twelve screens sharing six shapes is what makes the app look like
 * one app; building them from one vocabulary is what stops that agreement from
 * having to be maintained by hand across a dozen files.
 */
public class MainActivity extends Activity {

    private static final int REQ_POST_NOTIFS = 10;

    /* Cards tab — built in code, so only its host and its live bits are held. */
    private LinearLayout cardsBody;
    private CheckSwitch themeSwitch;
    private TextView themeStatus;
    private TextView permOverlayState, permListenerState, permPostState;

    /* Island tab */
    private LinearLayout lookBody, variantCard, colourRow, presetsList;
    private IslandPreview preview;
    private CheckSwitch reduceAnimSwitch, glowSwitch;

    /* Settings tab */
    private LinearLayout settingsBody, deviceStats, capacityRoot, capacityPanel;
    private CheckSwitch bootSwitch, alwaysSwitch, autoSwitch;
    private TextView alwaysDesc, ramDetail, footerBy, demoDesc, demoState,
            capacityButton, capacityBody, capacityNote;
    private MemoryGaugeView ramGauge;
    private EditText userName;

    private View[] panes;
    private FloatingTabBar tabBar;
    private FrameLayout screenHost;
    private Rows rows;

    /** The detail screen currently on top, if any. Back closes it first. */
    private SubScreen openScreen;
    /** How to rebuild whatever is on top, for when its data changed underneath. */
    private Runnable reopenScreen;

    private Prefs prefs;
    private Sources sources;
    private Appearance look;
    private Gestures gestures;
    private MemoryBudget memory;
    private DeviceStats device;

    /** Set while programmatically flipping controls, so listeners stay quiet. */
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = Prefs.get(this);
        sources = Sources.get(this);
        look = Appearance.get(this);
        gestures = Gestures.get(this);
        memory = DcpApp.get().memory();
        device = new DeviceStats(this);

        rows = new Rows(this);

        findViews();
        wireTabs();
        buildCardsTab();
        buildLookTab();
        buildSettingsTab();
    }

    private void findViews() {
        panes = new View[]{
                findViewById(R.id.pane_cards),
                findViewById(R.id.pane_look),
                findViewById(R.id.pane_settings)
        };
        tabBar = findViewById(R.id.tab_bar);
        screenHost = findViewById(R.id.screen_host);
        cardsBody = findViewById(R.id.cards_body);
        lookBody = findViewById(R.id.look_body);
        settingsBody = findViewById(R.id.settings_body);

        capacityRoot = findViewById(R.id.capacity_root);
        capacityPanel = findViewById(R.id.capacity_panel);
        capacityButton = findViewById(R.id.capacity_button);
        capacityBody = findViewById(R.id.capacity_body);
        capacityNote = findViewById(R.id.capacity_note);
    }

    /* ══════════════════════════════════════════════════════════════════
       TABS
       ═════════════════════════════════════════════════════════════════ */

    private void wireTabs() {
        for (int i = 1; i < panes.length; i++) {
            panes[i].setAlpha(0f);
            panes[i].setVisibility(View.GONE);
        }
        tabBar.setOnTabSelected(this::showPane);
        tabBar.select(FloatingTabBar.TAB_CARDS, false);
    }

    /**
     * Cross-fade to a pane, with the incoming one drifting up a few dp.
     *
     * The outgoing pane is left GONE rather than INVISIBLE so it stops being
     * measured, and its scroll position is preserved because the view itself is
     * kept — coming back to a tab lands where you left it.
     */
    private void showPane(int index) {
        for (int i = 0; i < panes.length; i++) {
            final View pane = panes[i];
            boolean wanted = i == index;

            if (wanted) {
                if (pane.getVisibility() != View.VISIBLE) {
                    pane.setVisibility(View.VISIBLE);
                    pane.setTranslationY(14f * getResources().getDisplayMetrics().density);
                }
                pane.animate().alpha(1f).translationY(0f).setDuration(260).start();
            } else if (pane.getVisibility() == View.VISIBLE) {
                pane.animate().alpha(0f).setDuration(160)
                        .withEndAction(() -> pane.setVisibility(View.GONE)).start();
            }
        }

        // Capacity describes the memory ceiling, which lives on Settings.
        boolean showCapacity = index == FloatingTabBar.TAB_SETTINGS;
        capacityRoot.animate().cancel();
        if (showCapacity) {
            capacityRoot.setVisibility(View.VISIBLE);
            capacityRoot.setAlpha(0f);
            capacityRoot.animate().alpha(1f).setDuration(220).start();
        } else {
            capacityRoot.animate().alpha(0f).setDuration(140)
                    .withEndAction(() -> capacityRoot.setVisibility(View.GONE)).start();
        }

        if (index == FloatingTabBar.TAB_LOOK) preview.refresh();
    }

    /* ══════════════════════════════════════════════════════════════════
       CARDS TAB
       ═════════════════════════════════════════════════════════════════ */

    /**
     * The front door: a header, the master switch, setup, then one summary row
     * per category.
     *
     * It used to be every switch in the app on one scroll — forty of them, in
     * the order they happened to be written. Now the top level says what the
     * island can do and each row opens the screen that decides the detail, which
     * is the difference between a settings page you read and one you search.
     */
    private void buildCardsTab() {
        cardsBody.removeAllViews();

        buildAppHeader();
        buildMasterSwitch();
        buildSetupCard();

        rows.section(cardsBody, getString(R.string.cat_events), getString(R.string.guide),
                () -> guide(R.string.cat_events, R.string.guide_events));
        rows.chips(cardsBody,
                new int[]{ Glyphs.BOLT, Glyphs.BATTERY_LOW, Glyphs.VIBRATE, Glyphs.HEADPHONES },
                Rows.BLUE, this::openEventsScreen);

        rows.section(cardsBody, getString(R.string.cat_cards), getString(R.string.guide),
                () -> guide(R.string.cat_cards, R.string.guide_cards));
        rows.chips(cardsBody,
                new int[]{ Glyphs.PLAY, Glyphs.PHONE, Glyphs.TIMER, Glyphs.PROGRESS },
                Rows.PINK, this::openLiveCardsScreen);

        rows.section(cardsBody, getString(R.string.cat_gestures), getString(R.string.guide),
                () -> guide(R.string.cat_gestures, R.string.guide_gestures));
        rows.chips(cardsBody,
                new int[]{ Glyphs.TAP, Glyphs.DOUBLE_TAP, Glyphs.LONG_PRESS, Glyphs.SWIPE_LEFT },
                Rows.BLUE, this::openGesturesScreen);

        rows.section(cardsBody, getString(R.string.cat_notifications), getString(R.string.guide),
                () -> guide(R.string.cat_notifications, R.string.guide_notifications));
        rows.nav(rows.card(cardsBody), Glyphs.BELL, Rows.PINK,
                getString(R.string.cat_notifications),
                getString(R.string.cat_notifications_desc),
                this::openNotificationsScreen);
    }

    private void buildAppHeader() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(rows.px(6), rows.px(22), rows.px(6), rows.px(6));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setContentDescription(null);
        head.addView(icon, new LinearLayout.LayoutParams(rows.px(42), rows.px(42)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(R.string.app_name);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(20f);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        text.addView(name);

        TextView sub = new TextView(this);
        sub.setText(getString(R.string.tagline_version,
                getString(R.string.tagline), BuildConfig.VERSION_NAME));
        sub.setTextColor(getColor(R.color.text_dim));
        sub.setTextSize(12.5f);
        text.addView(sub);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = rows.px(12);
        head.addView(text, lp);

        cardsBody.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildMasterSwitch() {
        LinearLayout card = rows.card(cardsBody);
        themeSwitch = rows.toggle(card, Glyphs.ISLAND, Rows.BLUE,
                getString(R.string.theme_title), getString(R.string.theme_off),
                IslandService.isRunning(),
                (v, checked) -> {
                    if (binding) return;
                    if (checked) enableTheme(); else disableTheme();
                });
        // The subtitle of that row doubles as the live status line.
        themeStatus = subtitleOf(card.getChildAt(0));
    }

    private void buildSetupCard() {
        rows.section(cardsBody, getString(R.string.setup_title));
        LinearLayout card = rows.card(cardsBody);

        permOverlayState = rows.value(card, Glyphs.ISLAND, Rows.PLAIN,
                getString(R.string.perm_overlay), getString(R.string.perm_overlay_why),
                getString(R.string.grant),
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));

        permListenerState = rows.value(card, Glyphs.BELL, Rows.PLAIN,
                getString(R.string.perm_notifications), getString(R.string.perm_notifications_why),
                getString(R.string.grant),
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        permPostState = rows.value(card, Glyphs.MESSAGE, Rows.PLAIN,
                getString(R.string.perm_post), getString(R.string.perm_post_why),
                getString(R.string.grant),
                () -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(
                                new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFS);
                    }
                });
    }

    /** The second TextView inside a row's text column — its subtitle. */
    private TextView subtitleOf(View row) {
        LinearLayout text = (LinearLayout) ((LinearLayout) row).getChildAt(1);
        return (TextView) text.getChildAt(1);
    }

    /** A category's explanation, as a dialog rather than a wall of body text. */
    private void guide(int titleRes, int bodyRes) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(titleRes)
                .setMessage(bodyRes)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /* ── The detail screens ──────────────────────────────────────────── */

    /**
     * Show a detail screen. {@code rebuild} is how to draw it again from
     * scratch, kept so a screen whose data changed while the picker was in
     * front can be refreshed on the way back rather than showing a stale list.
     */
    private void push(SubScreen screen, Runnable rebuild) {
        boolean replacing = openScreen != null;
        if (replacing) openScreen.dismiss();
        openScreen = screen;
        reopenScreen = rebuild;
        screen.present(screenHost, () -> {
            if (openScreen == screen) { openScreen = null; reopenScreen = null; }
        });
    }

    private static final Sources.Source[] EVENT_SOURCES = {
            Sources.Source.CHARGING, Sources.Source.BATTERY,
            Sources.Source.RINGER, Sources.Source.HEADPHONES
    };

    private static final Sources.Source[] CARD_SOURCES = {
            Sources.Source.MESSAGES, Sources.Source.MEDIA, Sources.Source.CALLS,
            Sources.Source.TIMERS, Sources.Source.NAVIGATION,
            Sources.Source.PROGRESS, Sources.Source.OTHER
    };

    private void openEventsScreen() {
        push(new SubScreen(this, getString(R.string.cat_events), getString(R.string.guide),
                () -> guide(R.string.cat_events, R.string.guide_events),
                (body, r) -> {
                    r.note(body, getString(R.string.events_note));
                    for (Sources.Source s : EVENT_SOURCES) {
                        sourceToggle(r, r.card(body), s, Rows.BLUE);
                    }
                }), this::openEventsScreen);
    }

    private void openLiveCardsScreen() {
        push(new SubScreen(this, getString(R.string.cat_cards), getString(R.string.guide),
                () -> guide(R.string.cat_cards, R.string.guide_cards),
                (body, r) -> {
                    r.note(body, getString(R.string.cards_note));
                    for (Sources.Source s : CARD_SOURCES) {
                        sourceToggle(r, r.card(body), s, Rows.PINK);
                    }
                }), this::openLiveCardsScreen);
    }

    private void sourceToggle(Rows r, ViewGroup card, Sources.Source s, int tint) {
        r.toggle(card, glyphFor(s), tint, getString(labelResFor(s)), getString(descResFor(s)),
                sources.isEnabled(s), (v, on) -> setSourceEnabled(s, on));
    }

    /**
     * Turn one kind of content on or off.
     *
     * The stored list is an allow-list where EMPTY MEANS EVERYTHING, so the
     * first thing switched off cannot simply be removed — the list would still
     * be empty and the switch would spring back on. Switching one off while the
     * list is empty therefore writes every other kind in first, which is the
     * same state the user was already looking at, and only then removes this
     * one.
     */
    private void setSourceEnabled(Sources.Source s, boolean on) {
        if (on) {
            sources.addFunction(s);
        } else {
            if (sources.functionsUnrestricted()) {
                for (Sources.Source every : SOURCE_ORDER) sources.addFunction(every);
            }
            sources.removeFunction(s);
        }
        DcpApp.get().store().clear();
    }

    private void openGesturesScreen() {
        push(new SubScreen(this, getString(R.string.cat_gestures), getString(R.string.guide),
                () -> guide(R.string.cat_gestures, R.string.guide_gestures),
                (body, r) -> {
                    r.note(body, getString(R.string.gestures_desc));
                    for (Gestures.Gesture g : Gestures.Gesture.values()) {
                        LinearLayout card = r.card(body);
                        final TextView[] holder = new TextView[1];
                        holder[0] = r.value(card, glyphFor(g), Rows.BLUE,
                                getString(gestureLabel(g)), getString(gestureHint(g)),
                                getString(actionLabel(gestures.actionFor(g))),
                                () -> pickAction(g, holder[0]));
                    }
                    r.caption(body, getString(R.string.gestures_stored_note));
                }), this::openGesturesScreen);
    }

    private void openNotificationsScreen() {
        push(new SubScreen(this, getString(R.string.cat_notifications), getString(R.string.guide),
                () -> guide(R.string.cat_notifications, R.string.guide_notifications),
                (body, r) -> {
                    r.section(body, getString(R.string.notif_settings));
                    LinearLayout settings = r.card(body);

                    final TextView[] mode = new TextView[1];
                    mode[0] = r.value(settings, Glyphs.CARDS, Rows.PINK,
                            getString(R.string.notif_mode_title),
                            getString(R.string.notif_mode_desc),
                            getString(look.get(Appearance.NOTIF_MODE) == 1
                                    ? R.string.notif_mode_1 : R.string.notif_mode_0),
                            () -> pickNotifMode(mode[0]));

                    final TextView[] hide = new TextView[1];
                    hide[0] = r.value(settings, Glyphs.TIMER, Rows.PINK,
                            getString(R.string.d_auto_hide),
                            getString(R.string.auto_hide_desc),
                            autoHideLabel(look.get(Appearance.AUTO_HIDE)),
                            () -> pickAutoHide(hide[0]));

                    r.toggle(settings, Glyphs.BELL, Rows.PINK,
                            getString(R.string.hide_title), getString(R.string.hide_short),
                            prefs.isHideFromShade(),
                            (v, on) -> { prefs.setHideFromShade(on); bind(); });

                    r.caption(body, getString(R.string.hide_limits));

                    r.section(body, getString(R.string.apps_title), getString(R.string.add_plus),
                            () -> openPicker(PickerActivity.MODE_APPS));

                    if (sources.appsUnrestricted()) {
                        r.note(body, getString(R.string.apps_all));
                    } else {
                        for (String pkg : sources.allowedApps()) {
                            final String p = pkg;
                            LinearLayout card = r.card(body);
                            r.value(card, Glyphs.APPS, Rows.PLAIN, appLabel(p), p,
                                    getString(R.string.remove), () -> {
                                        sources.removeApp(p);
                                        DcpApp.get().store().clear();
                                        openNotificationsScreen();   // rebuild in place
                                    });
                        }
                    }
                }), this::openNotificationsScreen);
    }

    private String autoHideLabel(int seconds) {
        return seconds <= 0 ? getString(R.string.auto_hide_never)
                            : getString(R.string.auto_hide_seconds, seconds);
    }

    private static final int[] AUTO_HIDE_CHOICES = { 0, 2, 3, 5, 8, 10, 15, 20, 30 };

    private void pickAutoHide(TextView label) {
        CharSequence[] names = new CharSequence[AUTO_HIDE_CHOICES.length];
        int current = 0;
        for (int i = 0; i < AUTO_HIDE_CHOICES.length; i++) {
            names[i] = autoHideLabel(AUTO_HIDE_CHOICES[i]);
            if (AUTO_HIDE_CHOICES[i] == look.get(Appearance.AUTO_HIDE)) current = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.d_auto_hide)
                .setSingleChoiceItems(names, current, (dialog, which) -> {
                    look.set(Appearance.AUTO_HIDE, AUTO_HIDE_CHOICES[which]);
                    label.setText(autoHideLabel(AUTO_HIDE_CHOICES[which]));
                    afterLookChange();
                    dialog.dismiss();
                })
                .show();
    }

    private void pickNotifMode(TextView label) {
        CharSequence[] names = {
                getString(R.string.notif_mode_0), getString(R.string.notif_mode_1)
        };
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.notif_mode_title)
                .setSingleChoiceItems(names, look.get(Appearance.NOTIF_MODE), (dialog, which) -> {
                    look.set(Appearance.NOTIF_MODE, which);
                    label.setText(names[which]);
                    afterLookChange();
                    dialog.dismiss();
                })
                .show();
    }

    static final Sources.Source[] SOURCE_ORDER = {
            Sources.Source.MESSAGES, Sources.Source.MEDIA, Sources.Source.CALLS,
            Sources.Source.TIMERS, Sources.Source.NAVIGATION, Sources.Source.PROGRESS,
            Sources.Source.OTHER, Sources.Source.CHARGING, Sources.Source.BATTERY,
            Sources.Source.RINGER, Sources.Source.HEADPHONES
    };

    static int labelResFor(Sources.Source s) {
        switch (s) {
            case MESSAGES:   return R.string.src_messages;
            case MEDIA:      return R.string.src_media;
            case CALLS:      return R.string.src_calls;
            case TIMERS:     return R.string.src_timers;
            case NAVIGATION: return R.string.src_navigation;
            case PROGRESS:   return R.string.src_progress;
            case CHARGING:   return R.string.src_charging;
            case BATTERY:    return R.string.src_battery;
            case RINGER:     return R.string.src_ringer;
            case HEADPHONES: return R.string.src_headphones;
            default:         return R.string.src_other;
        }
    }

    private void openPicker(String mode) {
        startActivity(new Intent(this, PickerActivity.class)
                .putExtra(PickerActivity.EXTRA_MODE, mode));
    }

    /** The icon that stands for one kind of content, everywhere it appears. */
    static int glyphFor(Sources.Source s) {
        switch (s) {
            case MESSAGES:   return Glyphs.MESSAGE;
            case MEDIA:      return Glyphs.PLAY;
            case CALLS:      return Glyphs.PHONE;
            case TIMERS:     return Glyphs.TIMER;
            case NAVIGATION: return Glyphs.NAVIGATION;
            case PROGRESS:   return Glyphs.PROGRESS;
            case CHARGING:   return Glyphs.BOLT;
            case BATTERY:    return Glyphs.BATTERY_LOW;
            case RINGER:     return Glyphs.VIBRATE;
            case HEADPHONES: return Glyphs.HEADPHONES;
            default:         return Glyphs.BELL;
        }
    }

    static int descResFor(Sources.Source s) {
        switch (s) {
            case MESSAGES:   return R.string.src_messages_desc;
            case MEDIA:      return R.string.src_media_desc;
            case CALLS:      return R.string.src_calls_desc;
            case TIMERS:     return R.string.src_timers_desc;
            case NAVIGATION: return R.string.src_navigation_desc;
            case PROGRESS:   return R.string.src_progress_desc;
            case CHARGING:   return R.string.src_charging_desc;
            case BATTERY:    return R.string.src_battery_desc;
            case RINGER:     return R.string.src_ringer_desc;
            case HEADPHONES: return R.string.src_headphones_desc;
            default:         return R.string.src_other_desc;
        }
    }

    static int glyphFor(Gestures.Gesture g) {
        switch (g) {
            case TAP:        return Glyphs.TAP;
            case DOUBLE_TAP: return Glyphs.DOUBLE_TAP;
            case LONG_PRESS: return Glyphs.LONG_PRESS;
            case SWIPE_LEFT: return Glyphs.SWIPE_LEFT;
            default:         return Glyphs.SWIPE_RIGHT;
        }
    }

    static int gestureHint(Gestures.Gesture g) {
        switch (g) {
            case TAP:        return R.string.g_tap_hint;
            case DOUBLE_TAP: return R.string.g_double_tap_hint;
            case LONG_PRESS: return R.string.g_long_press_hint;
            case SWIPE_LEFT: return R.string.g_swipe_left_hint;
            default:         return R.string.g_swipe_right_hint;
        }
    }

    private void pickAction(Gestures.Gesture g, TextView label) {
        Gestures.Action[] all = Gestures.Action.values();
        CharSequence[] names = new CharSequence[all.length];
        int current = 0;
        for (int i = 0; i < all.length; i++) {
            names[i] = getString(actionLabel(all[i]));
            if (all[i] == gestures.actionFor(g)) current = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(gestureLabel(g))
                .setSingleChoiceItems(names, current, (dialog, which) -> {
                    gestures.setAction(g, all[which]);
                    label.setText(actionLabel(all[which]));
                    dialog.dismiss();
                })
                .show();
    }

    private static int gestureLabel(Gestures.Gesture g) {
        switch (g) {
            case TAP:         return R.string.g_tap;
            case DOUBLE_TAP:  return R.string.g_double_tap;
            case LONG_PRESS:  return R.string.g_long_press;
            case SWIPE_LEFT:  return R.string.g_swipe_left;
            default:          return R.string.g_swipe_right;
        }
    }

    private static int actionLabel(Gestures.Action a) {
        switch (a) {
            case OPEN_APP:       return R.string.ga_open_app;
            case EXPAND:         return R.string.ga_expand;
            case COLLAPSE:       return R.string.ga_collapse;
            case DISMISS:        return R.string.ga_dismiss;
            case SWAP:           return R.string.ga_swap;
            case PLAY_PAUSE:     return R.string.ga_play_pause;
            case NEXT_TRACK:     return R.string.ga_next;
            case PREVIOUS_TRACK: return R.string.ga_previous;
            default:             return R.string.ga_nothing;
        }
    }

    /* ══════════════════════════════════════════════════════════════════
       ISLAND TAB
       ═════════════════════════════════════════════════════════════════ */

    /**
     * Tab two, built in the same vocabulary as the other two: the live preview,
     * then the shape, the surface, the motion, and your saved presets.
     */
    private void buildLookTab() {
        lookBody.removeAllViews();

        title(lookBody, getString(R.string.look_title), getString(R.string.look_sub));

        // The preview sits on its own card, unpadded, because it draws its own
        // margins and a second set would shrink the island it is showing.
        LinearLayout previewCard = rows.card(lookBody);
        preview = new IslandPreview(this);
        previewCard.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rows.px(190)));

        rows.section(lookBody, getString(R.string.variant_title));
        variantCard = rows.card(lookBody);
        variantRow(variantCard, "a", Glyphs.PHONE_PORTRAIT,
                R.string.variant_a, R.string.variant_a_desc);
        variantRow(variantCard, "b", Glyphs.PHONE_LANDSCAPE,
                R.string.variant_b, R.string.variant_b_desc);

        rows.section(lookBody, getString(R.string.look_geometry));
        LinearLayout geometry = rows.card(lookBody);
        addDial(geometry, Appearance.OFFSET_X, R.string.d_offset_x);
        addDial(geometry, Appearance.OFFSET_Y, R.string.d_offset_y);
        addDial(geometry, Appearance.IDLE_SCALE, R.string.d_idle_scale);
        addDial(geometry, Appearance.CORNER_RADIUS, R.string.d_corner_radius);
        addDial(geometry, Appearance.EXP_RADIUS, R.string.d_exp_radius);
        addDial(geometry, Appearance.EXP_WIDTH, R.string.d_exp_width);

        rows.section(lookBody, getString(R.string.look_surface));
        LinearLayout surface = rows.card(lookBody);
        addDial(surface, Appearance.COLLAPSED_ALPHA, R.string.d_collapsed_alpha);
        addDial(surface, Appearance.BORDER_WIDTH, R.string.d_border_width);
        addDial(surface, Appearance.SHADOW_ALPHA, R.string.d_shadow_alpha);
        colourRow = new LinearLayout(this);
        colourRow.setOrientation(LinearLayout.VERTICAL);
        colourRow.setPadding(rows.px(16), 0, rows.px(16), rows.px(6));
        surface.addView(colourRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rows.section(lookBody, getString(R.string.look_motion), getString(R.string.reset),
                () -> {
                    look.resetAll();
                    buildLookTab();
                    bind();
                    afterLookChange();
                });
        LinearLayout motion = rows.card(lookBody);
        addDial(motion, Appearance.ANIM_SPEED, R.string.d_anim_speed);
        addDial(motion, Appearance.BOUNCE, R.string.d_bounce);
        reduceAnimSwitch = rows.toggle(motion, Glyphs.BOUNCE, Rows.BLUE,
                getString(R.string.reduce_anim_title), getString(R.string.reduce_anim_desc),
                look.flag(Appearance.REDUCE_ANIM), (v, c) -> {
                    if (binding) return;
                    look.setFlag(Appearance.REDUCE_ANIM, c);
                    afterLookChange();
                });
        glowSwitch = rows.toggle(motion, Glyphs.SPARKLE, Rows.BLUE,
                getString(R.string.glow_title), getString(R.string.glow_desc),
                look.flag(Appearance.GLOW), (v, c) -> {
                    if (binding) return;
                    look.setFlag(Appearance.GLOW, c);
                    afterLookChange();
                });

        rows.section(lookBody, getString(R.string.presets_title), getString(R.string.presets_save),
                this::promptSavePreset);
        presetsList = rows.card(lookBody);

        buildColourSwatches();
        buildPresetRows();
    }

    /** One cutout shape, shown as chosen or not. */
    private void variantRow(ViewGroup card, String key, int glyph, int nameRes, int descRes) {
        LinearLayout row = rows.plain(card, glyph, Rows.BLUE,
                getString(nameRes), getString(descRes));
        row.setTag(key);

        CheckSwitch pick = new CheckSwitch(this);
        pick.bind(key.equals(prefs.getVariant()));
        // Choosing one is what deselects the other, so this switch only ever
        // turns on: flipping it off would leave the island with no shape.
        pick.setOnChanged((v, on) -> {
            if (binding) return;
            if (on) setVariant(key); else pick.bind(true);
        });
        row.addView(pick);
        row.setBackground(rows.ripple(0));
        row.setOnClickListener(v -> setVariant(key));
    }

    /** Reflect the chosen shape across both rows. */
    private void bindVariant() {
        if (variantCard == null) return;
        String chosen = prefs.getVariant();
        for (int i = 0; i < variantCard.getChildCount(); i++) {
            View row = variantCard.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof String)) continue;
            View last = ((LinearLayout) row).getChildAt(((LinearLayout) row).getChildCount() - 1);
            if (last instanceof CheckSwitch) ((CheckSwitch) last).bind(tag.equals(chosen));
            row.setAlpha(tag.equals(chosen) ? 1f : 0.62f);
        }
    }

    private void promptSavePreset() {
        final EditText field = new EditText(this);
        field.setHint(R.string.presets_hint);
        field.setTextColor(getColor(R.color.text));
        field.setHintTextColor(getColor(R.color.text_faint));
        field.setSingleLine(true);
        int pad = rows.px(22);
        field.setPadding(pad, rows.px(12), pad, rows.px(12));

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.presets_save)
                .setMessage(R.string.presets_prompt)
                .setView(field)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = field.getText().toString().trim();
                    if (name.isEmpty()) return;
                    look.savePreset(name);
                    buildPresetRows();
                    Toast.makeText(this, R.string.presets_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addDial(ViewGroup into, String key, int titleRes) {
        SliderRow row = new SliderRow(this);
        row.bind(key, getString(titleRes), look.min(key), look.max(key), look.get(key),
                v -> formatDial(key, v),
                new SliderRow.Listener() {
                    @Override public void onSliding(SliderRow r, int value) {
                        // Cheap: write and let the preview pick it up. The
                        // overlay is only told once, on release.
                        look.set(key, value);
                    }
                    @Override public void onCommitted(SliderRow r, int value) {
                        look.set(key, value);
                        afterLookChange();
                    }
                });
        into.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String formatDial(String key, int v) {
        switch (key) {
            case Appearance.OFFSET_X:
            case Appearance.OFFSET_Y:
                return (v > 0 ? "+" : "") + v + " dp";
            case Appearance.CORNER_RADIUS:
            case Appearance.EXP_RADIUS:
            case Appearance.EXP_WIDTH:
            case Appearance.BORDER_WIDTH:
                return v + " dp";
            case Appearance.IDLE_SCALE:
            case Appearance.COLLAPSED_ALPHA:
            case Appearance.SHADOW_ALPHA:
                return v + "%";
            case Appearance.ANIM_SPEED:
                return String.format(java.util.Locale.US, "%.2fx", v / 100.0);
            case Appearance.BOUNCE:
                return getString(v == 0 ? R.string.bounce_0 : v == 1 ? R.string.bounce_1
                        : v == 2 ? R.string.bounce_2 : R.string.bounce_3);
            case Appearance.AUTO_HIDE:
                return v <= 0 ? getString(R.string.autohide_never) : v + " s";
            default:
                return String.valueOf(v);
        }
    }

    /** Background and outline swatches. */
    private static final int[] BG_SWATCHES = {
            0xFF000000, 0xFF0B0B10, 0xFF14141C, 0xFF101820, 0xFF1A1020, 0xFF0A1418
    };
    private static final int[] OUTLINE_SWATCHES = {
            0x00000000, 0x1AFFFFFF, 0x40FFFFFF, 0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A
    };

    private void buildColourSwatches() {
        colourRow.removeAllViews();
        colourRow.setOrientation(LinearLayout.VERTICAL);
        colourRow.addView(swatchGroup(getString(R.string.colour_background),
                BG_SWATCHES, Appearance.BG_COLOR));
        colourRow.addView(swatchGroup(getString(R.string.colour_outline),
                OUTLINE_SWATCHES, Appearance.OUTLINE_COLOR));
    }

    private View swatchGroup(String title, int[] colours, String key) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, 0, 0, Math.round(10 * d));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(getColor(R.color.text_dim));
        label.setTextSize(12.5f);
        group.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Math.round(8 * d), 0, 0);

        for (int colour : colours) {
            final int c = colour;
            View dot = new View(this);
            dot.setBackground(swatchDrawable(c, look.get(key) == c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Math.round(34 * d), Math.round(34 * d));
            lp.rightMargin = Math.round(10 * d);
            dot.setOnClickListener(v -> {
                look.set(key, c);
                // Repaint the whole group so the previous selection loses its ring.
                buildColourSwatches();
                afterLookChange();
            });
            row.addView(dot, lp);
        }
        group.addView(row);
        return group;
    }

    private Drawable swatchDrawable(int colour, boolean selected) {
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        // A fully transparent swatch would be invisible, so "none" reads as a
        // hollow ring rather than a hole in the row.
        g.setColor(Color.alpha(colour) == 0 ? 0x14FFFFFF : colour);
        g.setStroke(Math.round((selected ? 2.5f : 1f) * d),
                selected ? getColor(R.color.accent) : 0x33FFFFFF);
        return g;
    }

    private void buildPresetRows() {
        presetsList.removeAllViews();
        List<String> names = look.presetNames();
        if (names.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.presets_none);
            none.setTextColor(getColor(R.color.text_faint));
            none.setTextSize(12.5f);
            presetsList.addView(none);
            return;
        }
        for (String name : names) {
            final String n = name;
            presetsList.addView(pickedRow(n, () -> {
                look.deletePreset(n);
                buildPresetRows();
            }, () -> {
                if (look.applyPreset(n)) {
                    buildLookTab();
                    bind();
                    afterLookChange();
                    Toast.makeText(this, R.string.presets_applied, Toast.LENGTH_SHORT).show();
                }
            }));
        }
    }

    /** Push the new look to a running overlay and re-run the preview. */
    private void afterLookChange() {
        preview.refresh();
        IslandService.refresh(this);
    }

    private void setVariant(String v) {
        prefs.setVariant(v);
        if (IslandService.isRunning()) {
            IslandService.stop(this);
            tabBar.postDelayed(() -> IslandService.start(this), 220);
        }
        bind();
        preview.refresh();
    }

    /* ══════════════════════════════════════════════════════════════════
       SETTINGS TAB
       ═════════════════════════════════════════════════════════════════ */

    /**
     * Tab three: you, the island's own behaviour, this device, and the extras.
     *
     * The memory gauge and the device stats are custom views dropped into
     * ordinary cards, so they sit in the same rhythm as every other row rather
     * than being a differently-shaped panel bolted on.
     */
    private void buildSettingsTab() {
        settingsBody.removeAllViews();

        title(settingsBody, getString(R.string.settings_title), null);

        rows.section(settingsBody, getString(R.string.you_title));
        LinearLayout you = rows.card(settingsBody);
        LinearLayout nameRow = rows.plain(you, Glyphs.PERSON, Rows.BLUE,
                getString(R.string.name_title), getString(R.string.name_desc));
        userName = new EditText(this);
        userName.setHint(R.string.name_hint);
        userName.setText(prefs.getUserName());
        userName.setTextColor(getColor(R.color.text));
        userName.setHintTextColor(getColor(R.color.text_faint));
        userName.setTextSize(15f);
        userName.setSingleLine(true);
        userName.setBackground(null);
        userName.setGravity(Gravity.END);
        userName.setMinWidth(rows.px(110));
        userName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if (binding) return;
                prefs.setUserName(e.toString());
                paintFooter();
            }
        });
        nameRow.addView(userName);

        rows.section(settingsBody, getString(R.string.behaviour_title));
        LinearLayout behaviour = rows.card(settingsBody);
        alwaysSwitch = rows.toggle(behaviour, Glyphs.ISLAND, Rows.BLUE,
                getString(R.string.always_title), getString(R.string.always_short),
                prefs.isAlwaysVisible(), (v, checked) -> {
                    if (binding) return;
                    prefs.setAlwaysVisible(checked);
                    IslandService.refresh(this);
                    bind();
                });
        alwaysDesc = subtitleOf(behaviour.getChildAt(0));

        bootSwitch = rows.toggle(behaviour, Glyphs.BOLT, Rows.BLUE,
                getString(R.string.boot_title), getString(R.string.boot_desc),
                prefs.isStartOnBoot(), (v, checked) -> {
                    if (!binding) prefs.setStartOnBoot(checked);
                });

        rows.section(settingsBody, getString(R.string.ram_title));
        LinearLayout mem = rows.card(settingsBody);
        ramGauge = new MemoryGaugeView(this);
        ramGauge.bind(memory, bytes -> {
            memory.setBudgetBytes(bytes);
            bind();
        });
        mem.addView(ramGauge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rows.px(200)));

        ramDetail = new TextView(this);
        ramDetail.setTextColor(getColor(R.color.text_dim));
        ramDetail.setTextSize(12.5f);
        ramDetail.setLineSpacing(0f, 1.3f);
        ramDetail.setPadding(rows.px(18), 0, rows.px(18), rows.px(12));
        mem.addView(ramDetail);

        autoSwitch = rows.toggle(mem, Glyphs.MEMORY, Rows.BLUE,
                getString(R.string.auto_title), getString(R.string.auto_desc),
                memory.isAutoManage(), (v, checked) -> {
                    if (binding) return;
                    memory.setAutoManage(checked);
                    bind();
                });
        rows.caption(settingsBody, getString(R.string.ram_honest));

        rows.section(settingsBody, getString(R.string.device_title), getString(R.string.refresh),
                this::buildDeviceStats);
        LinearLayout deviceCard = rows.card(settingsBody);
        deviceStats = new LinearLayout(this);
        deviceStats.setOrientation(LinearLayout.VERTICAL);
        deviceStats.setPadding(rows.px(18), rows.px(16), rows.px(18), rows.px(6));
        deviceCard.addView(deviceStats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rows.caption(settingsBody, getString(R.string.cpu_honest));

        rows.section(settingsBody, getString(R.string.extras_title));
        LinearLayout extras = rows.card(settingsBody);
        demoState = rows.value(extras, Glyphs.APPS, Rows.PINK,
                getString(R.string.demo_title), getString(R.string.demo_desc),
                getString(R.string.demo_open), () -> {
                    if (IslandService.isRunning()) {
                        Toast.makeText(this, R.string.demo_blocked, Toast.LENGTH_LONG).show();
                        return;
                    }
                    startActivity(new Intent(this, DemoActivity.class));
                });
        demoDesc = subtitleOf(extras.getChildAt(0));

        rows.nav(extras, Glyphs.HEART, Rows.PINK,
                getString(R.string.support_title), getString(R.string.support_desc_short),
                () -> startActivity(new Intent(this, SupportActivity.class)));

        rows.nav(extras, Glyphs.SHIELD, Rows.PLAIN,
                getString(R.string.about_title),
                getString(R.string.about_desc, BuildConfig.VERSION_NAME),
                () -> guide(R.string.about_title, R.string.about_body));

        footerBy = rows.caption(settingsBody, getString(R.string.made_by));
        rows.caption(settingsBody, getString(R.string.made_with));

        capacityButton.setOnClickListener(v -> {
            boolean open = capacityPanel.getVisibility() == View.VISIBLE;
            if (open) {
                capacityPanel.animate().alpha(0f).setDuration(140)
                        .withEndAction(() -> capacityPanel.setVisibility(View.GONE)).start();
            } else {
                capacityPanel.setAlpha(0f);
                capacityPanel.setVisibility(View.VISIBLE);
                capacityPanel.animate().alpha(1f).setDuration(200).start();
            }
        });

        buildDeviceStats();
        paintFooter();
    }

    /** A screen's own large title, above its first section. */
    private void title(ViewGroup parent, CharSequence text, CharSequence sub) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(rows.px(6), rows.px(24), rows.px(6), rows.px(2));

        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.text));
        t.setTextSize(24f);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        box.addView(t);

        if (sub != null) {
            TextView s2 = new TextView(this);
            s2.setText(sub);
            s2.setTextColor(getColor(R.color.text_dim));
            s2.setTextSize(13f);
            s2.setLineSpacing(0f, 1.25f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = rows.px(3);
            box.addView(s2, lp);
        }

        parent.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildDeviceStats() {
        deviceStats.removeAllViews();
        float d = getResources().getDisplayMetrics().density;

        long ramTotal = device.ramTotal(), ramUsed = device.ramUsed();
        StatBar ram = new StatBar(this);
        ram.set(getString(R.string.stat_ram),
                DeviceStats.bytes(ramUsed) + " / " + DeviceStats.bytes(ramTotal),
                getString(R.string.stat_free, DeviceStats.bytes(device.ramAvailable())),
                ramTotal > 0 ? ramUsed / (float) ramTotal : -1f);
        deviceStats.addView(ram, rowParams(d));

        long stTotal = device.storageTotal(), stUsed = device.storageUsed();
        StatBar storage = new StatBar(this);
        storage.set(getString(R.string.stat_storage),
                DeviceStats.bytes(stUsed) + " / " + DeviceStats.bytes(stTotal),
                getString(R.string.stat_free, DeviceStats.bytes(device.storageFree())),
                stTotal > 0 ? stUsed / (float) stTotal : -1f);
        deviceStats.addView(storage, rowParams(d));

        StatBar cpu = new StatBar(this);
        cpu.set(getString(R.string.stat_cpu), device.cpuModel(),
                getString(R.string.stat_cores, device.cpuCores(), device.cpuAbi(),
                        DeviceStats.mhz(device.cpuMaxMhz())),
                -1f);
        deviceStats.addView(cpu, rowParams(d));

        StatBar own = new StatBar(this);
        long cpuMs = device.ownCpuMillis();
        own.set(getString(R.string.stat_cpu_own),
                String.format(java.util.Locale.US, "%.1f s", cpuMs / 1000.0),
                String.format(java.util.Locale.US, "%.1f%% of one core since launch",
                        device.ownCpuShare() * 100),
                Math.min(1f, device.ownCpuShare()));
        deviceStats.addView(own, rowParams(d));
    }

    private LinearLayout.LayoutParams rowParams(float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(10 * d);
        return lp;
    }

    private void paintFooter() {
        String name = prefs.getUserName();
        footerBy.setText(TextUtils.isEmpty(name)
                ? getString(R.string.made_by)
                : getString(R.string.made_by_named, name));
    }

    private void bindCapacity() {
        MemoryBudget.Capacity c = memory.capacity();
        capacityButton.setText(getString(R.string.capacity_button_fmt, c.notifications));
        capacityBody.setText(getString(R.string.capacity_body_fmt,
                MemoryBudget.readable(c.headroomBytes),
                c.appsUncounted ? getString(R.string.capacity_apps_uncounted)
                                : String.valueOf(c.apps),
                c.notifications, c.functions));
        capacityNote.setText(c.baseExceedsCeiling
                ? getString(R.string.capacity_tight)
                : getString(R.string.capacity_note));
    }

    /* ══════════════════════════════════════════════════════════════════
       BINDING
       ═════════════════════════════════════════════════════════════════ */

    @Override
    protected void onResume() {
        super.onResume();
        bind();
        buildPresetRows();
        buildDeviceStats();
        // A screen left open while the picker was in front is now stale — the
        // app list it drew may have gained an entry.
        if (reopenScreen != null) reopenScreen.run();
    }

    private void bind() {
        binding = true;

        boolean running = IslandService.isRunning();
        themeSwitch.bind(running);
        themeStatus.setText(running ? R.string.theme_on : R.string.theme_off);

        setPermissionState(permOverlayState, DcpApp.canDrawOverlay(this));
        setPermissionState(permListenerState, DcpApp.hasNotificationAccess(this));
        setPermissionState(permPostState, hasPostNotifications());

        bindVariant();

        reduceAnimSwitch.bind(look.flag(Appearance.REDUCE_ANIM));
        glowSwitch.bind(look.flag(Appearance.GLOW));

        bootSwitch.bind(prefs.isStartOnBoot());
        alwaysSwitch.bind(prefs.isAlwaysVisible());
        alwaysDesc.setText(prefs.isAlwaysVisible() ? R.string.always_on : R.string.always_off);
        autoSwitch.bind(memory.isAutoManage());

        if (!userName.getText().toString().equals(prefs.getUserName())) {
            userName.setText(prefs.getUserName());
        }
        paintFooter();

        memory.sample();
        ramGauge.refresh();
        ramDetail.setText(getString(
                R.string.ram_detail_fmt,
                MemoryBudget.gb(memory.totalDeviceBytes()),
                MemoryBudget.gb(MemoryBudget.OS_RESERVE_BYTES),
                MemoryBudget.readable(memory.maxBudgetBytes()),
                MemoryBudget.mb(memory.actualUsageBytes())));
        bindCapacity();

        demoState.setTextColor(getColor(running ? R.color.text_faint : R.color.accent));
        demoDesc.setText(running ? R.string.demo_blocked : R.string.demo_desc);

        binding = false;
    }

    /* ══════════════════════════════════════════════════════════════════
       SHARED ROW HELPERS
       ═════════════════════════════════════════════════════════════════ */

    /**
     * A saved preset: its name, a Remove control, and a tap that applies it.
     * Only the Remove chip deletes, because a list you apply from must not
     * delete an entry when you reach for it.
     */
    private View pickedRow(String label, Runnable onRemove, Runnable onTap) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Math.round(7 * d), 0, Math.round(7 * d));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(getColor(R.color.text));
        tv.setTextSize(14.5f);
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView rm = new TextView(this);
        rm.setText(R.string.remove);
        rm.setTextSize(12.5f);
        rm.setTextColor(getColor(R.color.text_dim));
        rm.setBackgroundResource(R.drawable.bg_pill);
        rm.setPadding(Math.round(12 * d), Math.round(6 * d),
                Math.round(12 * d), Math.round(6 * d));
        rm.setOnClickListener(v -> onRemove.run());
        row.addView(rm);

        row.setOnClickListener(v -> onTap.run());
        return row;
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) return label.toString();
        } catch (Exception ignored) { }
        return pkg;
    }

    /**
     * "Grant" in the accent, or "Granted" in green and no longer a target.
     * A granted permission that still looks tappable invites a trip to a
     * Settings screen with nothing left to do on it.
     */
    private void setPermissionState(TextView state, boolean granted) {
        state.setText(granted ? R.string.granted : R.string.grant);
        state.setTextColor(getColor(granted ? R.color.green : R.color.accent));
        View row = (View) state.getParent();
        row.setClickable(!granted);
        row.setAlpha(granted ? 0.72f : 1f);
    }

    private boolean hasPostNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /* ══════════════════════════════════════════════════════════════════
       ACTIONS
       ═════════════════════════════════════════════════════════════════ */

    private void enableTheme() {
        if (!DcpApp.canDrawOverlay(this)) {
            themeSwitch.bind(false);
            Toast.makeText(this, R.string.perm_overlay_why, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (!hasPostNotifications() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFS);
        }
        IslandService.start(this);
        themeStatus.setText(R.string.theme_on);
        themeSwitch.postDelayed(this::bind, 400);
    }

    private void disableTheme() {
        prefs.setThemeEnabled(false);
        IslandService.stop(this);
        themeStatus.setText(R.string.theme_off);
        themeSwitch.postDelayed(this::bind, 400);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        bind();
    }

    @Override
    public void onBackPressed() {
        // A detail screen closes first, then the tabs step back to the first
        // one, and only then does back leave the app.
        if (openScreen != null) {
            openScreen.dismiss();
            return;
        }
        if (tabBar.getSelected() != FloatingTabBar.TAB_CARDS) {
            tabBar.select(FloatingTabBar.TAB_CARDS, true);
            ((ScrollView) panes[FloatingTabBar.TAB_CARDS]).smoothScrollTo(0, 0);
            return;
        }
        super.onBackPressed();
    }
}
