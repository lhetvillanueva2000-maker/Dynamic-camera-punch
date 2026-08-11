package com.dcp.punch.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dcp.punch.BuildConfig;
import com.dcp.punch.DcpApp;
import com.dcp.punch.R;
import com.dcp.punch.data.DcpNotificationListener;
import com.dcp.punch.data.Sources;
import com.dcp.punch.mem.MemoryBudget;
import com.dcp.punch.mem.Prefs;
import com.dcp.punch.overlay.IslandService;

/**
 * The control panel: grant what the theme needs, pick the cutout shape, see the
 * memory budget, and open the live demonstration when the theme is off.
 *
 * Every permission row states what it is for in plain language, because
 * "Display over other apps" is exactly the permission a user should be
 * suspicious of, and the honest answer here is a good one.
 */
public class MainActivity extends Activity {

    private static final int REQ_POST_NOTIFS = 10;

    private Switch themeSwitch, bootSwitch, alwaysSwitch, autoSwitch;
    private TextView themeStatus, variantA, variantB, variantDesc, ramDetail, demoDesc, alwaysDesc;
    private MemoryGaugeView ramGauge;
    private Button demoButton;
    private View rowOverlay, rowListener, rowPost;

    private MemoryBudget memory;
    private Prefs prefs;
    private Sources sources;

    private LinearLayout sourcesList, appsList, capacityPanel;
    private TextView appsDesc, functionsDesc, capacityButton, capacityBody, capacityNote;

    /** Set while we programmatically flip the switch, so listeners stay quiet. */
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = Prefs.get(this);
        memory = DcpApp.get().memory();
        sources = Sources.get(this);

        themeSwitch = findViewById(R.id.theme_switch);
        themeStatus = findViewById(R.id.theme_status);
        bootSwitch = findViewById(R.id.boot_switch);
        variantA = findViewById(R.id.variant_a);
        variantB = findViewById(R.id.variant_b);
        variantDesc = findViewById(R.id.variant_desc);
        ramGauge = findViewById(R.id.ram_gauge);
        ramDetail = findViewById(R.id.ram_detail);
        alwaysSwitch = findViewById(R.id.always_switch);
        alwaysDesc = findViewById(R.id.always_desc);
        autoSwitch = findViewById(R.id.auto_switch);
        sourcesList = findViewById(R.id.sources_list);
        appsList = findViewById(R.id.apps_list);
        appsDesc = findViewById(R.id.apps_desc);
        functionsDesc = findViewById(R.id.functions_desc);
        capacityPanel = findViewById(R.id.capacity_panel);
        capacityButton = findViewById(R.id.capacity_button);
        capacityBody = findViewById(R.id.capacity_body);
        capacityNote = findViewById(R.id.capacity_note);

        findViewById(R.id.apps_add).setOnClickListener(v -> openPicker(PickerActivity.MODE_APPS));
        findViewById(R.id.functions_add).setOnClickListener(
                v -> openPicker(PickerActivity.MODE_FUNCTIONS));

        capacityButton.setOnClickListener(v -> {
            boolean open = capacityPanel.getVisibility() == View.VISIBLE;
            capacityPanel.setVisibility(open ? View.GONE : View.VISIBLE);
        });
        demoDesc = findViewById(R.id.demo_desc);
        demoButton = findViewById(R.id.demo_button);
        // Show the version on screen as well as in Settings, so "which build am
        // I running" never needs a filename to answer.
        ((TextView) findViewById(R.id.tagline)).setText(
                getString(R.string.tagline) + "  ·  v" + BuildConfig.VERSION_NAME);

        rowOverlay = findViewById(R.id.row_overlay);
        rowListener = findViewById(R.id.row_listener);
        rowPost = findViewById(R.id.row_post);

        themeSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            if (checked) enableTheme(); else disableTheme();
        });

        bootSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (!binding) prefs.setStartOnBoot(checked);
        });

        alwaysSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            prefs.setAlwaysVisible(checked);
            // Applies immediately: with the theme running and nothing to show,
            // this is the difference between an overlay window and none.
            IslandService.refresh(this);
            bind();
        });

        autoSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            memory.setAutoManage(checked);
            bind();
        });

        // Dragging the needle only commits on release, so the ceiling is not
        // rewritten to preferences once per touch event.
        ramGauge.bind(memory, bytes -> {
            memory.setBudgetBytes(bytes);
            bind();
        });

        variantA.setOnClickListener(v -> setVariant("a"));
        variantB.setOnClickListener(v -> setVariant("b"));

        demoButton.setOnClickListener(v -> {
            if (IslandService.isRunning()) {
                Toast.makeText(this, R.string.demo_blocked, Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, DemoActivity.class));
        });

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

    @Override
    protected void onResume() {
        super.onResume();
        bind();
    }

    /* ── Binding ─────────────────────────────────────────────────────── */

    private void bind() {
        binding = true;

        boolean running = IslandService.isRunning();
        themeSwitch.setChecked(running);
        themeStatus.setText(running ? R.string.theme_on : R.string.theme_off);
        bootSwitch.setChecked(prefs.isStartOnBoot());

        setPermissionState(rowOverlay, DcpApp.canDrawOverlay(this));
        setPermissionState(rowListener, DcpApp.hasNotificationAccess(this));
        setPermissionState(rowPost, hasPostNotifications());

        String variant = prefs.getVariant();
        variantA.setBackgroundResource("a".equals(variant)
                ? R.drawable.bg_pill_active : R.drawable.bg_pill);
        variantB.setBackgroundResource("b".equals(variant)
                ? R.drawable.bg_pill_active : R.drawable.bg_pill);
        variantDesc.setText("a".equals(variant) ? R.string.variant_a_desc : R.string.variant_b_desc);

        buildFunctionRows();
        buildAppRows();
        bindCapacity();

        alwaysSwitch.setChecked(prefs.isAlwaysVisible());
        alwaysDesc.setText(prefs.isAlwaysVisible() ? R.string.always_on : R.string.always_off);
        autoSwitch.setChecked(memory.isAutoManage());

        memory.sample();
        ramGauge.refresh();
        ramDetail.setText(getString(
                R.string.ram_detail_fmt,
                MemoryBudget.gb(memory.totalDeviceBytes()),
                MemoryBudget.gb(MemoryBudget.OS_RESERVE_BYTES),
                MemoryBudget.readable(memory.maxBudgetBytes()),
                MemoryBudget.mb(memory.actualUsageBytes())));

        // The demonstration and the theme are mutually exclusive by design.
        demoButton.setEnabled(!running);
        demoButton.setAlpha(running ? 0.45f : 1f);
        demoDesc.setText(running ? R.string.demo_blocked : R.string.demo_desc);

        binding = false;
    }

    /* ── What the island shows ───────────────────────────────────────── */

    /** Display order for the picker and the summary list. */
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

    /**
     * The picked functions, or nothing at all when the list is empty.
     *
     * An empty list means "no restriction", so listing all eleven rows would be
     * a lie about what the user has chosen — the description says everything
     * shows, and the list stays out of the way until there is a real selection.
     */
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

    /**
     * The app's display name when the platform will give it to us, and the
     * package name when it will not. The manifest's <queries> declaration covers
     * launcher-visible apps; anything outside that stays hidden, and a package
     * name is a worse label but an honest one.
     */
    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) return label.toString();
        } catch (Exception ignored) { }
        return pkg;
    }

    private android.graphics.drawable.Drawable appIcon(String pkg) {
        try { return getPackageManager().getApplicationIcon(pkg); }
        catch (Exception e) { return null; }
    }

    /** A picked entry: optional icon, label, and a Remove control. */
    private View pickedRow(String label, android.graphics.drawable.Drawable icon, Runnable onRemove) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
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

        row.setOnClickListener(v -> onRemove.run());
        return row;
    }

    /* ── Capacity readout ────────────────────────────────────────────── */

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

    /* ── Actions ─────────────────────────────────────────────────────── */

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
        // The service kills the demo process itself; reflect it here too.
        demoButton.postDelayed(this::bind, 400);
    }

    private void disableTheme() {
        prefs.setThemeEnabled(false);
        IslandService.stop(this);
        themeStatus.setText(R.string.theme_off);
        demoButton.postDelayed(this::bind, 400);
    }

    private void setVariant(String v) {
        prefs.setVariant(v);
        if (IslandService.isRunning()) {
            // Simplest correct way to re-shape live windows: restart the service.
            IslandService.stop(this);
            demoButton.postDelayed(() -> IslandService.start(this), 220);
        }
        bind();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        bind();
    }
}
