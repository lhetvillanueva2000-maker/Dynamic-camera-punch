package com.dcp.punch.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dcp.punch.R;

/**
 * The control panel's vocabulary, in one place.
 *
 * Every screen is built from the same six shapes — a section header, a card, a
 * row with a switch, a row with a value, a row that goes somewhere, and a note.
 * Defining them once is what makes twelve screens look like one app; defining
 * them in code rather than in layout XML is what makes adding a screen a dozen
 * lines instead of a new file and a round of findViewById.
 *
 * The rows are ordinary Views, so they scroll, recycle and hit-test exactly as
 * the framework expects. Only the pieces that animate — the switch, the badges,
 * the tab bar — are drawn by hand.
 */
public final class Rows {

    /** Row accent families. The tint groups rows by kind, never by state. */
    public static final int BLUE = 0;
    public static final int PINK = 1;
    public static final int PLAIN = 2;

    private final Context ctx;
    private final float d;

    private final int cardColour, cardPressed, textColour, dimColour, accentColour;
    private final int[] tintFill = new int[3];
    private final int[] tintIcon = new int[3];

    public Rows(Context c) {
        ctx = c;
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());

        cardColour = c.getColor(R.color.card_elev);
        cardPressed = c.getColor(R.color.ripple);
        textColour = c.getColor(R.color.text);
        dimColour = c.getColor(R.color.text_dim);
        accentColour = c.getColor(R.color.section);

