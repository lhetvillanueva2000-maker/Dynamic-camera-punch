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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dcp.punch.BuildConfig;
import com.dcp.punch.DcpApp;
import com.dcp.punch.R;
import com.dcp.punch.data.DcpNotificationListener;
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

    /** Set while we programmatically flip the switch, so listeners stay quiet. */
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = Prefs.get(this);
        memory = DcpApp.get().memory();

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
