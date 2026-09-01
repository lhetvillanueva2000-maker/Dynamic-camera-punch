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
 * for QUERY_ALL_PACKAGES and still does not.
 *
 * Icons are the one real cost on this screen, and they are loaded lazily: only
 * rows within a screen of the viewport are decoded, each is rasterised straight
 * into a bitmap the size it is drawn at, and rows that scroll well clear have
 * theirs released. Decoding the whole drawer up front would be roughly 11 MB of
 * bitmaps for a list showing a dozen rows — the largest allocation this app
 * would ever make, on a screen that is open for a few seconds.
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
        View view;
        ImageView iconView;
        boolean iconLoaded;
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

        if (appsMode) {
            // Icons are decoded only for the rows you can actually see. Doing it
            // up front for a couple of hundred apps is ~11 MB of bitmaps for a
            // screen that shows a dozen — the single largest allocation this app
            // would make, on a screen that is open for a few seconds.
            ScrollView sv = findViewById(R.id.picker_scroll);
            sv.setOnScrollChangeListener((v, x, y, ox, oy) -> syncIcons());
            sv.getViewTreeObserver().addOnGlobalLayoutListener(this::syncIcons);
            sv.post(this::syncIcons);
        }
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
        java.util.Set<String> seenPkgs = new java.util.HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;        // not ourselves
            if (!seenPkgs.add(pkg)) continue;                  // multi-launcher apps

            Row r = new Row();
            r.id = pkg;
            CharSequence lbl = ri.loadLabel(pm);
            r.label = lbl == null ? pkg : lbl.toString();
            // Deliberately no icon here — see syncIcons().
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
                r.iconView = iv;
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

    /**
     * Load the icons you can see and drop the ones you cannot.
     *
     * The visible band is padded by one screen height in each direction so a
     * flick does not scroll into a column of blanks, and rows outside twice
     * that have their bitmap released. Peak stays around a megabyte instead of
     * climbing with the size of the app drawer.
     */
    private void syncIcons() {
        ScrollView sv = findViewById(R.id.picker_scroll);
        if (sv == null) return;
        int top = sv.getScrollY();
        int bottom = top + sv.getHeight();
        int near = sv.getHeight();
        int far = near * 2;

        for (Row r : rows) {
            if (r.view == null || r.iconView == null) continue;
            if (r.view.getVisibility() != View.VISIBLE) continue;

            int y = r.view.getTop();
            boolean isNear = y > top - near && y < bottom + near;
            boolean isFar = y < top - far || y > bottom + far;

            if (isNear && !r.iconLoaded) {
                r.iconView.setImageDrawable(icon(r.id));
                r.iconLoaded = true;
            } else if (isFar && r.iconLoaded) {
                r.iconView.setImageDrawable(null);
                r.iconLoaded = false;
            }
        }
    }

    private Drawable icon(String pkg) {
        try { return scaled(getPackageManager().getApplicationIcon(pkg)); }
        catch (Exception e) { return null; }
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
        // The rows moved, so what is on screen changed with them.
        if (appsMode) findViewById(R.id.picker_scroll).post(this::syncIcons);
    }

    @Override
    protected void onDestroy() {
        // Hand the bitmaps back rather than waiting for the activity to be
        // collected. This screen can hold a few hundred rows.
        for (Row r : rows) {
            if (r.iconView != null) r.iconView.setImageDrawable(null);
            r.view = null;
            r.iconView = null;
        }
        rows.clear();
        super.onDestroy();
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