        tintFill[BLUE] = c.getColor(R.color.tint_blue_bg);
        tintIcon[BLUE] = c.getColor(R.color.tint_blue_fg);
        tintFill[PINK] = c.getColor(R.color.tint_pink_bg);
        tintIcon[PINK] = c.getColor(R.color.tint_pink_fg);
        tintFill[PLAIN] = c.getColor(R.color.tint_plain_bg);
        tintIcon[PLAIN] = c.getColor(R.color.tint_plain_fg);
    }

    public float dp(float v) { return v * d; }
    public int px(float v) { return Math.round(v * d); }

    /* ── Containers ──────────────────────────────────────────────────── */

    /**
     * A section header: the title in the accent, and an optional action on the
     * right. The action is a word rather than an icon because there is room for
     * it and a word cannot be misread.
     */
    public TextView section(ViewGroup parent, CharSequence title,
                            CharSequence action, Runnable onAction) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(6), px(20), px(6), px(9));

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(accentColour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView a = null;
        if (action != null) {
            a = new TextView(ctx);
            a.setText(action);
            a.setTextColor(ctx.getColor(R.color.text_faint));
            a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            a.setPadding(px(10), px(4), px(6), px(4));
            if (onAction != null) {
                a.setBackground(ripple(px(14)));
                a.setOnClickListener(v -> onAction.run());
            }
            row.addView(a);
        }

        parent.addView(row, wide());
        return a;
    }

    public TextView section(ViewGroup parent, CharSequence title) {
        return section(parent, title, null, null);
    }

    /**
     * A card: the rounded surface rows sit on. Several rows in one card reads as
     * one group; one row per card reads as a list of independent things. Both
     * appear in this app and the difference is deliberate.
     */
    public LinearLayout card(ViewGroup parent) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cardBackground());
        box.setClipToOutline(true);
        LinearLayout.LayoutParams lp = wide();
        lp.bottomMargin = px(9);
        parent.addView(box, lp);
        return box;
    }

    /** A note: explanatory text on its own card, no icon, no action. */
    public TextView note(ViewGroup parent, CharSequence text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(dimColour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        t.setLineSpacing(0f, 1.35f);
        t.setBackground(cardBackground());
        t.setPadding(px(18), px(16), px(18), px(16));
        LinearLayout.LayoutParams lp = wide();
        lp.bottomMargin = px(9);
        parent.addView(t, lp);
        return t;
    }

    /** Fine print under a card, no surface of its own. */
    public TextView caption(ViewGroup parent, CharSequence text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(ctx.getColor(R.color.text_faint));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        t.setLineSpacing(0f, 1.3f);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(px(16), px(2), px(16), px(14));
        parent.addView(t, wide());
        return t;
    }

    /* ── Rows ────────────────────────────────────────────────────────── */

    /** A row with a switch on the right. Returns the switch so it can be bound. */
    public CheckSwitch toggle(ViewGroup card, int glyph, int tint,
                              CharSequence title, CharSequence subtitle,
                              boolean checked, CheckSwitch.OnChanged onChanged) {
        LinearLayout row = baseRow(card, glyph, tint, title, subtitle);

        CheckSwitch sw = new CheckSwitch(ctx);
        sw.bind(checked);
        sw.setOnChanged(onChanged);
        divider(row);
        row.addView(sw);

        // The whole row toggles, not just the 52dp switch. A switch is a small
        // target and the row is already the thing being described.
        row.setBackground(ripple(0));
        row.setOnClickListener(v -> sw.toggle());
        return sw;
    }

    /** A row showing a value on the right, tapped to change it. */
    public TextView value(ViewGroup card, int glyph, int tint,
                          CharSequence title, CharSequence subtitle,
                          CharSequence value, Runnable onClick) {
        LinearLayout row = baseRow(card, glyph, tint, title, subtitle);

        TextView v = new TextView(ctx);
        v.setText(value);
        v.setTextColor(textColour);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        v.setGravity(Gravity.END);
        v.setPadding(px(12), 0, 0, 0);
        row.addView(v);

        if (onClick != null) {
            row.setBackground(ripple(0));
            row.setOnClickListener(x -> onClick.run());
        }
        return v;
    }

    /** A row that opens something, with a chevron. */
    public LinearLayout nav(ViewGroup card, int glyph, int tint,
                            CharSequence title, CharSequence subtitle, Runnable onClick) {
        LinearLayout row = baseRow(card, glyph, tint, title, subtitle);

        IconBadge chevron = new IconBadge(ctx);
        chevron.set(Glyphs.CHEVRON, Color.TRANSPARENT, ctx.getColor(R.color.text_faint));
        chevron.size(22f);
        row.addView(chevron);

        if (onClick != null) {
            row.setBackground(ripple(0));
            row.setOnClickListener(v -> onClick.run());
        }
        return row;
    }

    /**
     * A category summary: a strip of the icons that category contains, a
     * divider, and a chevron. Tapping opens the full list.
     *
     * This is the row that makes the first screen short. The alternative — every
     * toggle on one page — was what this app used to do, and it meant scrolling
     * past forty switches to reach the one being looked for.
     */
    public LinearLayout chips(ViewGroup parent, int[] glyphs, int tint, Runnable onClick) {
        LinearLayout box = card(parent);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(14), px(14), px(14), px(14));
        row.setBackground(ripple(0));

        LinearLayout strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < glyphs.length; i++) {
            IconBadge b = new IconBadge(ctx);
            b.set(glyphs[i], tintFill[tint], tintIcon[tint]).size(42f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = px(10);
            strip.addView(b, lp);
        }
        row.addView(strip, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        divider(row);

        IconBadge chevron = new IconBadge(ctx);
        chevron.set(Glyphs.CHEVRON, Color.TRANSPARENT, ctx.getColor(R.color.text_faint));
        chevron.size(22f);
        row.addView(chevron);

        if (onClick != null) row.setOnClickListener(v -> onClick.run());
        box.addView(row, wide());
        return box;
    }

    /** A row with a title and subtitle only — for things that just describe. */
    public LinearLayout plain(ViewGroup card, int glyph, int tint,
                              CharSequence title, CharSequence subtitle) {
        return baseRow(card, glyph, tint, title, subtitle);
    }

    /* ── Pieces ──────────────────────────────────────────────────────── */

    /**
     * The shared skeleton: optional badge, then the title over its subtitle,
     * taking all the width that is left.
     */
    private LinearLayout baseRow(ViewGroup card, int glyph, int tint,
                                 CharSequence title, CharSequence subtitle) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(14), px(13), px(16), px(13));
        row.setMinimumHeight(px(62));

        if (glyph >= 0) {
            IconBadge badge = new IconBadge(ctx);
            badge.set(glyph, tintFill[tint], tintIcon[tint]).size(44f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = px(14);
            row.addView(badge, lp);
        } else {
            row.setPadding(px(18), px(13), px(16), px(13));
        }

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(textColour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        text.addView(t);

        if (subtitle != null && subtitle.length() > 0) {
            TextView s = new TextView(ctx);
            s.setText(subtitle);
            s.setTextColor(dimColour);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            s.setLineSpacing(0f, 1.22f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(2);
            text.addView(s, lp);
        }

        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(row, wide());
        return row;
    }

    /** The hairline that separates a row's description from its control. */
    private void divider(ViewGroup row) {
        View line = new View(ctx);
        line.setBackgroundColor(ctx.getColor(R.color.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, px(1)), px(28));
        lp.leftMargin = px(6);
        lp.rightMargin = px(14);
        row.addView(line, lp);
    }

    public GradientDrawable cardBackground() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cardColour);
        g.setCornerRadius(dp(22));
        return g;
    }

    /** A ripple clipped to a rounded rect, so it never squares off a card. */
    public RippleDrawable ripple(int radiusPx) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(cardPressed), null, mask);
    }

    private LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
