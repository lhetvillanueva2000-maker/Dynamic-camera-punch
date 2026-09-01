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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
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
 *   Settings  your name, what the device has, and everything else
 *
 * All three panes are inflated once and cross-faded, because a tab switch that
 * re-inflates is a stutter on exactly the low-end hardware this is meant to run
 * well on. The panes are small enough that keeping all three costs less than
 * rebuilding one.
 */
public class MainActivity extends Activity {

    private static final int REQ_POST_NOTIFS = 10;

    /* Cards tab */
    private Switch themeSwitch;
    private TextView themeStatus, functionsDesc, appsDesc;
    private LinearLayout sourcesList, appsList, gesturesList;
    private View rowOverlay, rowListener, rowPost;

    /* Island tab */
    private IslandPreview preview;
    private TextView variantA, variantB, variantDesc;
    private Switch reduceAnimSwitch, glowSwitch;
    private LinearLayout dialsGeometry, dialsSurface, dialsMotion, dialsNotif,
            colourRow, notifModeRow, presetsList;
    private EditText presetName;

    /* Settings tab */
    private Switch bootSwitch, alwaysSwitch, autoSwitch, hideSwitch;
    private TextView alwaysDesc, hideDesc, ramDetail, footerBy,
            capacityButton, capacityBody, capacityNote, demoDesc;
    private MemoryGaugeView ramGauge;
    private LinearLayout deviceStats, capacityRoot, capacityPanel;
    private EditText userName;
    private Button demoButton;

    private View[] panes;
    private TabBar tabBar;

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

        findViews();
        wireTabs();
        wireCards();
        wireLook();
        wireSettings();

