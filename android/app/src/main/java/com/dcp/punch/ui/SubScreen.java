package com.dcp.punch.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dcp.punch.R;

/**
 * A detail screen, pushed over a tab.
 *
 * The first screen of each tab is a short list of categories; opening one pushes
 * one of these over it. That split is the whole reorganisation: the top level
 * answers "what can this do", and only the level below it asks you to make forty
 * decisions.
 *
 * It slides in from the right and back out the same way, and it covers the tab
 * completely — including the tab bar — because while you are inside a category
 * the tabs are not what the back gesture should reach first.
 */
public class SubScreen extends FrameLayout {

    /** Fills in the body. Called once, at construction. */
    public interface Builder { void build(LinearLayout body, Rows rows); }

    private static final long IN_MS = 320;
    private static final long OUT_MS = 240;

    private final Rows rows;
    private Runnable onDismiss;

    public SubScreen(Context c, CharSequence title, CharSequence actionLabel,
                     Runnable onAction, Builder builder) {
        super(c);
        rows = new Rows(c);

        setBackgroundColor(c.getColor(R.color.app_background));
        // Opaque and clickable, so a tap cannot fall through to the tab beneath.
        setClickable(true);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        root.addView(header(c, title, actionLabel, onAction),
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(rows.px(14), 0, rows.px(14), rows.px(28));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        builder.build(body, rows);
    }

    /** Back arrow, title, and an optional action pill on the right. */
    private View header(Context c, CharSequence title,
                        CharSequence actionLabel, Runnable onAction) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(rows.px(14), rows.px(14), rows.px(16), rows.px(10));

        IconBadge back = new IconBadge(c);
        back.set(Glyphs.BACK, c.getColor(R.color.card_elev), c.getColor(R.color.text));
        back.size(42f);
        back.setBackground(rows.ripple(rows.px(21)));
        back.setOnClickListener(v -> dismiss());
        bar.addView(back);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(c.getColor(R.color.text));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 19f);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        t.setPadding(rows.px(14), 0, rows.px(8), 0);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (actionLabel != null) {
            TextView a = new TextView(c);
            a.setText(actionLabel);
            a.setTextColor(c.getColor(R.color.text));
            a.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.5f);
            a.setPadding(rows.px(18), rows.px(9), rows.px(18), rows.px(9));
            android.graphics.drawable.GradientDrawable pill =
                    new android.graphics.drawable.GradientDrawable();
            pill.setColor(c.getColor(R.color.card_elev));
            pill.setCornerRadius(rows.dp(20));
            a.setBackground(new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{ pill, rows.ripple(rows.px(20)) }));
            if (onAction != null) a.setOnClickListener(v -> onAction.run());
            bar.addView(a);
        }

        return bar;
    }

    /* ── Push and pop ────────────────────────────────────────────────── */

    /** Slide in over the host. */
    public void present(ViewGroup host, Runnable onDismissed) {
        this.onDismiss = onDismissed;
        host.addView(this, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        setAlpha(0f);
        post(() -> {
            setTranslationX(getWidth() * 0.14f);
            animate().translationX(0f).alpha(1f)
                    .setDuration(IN_MS)
                    .setInterpolator(new PathInterpolator(0.32f, 0.72f, 0f, 1f))
                    .start();
        });
    }

    /** Slide out and remove. Safe to call twice. */
    public void dismiss() {
        if (getParent() == null) return;
        animate().translationX(getWidth() * 0.14f).alpha(0f)
                .setDuration(OUT_MS)
                .setInterpolator(new PathInterpolator(0.4f, 0f, 0.6f, 1f))
                .withEndAction(() -> {
                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) parent.removeView(this);
                    if (onDismiss != null) onDismiss.run();
                })
                .start();
    }

    /** An illustration for a screen with nothing on it yet. */
    public static View emptyState(Context c, Rows rows, CharSequence message) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(rows.px(20), rows.px(46), rows.px(20), rows.px(30));

        IconBadge art = new IconBadge(c);
        art.set(Glyphs.SPARKLE, c.getColor(R.color.card_elev), c.getColor(R.color.text_faint));
        art.size(84f);
        box.addView(art);

        TextView t = new TextView(c);
        t.setText(message);
        t.setTextColor(c.getColor(R.color.text_faint));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.5f);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0f, 1.3f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = rows.px(16);
        box.addView(t, lp);

        box.setBackgroundColor(Color.TRANSPARENT);
        return box;
    }
}
