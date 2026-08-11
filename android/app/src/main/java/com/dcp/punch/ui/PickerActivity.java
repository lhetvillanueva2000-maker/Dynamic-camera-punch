package com.dcp.punch.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dcp.punch.R;
import com.dcp.punch.data.Sources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "+" picker: choose what the island is allowed to show.
 *
 * Two modes, one screen. In APPS mode it lists every installed app that has a
 * launcher icon; in FUNCTIONS mode it lists the built-in kinds of content. Both
 * are multi-select and both write straight through to Sources, so backing out
 * keeps whatever was ticked.
 *
 * The app list is read through PackageManager under the manifest's <queries>
 * declaration. That means launcher-visible apps only — this app has never asked
 * for QUERY_ALL_PACKAGES and still does not. Icons are loaded once, scaled down
 * to the size they are drawn at, and cached: a full app list at full resolution
 * is tens of megabytes, which is exactly the kind of waste the rest of this
 * project has been busy deleting.
 */
public class PickerActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_APPS = "apps";
    public static final String MODE_FUNCTIONS = "functions";

    /** Icons are drawn at 40dp; decoding them larger than that is dead weight. */
    private static final int ICON_DP = 40;

    private Sources sources;
    private LinearLayout list;
    private TextView emptyNote;
    private boolean appsMode;
    private int iconPx;

    private final List<Row> rows = new ArrayList<>();

    private static class Row {
        String id;          // package name, or Source.key
        String label;
        Drawable icon;
        View view;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        sources = Sources.get(this);
        appsMode = !MODE_FUNCTIONS.equals(getIntent().getStringExtra(EXTRA_MODE));
        iconPx = Math.round(ICON_DP * getResources().getDisplayMetrics().density);

        setContentView(R.layout.activity_picker);
        ((TextView) findViewById(R.id.picker_title))
                .setText(appsMode ? R.string.picker_apps_title : R.string.picker_functions_title);
        ((TextView) findViewById(R.id.picker_sub))
                .setText(appsMode ? R.string.picker_apps_sub : R.string.picker_functions_sub);

        list = findViewById(R.id.picker_list);
        emptyNote = findViewById(R.id.picker_empty);
        findViewById(R.id.picker_done).setOnClickListener(v -> finish());

        EditText search = findViewById(R.id.picker_search);
        search.setVisibility(appsMode ? View.VISIBLE : View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });

        if (appsMode) loadApps(); else loadFunctions();
        buildRows();
    }

    /* ── Contents ────────────────────────────────────────────────────── */

    private void loadFunctions() {
        for (Sources.Source s : Sources.Source.values()) {
            Row r = new Row();
            r.id = s.key;
            r.label = getString(MainActivity.labelResFor(s));
            rows.add(r);
        }
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent launchable = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(launchable, 0);
        } catch (Exception e) {
            found = Collections.emptyList();
        }

        List<Row> out = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;        // not ourselves
            boolean dupe = false;
            for (Row existing : out) if (existing.id.equals(pkg)) { dupe = true; break; }
            if (dupe) continue;                                 // multi-launcher apps

            Row r = new Row();
            r.id = pkg;
            CharSequence lbl = ri.loadLabel(pm);
            r.label = lbl == null ? pkg : lbl.toString();
            r.icon = scaled(ri.loadIcon(pm));
            out.add(r);
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        rows.addAll(out);
    }

    /**
     * Redraw the icon into a bitmap the size it will actually be shown at.
     * Adaptive icons hand back drawables that rasterise far larger than 40dp,
     * and a list of two hundred of them at full size is a real cost.
     */
    private Drawable scaled(Drawable src) {
        if (src == null) return null;
        try {
            Bitmap bm = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            src.setBounds(0, 0, iconPx, iconPx);
            src.draw(c);
            return new BitmapDrawable(getResources(), bm);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /* ── Rows ────────────────────────────────────────────────────────── */

    private void buildRows() {
        list.removeAllViews();
        emptyNote.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);

        float d = getResources().getDisplayMetrics().density;
        for (Row r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Math.round(9 * d), 0, Math.round(9 * d));

            if (appsMode) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(r.icon);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconPx, iconPx);
                lp.rightMargin = Math.round(12 * d);
                row.addView(iv, lp);
            }

            TextView tv = new TextView(this);
            tv.setText(r.label);
            tv.setTextColor(getColor(R.color.text));
            tv.setTextSize(15f);
            tv.setMaxLines(1);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(tv, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView add = new TextView(this);
            add.setTextSize(13f);
            add.setPadding(Math.round(14 * d), Math.round(7 * d),
                    Math.round(14 * d), Math.round(7 * d));
            row.addView(add);

            paintAdd(add, isPicked(r));
            View.OnClickListener toggle = v -> {
                boolean nowPicked = !isPicked(r);
                setPicked(r, nowPicked);
                paintAdd(add, nowPicked);
            };
            row.setOnClickListener(toggle);
            add.setOnClickListener(toggle);

            r.view = row;
            list.addView(row);
        }
    }

    private void paintAdd(TextView t, boolean picked) {
        t.setText(picked ? R.string.picker_added : R.string.picker_add);
        t.setTextColor(getColor(picked ? R.color.green : R.color.accent));
        t.setBackgroundResource(picked ? R.drawable.bg_pill_active : R.drawable.bg_pill);
    }

    private boolean isPicked(Row r) {
        if (appsMode) return sources.allowedApps().contains(r.id);
        return sources.allowedFunctionKeys().contains(r.id);
    }

    private void setPicked(Row r, boolean picked) {
        if (appsMode) {
            if (picked) sources.addApp(r.id); else sources.removeApp(r.id);
            return;
        }
        Sources.Source s = Sources.Source.byKey(r.id);
        if (s == null) return;
        if (picked) sources.addFunction(s); else sources.removeFunction(s);
    }

    private void filter(String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(java.util.Locale.getDefault());
        for (Row r : rows) {
            if (r.view == null) continue;
            boolean show = needle.isEmpty()
                    || r.label.toLowerCase(java.util.Locale.getDefault()).contains(needle)
                    || r.id.toLowerCase(java.util.Locale.getDefault()).contains(needle);
            r.view.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void finish() {
        // The control panel rebuilds its lists and its capacity readout onResume.
        setResult(RESULT_OK);
        super.finish();
    }

    /** Keeps the scroll position sane when the keyboard opens over a long list. */
    @Override
    public void onBackPressed() {
        ScrollView sv = findViewById(R.id.picker_scroll);
        if (sv != null) sv.scrollTo(0, 0);
        super.onBackPressed();
    }
}