        buildDials();
        buildGestureRows();
        buildColourSwatches();
        buildNotifModeRow();
    }

    private void findViews() {
        panes = new View[]{
                findViewById(R.id.pane_cards),
                findViewById(R.id.pane_look),
                findViewById(R.id.pane_settings)
        };
        tabBar = findViewById(R.id.tab_bar);

        themeSwitch = findViewById(R.id.theme_switch);
        themeStatus = findViewById(R.id.theme_status);
        functionsDesc = findViewById(R.id.functions_desc);
        appsDesc = findViewById(R.id.apps_desc);
        sourcesList = findViewById(R.id.sources_list);
        appsList = findViewById(R.id.apps_list);
        gesturesList = findViewById(R.id.gestures_list);
        rowOverlay = findViewById(R.id.row_overlay);
        rowListener = findViewById(R.id.row_listener);
        rowPost = findViewById(R.id.row_post);

        preview = findViewById(R.id.island_preview);
        variantA = findViewById(R.id.variant_a);
        variantB = findViewById(R.id.variant_b);
        variantDesc = findViewById(R.id.variant_desc);
        reduceAnimSwitch = findViewById(R.id.reduce_anim_switch);
        glowSwitch = findViewById(R.id.glow_switch);
        dialsGeometry = findViewById(R.id.dials_geometry);
        dialsSurface = findViewById(R.id.dials_surface);
        dialsMotion = findViewById(R.id.dials_motion);
        dialsNotif = findViewById(R.id.dials_notif);
        colourRow = findViewById(R.id.colour_row);
        notifModeRow = findViewById(R.id.notif_mode_row);
        presetsList = findViewById(R.id.presets_list);
        presetName = findViewById(R.id.preset_name);

        bootSwitch = findViewById(R.id.boot_switch);
        alwaysSwitch = findViewById(R.id.always_switch);
        autoSwitch = findViewById(R.id.auto_switch);
        hideSwitch = findViewById(R.id.hide_switch);
        alwaysDesc = findViewById(R.id.always_desc);
        hideDesc = findViewById(R.id.hide_desc);
        ramDetail = findViewById(R.id.ram_detail);
        ramGauge = findViewById(R.id.ram_gauge);
        deviceStats = findViewById(R.id.device_stats);
        userName = findViewById(R.id.user_name);
        footerBy = findViewById(R.id.footer_by);
        demoDesc = findViewById(R.id.demo_desc);
        demoButton = findViewById(R.id.demo_button);
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
        tabBar.select(TabBar.TAB_CARDS, false);
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
        boolean showCapacity = index == TabBar.TAB_SETTINGS;
        capacityRoot.animate().cancel();
        if (showCapacity) {
            capacityRoot.setVisibility(View.VISIBLE);
            capacityRoot.setAlpha(0f);
            capacityRoot.animate().alpha(1f).setDuration(220).start();
        } else {
            capacityRoot.animate().alpha(0f).setDuration(140)
                    .withEndAction(() -> capacityRoot.setVisibility(View.GONE)).start();
        }

        if (index == TabBar.TAB_LOOK) preview.refresh();
    }

    /* ══════════════════════════════════════════════════════════════════
       CARDS TAB
       ═════════════════════════════════════════════════════════════════ */

    private void wireCards() {
        ((TextView) findViewById(R.id.tagline)).setText(
                getString(R.string.tagline) + "  ·  v" + BuildConfig.VERSION_NAME);

        themeSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            if (checked) enableTheme(); else disableTheme();
        });

        findViewById(R.id.apps_add).setOnClickListener(v -> openPicker(PickerActivity.MODE_APPS));
        findViewById(R.id.functions_add).setOnClickListener(
                v -> openPicker(PickerActivity.MODE_FUNCTIONS));

        wirePermissionRow(rowOverlay, R.string.perm_overlay, R.string.perm_overlay_why,
                v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));

        wirePermissionRow(rowListener, R.string.perm_notifications, R.string.perm_notifications_why,
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        wirePermissionRow(rowPost, R.string.perm_post, R.string.perm_post_why, v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFS);
            }
        });
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

    private void buildFunctionRows() {
        sourcesList.removeAllViews();
        boolean unrestricted = sources.functionsUnrestricted();
        functionsDesc.setText(unrestricted ? R.string.functions_all : R.string.functions_some);
        if (unrestricted) return;

        for (Sources.Source s : SOURCE_ORDER) {
            if (!sources.allowedFunctionKeys().contains(s.key)) continue;
            sourcesList.addView(pickedRow(getString(labelResFor(s)), null, () -> {
                sources.removeFunction(s);
                DcpApp.get().store().clear();
                bind();
            }));
        }
    }

    private void buildAppRows() {
        appsList.removeAllViews();
        boolean unrestricted = sources.appsUnrestricted();
        appsDesc.setText(unrestricted ? R.string.apps_all : R.string.apps_some);
        if (unrestricted) return;

        for (String pkg : sources.allowedApps()) {
            final String p = pkg;
            appsList.addView(pickedRow(appLabel(p), appIcon(p), () -> {
                sources.removeApp(p);
                DcpApp.get().store().clear();
                bind();
            }));
        }
    }

    /** One row per gesture, opening a chooser for the action it performs. */
    private void buildGestureRows() {
        gesturesList.removeAllViews();
        for (Gestures.Gesture g : Gestures.Gesture.values()) {
            gesturesList.addView(gestureRow(g));
        }
    }

    private View gestureRow(Gestures.Gesture g) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Math.round(9 * d), 0, Math.round(9 * d));

        TextView name = new TextView(this);
        name.setText(gestureLabel(g));
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(14.5f);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = new TextView(this);
        action.setTextSize(13f);
        action.setTextColor(getColor(R.color.accent));
        action.setBackgroundResource(R.drawable.bg_pill);
        action.setPadding(Math.round(13 * d), Math.round(7 * d),
                Math.round(13 * d), Math.round(7 * d));
        action.setText(actionLabel(gestures.actionFor(g)));
        row.addView(action);

        View.OnClickListener choose = v -> pickAction(g, action);
        row.setOnClickListener(choose);
        action.setOnClickListener(choose);
        return row;
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

    private void wireLook() {
        variantA.setOnClickListener(v -> setVariant("a"));
        variantB.setOnClickListener(v -> setVariant("b"));

        reduceAnimSwitch.setOnCheckedChangeListener((b, c) -> {
            if (binding) return;
            look.setFlag(Appearance.REDUCE_ANIM, c);
            afterLookChange();
        });
        glowSwitch.setOnCheckedChangeListener((b, c) -> {
            if (binding) return;
            look.setFlag(Appearance.GLOW, c);
            afterLookChange();
        });

        findViewById(R.id.preset_save).setOnClickListener(v -> {
            String name = presetName.getText().toString().trim();
            if (name.isEmpty()) { presetName.requestFocus(); return; }
            look.savePreset(name);
            presetName.setText("");
            Toast.makeText(this, R.string.presets_saved, Toast.LENGTH_SHORT).show();
            buildPresetRows();
        });

        findViewById(R.id.look_reset).setOnClickListener(v -> {
            look.resetAll();
            bind();
            buildDials();
            afterLookChange();
        });
    }

    /** Every dial on the Island tab, grouped as the card layout expects. */
    private void buildDials() {
        dialsGeometry.removeAllViews();
        dialsSurface.removeAllViews();
        dialsMotion.removeAllViews();
        dialsNotif.removeAllViews();

        addDial(dialsGeometry, Appearance.OFFSET_X, R.string.d_offset_x);
        addDial(dialsGeometry, Appearance.OFFSET_Y, R.string.d_offset_y);
        addDial(dialsGeometry, Appearance.IDLE_SCALE, R.string.d_idle_scale);
        addDial(dialsGeometry, Appearance.CORNER_RADIUS, R.string.d_corner_radius);
        addDial(dialsGeometry, Appearance.EXP_RADIUS, R.string.d_exp_radius);
        addDial(dialsGeometry, Appearance.EXP_WIDTH, R.string.d_exp_width);

        addDial(dialsSurface, Appearance.COLLAPSED_ALPHA, R.string.d_collapsed_alpha);
        addDial(dialsSurface, Appearance.BORDER_WIDTH, R.string.d_border_width);
        addDial(dialsSurface, Appearance.SHADOW_ALPHA, R.string.d_shadow_alpha);

        addDial(dialsMotion, Appearance.ANIM_SPEED, R.string.d_anim_speed);
        addDial(dialsMotion, Appearance.BOUNCE, R.string.d_bounce);

        addDial(dialsNotif, Appearance.AUTO_HIDE, R.string.d_auto_hide);
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

    private void buildNotifModeRow() {
        notifModeRow.removeAllViews();
        notifModeRow.addView(modeChip(getString(R.string.notif_mode_0), 0));
        notifModeRow.addView(modeChip(getString(R.string.notif_mode_1), 1));
    }

    private View modeChip(String label, int mode) {
        float d = getResources().getDisplayMetrics().density;
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(14f);
        chip.setTextColor(getColor(R.color.text));
        chip.setPadding(0, Math.round(12 * d), 0, Math.round(12 * d));
        chip.setBackgroundResource(look.get(Appearance.NOTIF_MODE) == mode
                ? R.drawable.bg_pill_active : R.drawable.bg_pill);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (mode == 0) lp.rightMargin = Math.round(8 * d);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            look.set(Appearance.NOTIF_MODE, mode);
            buildNotifModeRow();
            afterLookChange();
        });
        return chip;
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
            presetsList.addView(pickedRow(n, null, () -> {
                look.deletePreset(n);
                buildPresetRows();
            }, () -> {
                if (look.applyPreset(n)) {
                    bind();
                    buildDials();
                    buildColourSwatches();
                    buildNotifModeRow();
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
            variantA.postDelayed(() -> IslandService.start(this), 220);
        }
        bind();
        preview.refresh();
    }

    /* ══════════════════════════════════════════════════════════════════
       SETTINGS TAB
       ═════════════════════════════════════════════════════════════════ */

    private void wireSettings() {
        bootSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (!binding) prefs.setStartOnBoot(checked);
        });

        alwaysSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            prefs.setAlwaysVisible(checked);
            IslandService.refresh(this);
            bind();
        });

        autoSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            memory.setAutoManage(checked);
            bind();
        });

        hideSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            prefs.setHideFromShade(checked);
            bind();
        });

        ramGauge.bind(memory, bytes -> {
            memory.setBudgetBytes(bytes);
            bind();
        });

        userName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                prefs.setUserName(s.toString());
                paintFooter();
            }
        });

        findViewById(R.id.device_refresh).setOnClickListener(v -> buildDeviceStats());

        findViewById(R.id.support_button).setOnClickListener(v ->
                startActivity(new Intent(this, SupportActivity.class)));

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

        demoButton.setOnClickListener(v -> {
            if (IslandService.isRunning()) {
                Toast.makeText(this, R.string.demo_blocked, Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, DemoActivity.class));
        });
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
        buildFunctionRows();
        buildAppRows();
        buildPresetRows();
        buildDeviceStats();
    }

    private void bind() {
        binding = true;

        boolean running = IslandService.isRunning();
        themeSwitch.setChecked(running);
        themeStatus.setText(running ? R.string.theme_on : R.string.theme_off);

        setPermissionState(rowOverlay, DcpApp.canDrawOverlay(this));
        setPermissionState(rowListener, DcpApp.hasNotificationAccess(this));
        setPermissionState(rowPost, hasPostNotifications());

        String variant = prefs.getVariant();
        variantA.setBackgroundResource("a".equals(variant)
                ? R.drawable.bg_pill_active : R.drawable.bg_pill);
        variantB.setBackgroundResource("b".equals(variant)
                ? R.drawable.bg_pill_active : R.drawable.bg_pill);
        variantDesc.setText("a".equals(variant) ? R.string.variant_a_desc : R.string.variant_b_desc);

        reduceAnimSwitch.setChecked(look.flag(Appearance.REDUCE_ANIM));
        glowSwitch.setChecked(look.flag(Appearance.GLOW));

        bootSwitch.setChecked(prefs.isStartOnBoot());
        alwaysSwitch.setChecked(prefs.isAlwaysVisible());
        alwaysDesc.setText(prefs.isAlwaysVisible() ? R.string.always_on : R.string.always_off);
        autoSwitch.setChecked(memory.isAutoManage());
        hideSwitch.setChecked(prefs.isHideFromShade());
        hideDesc.setText(prefs.isHideFromShade() ? R.string.hide_on : R.string.hide_off);

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

        demoButton.setEnabled(!running);
        demoButton.setAlpha(running ? 0.45f : 1f);
        demoDesc.setText(running ? R.string.demo_blocked : R.string.demo_desc);

        binding = false;
    }

    /* ══════════════════════════════════════════════════════════════════
       SHARED ROW HELPERS
       ═════════════════════════════════════════════════════════════════ */

    private View pickedRow(String label, Drawable icon, Runnable onRemove) {
        return pickedRow(label, icon, onRemove, null);
    }

    /**
     * A chosen entry: optional icon, label, and a Remove control. When onTap is
     * given the row itself does something else (apply a preset) and only the
     * Remove chip deletes — otherwise tapping anywhere removes, which is what
     * the app and function lists want.
     */
    private View pickedRow(String label, Drawable icon, Runnable onRemove, Runnable onTap) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Math.round(7 * d), 0, Math.round(7 * d));

        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            int px = Math.round(28 * d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
            lp.rightMargin = Math.round(10 * d);
            row.addView(iv, lp);
        }

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

        row.setOnClickListener(v -> { if (onTap != null) onTap.run(); else onRemove.run(); });
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

    private Drawable appIcon(String pkg) {
        try { return getPackageManager().getApplicationIcon(pkg); }
        catch (Exception e) { return null; }
    }

    private void wirePermissionRow(View row, int nameRes, int whyRes, View.OnClickListener onGrant) {
        ((TextView) row.findViewById(R.id.perm_name)).setText(nameRes);
        ((TextView) row.findViewById(R.id.perm_why)).setText(whyRes);
        row.findViewById(R.id.perm_action).setOnClickListener(onGrant);
    }

    private void setPermissionState(View row, boolean granted) {
        ImageView tick = row.findViewById(R.id.perm_tick);
        TextView action = row.findViewById(R.id.perm_action);
        tick.setImageResource(granted ? R.drawable.ic_check : R.drawable.ic_chevron);
        tick.setColorFilter(getColor(granted ? R.color.green : R.color.text_faint));
        action.setText(granted ? R.string.granted : R.string.grant);
        action.setAlpha(granted ? 0.5f : 1f);
        action.setEnabled(!granted);
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
            binding = true;
            themeSwitch.setChecked(false);
            binding = false;
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
        // Back steps through the tabs before it leaves, which is what a
        // three-tab app is expected to do.
        if (tabBar.getSelected() != TabBar.TAB_CARDS) {
            tabBar.select(TabBar.TAB_CARDS, true);
            ((ScrollView) panes[TabBar.TAB_CARDS]).smoothScrollTo(0, 0);
            return;
        }
        super.onBackPressed();
    }
}
