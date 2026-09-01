package com.dcp.punch.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import com.dcp.punch.R;

/**
 * The tab bar: a floating pill, three icons, the active one riding in a
 * coloured circle that slides between them.
 *
 * It floats clear of the content rather than sitting in a bar welded to the
 * bottom edge. That is not only nicer to look at — it means the list behind it
 * reads as one continuous surface instead of being cut off by a hard line, and
 * the bar takes only as much width as it needs instead of a full-width slab
 * across a tablet.
 *
 * Labels are gone. Three destinations with distinct shapes do not need them, and
 * dropping them buys the height that makes the pill look like a control rather
 * than a toolbar. The name of the current tab is in the screen's own title,
 * which is where someone actually looks for it.
 *
 * Drawn on a Canvas: the selector slides every frame, and a stack of Views would
 * need a layout pass per frame to do the same thing.
 */
public class FloatingTabBar extends View {

    public interface OnTabSelected { void onTabSelected(int index); }

    public static final int TAB_CARDS = 0;
    public static final int TAB_LOOK = 1;
    public static final int TAB_SETTINGS = 2;

    private static final int COUNT = 3;
    private static final long SLIDE_MS = 460;
    private static final long PRESS_MS = 130;

    /** Pill geometry, in dp. */
    private static final float BAR_H = 62f;
    private static final float SLOT_W = 74f;
    private static final float SEL_R = 25f;

    private final float d;

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint press = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private final int barColour, selColour, iconOn, iconOff, shadow;

    private static final int[] ICONS = { Glyphs.CARDS, Glyphs.ISLAND, Glyphs.GEAR };

    private int selected = TAB_CARDS;
    /** Animated indicator position in tab units, 0..2, fractional. */
    private float indicator;
    private ValueAnimator slide;

    private int pressedTab = -1;
    private float pressAmount;
    private ValueAnimator pressAnim;

    private OnTabSelected listener;

    public FloatingTabBar(Context c) { this(c, null); }

    public FloatingTabBar(Context c, AttributeSet a) {
        super(c, a);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());

        barColour = c.getColor(R.color.tabbar);
        selColour = c.getColor(R.color.tabbar_selected);
        iconOn = c.getColor(R.color.tabbar_icon_on);
        iconOff = c.getColor(R.color.tabbar_icon_off);
        shadow = c.getColor(R.color.tabbar_shadow);

        glyph.setStyle(Paint.Style.STROKE);
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStrokeJoin(Paint.Join.ROUND);
        glyph.setStrokeWidth(2.1f * d);

        // A soft drop shadow so the pill reads as floating over the list rather
        // than pasted onto it. Software layer: shadow layers are not supported
        // by the hardware pipeline on every version this app runs on.
        bar.setColor(barColour);
        bar.setShadowLayer(14f * d, 0f, 5f * d, shadow);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        setClickable(true);
    }

    public void setOnTabSelected(OnTabSelected l) { this.listener = l; }

    public int getSelected() { return selected; }

    public void select(int index, boolean animate) {
        index = Math.max(0, Math.min(COUNT - 1, index));
        boolean same = index == selected;
        selected = index;

        if (!animate) {
            indicator = index;
            invalidate();
        } else if (!same || indicator != index) {
            if (slide != null) slide.cancel();
            slide = ValueAnimator.ofFloat(indicator, index);
            slide.setDuration(SLIDE_MS);
            slide.setInterpolator(new PathInterpolator(0.32f, 0.72f, 0f, 1f));
            slide.addUpdateListener(a -> { indicator = (float) a.getAnimatedValue(); invalidate(); });
            slide.start();
        }
        if (listener != null) listener.onTabSelected(index);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        // Enough room around the pill for its shadow to fall without clipping.
        setMeasuredDimension(Math.round(SLOT_W * COUNT * d + 40 * d),
                             Math.round(BAR_H * d + 34 * d));
    }

    /* ── Drawing ─────────────────────────────────────────────────────── */

    @Override
    protected void onDraw(Canvas canvas) {
        float barW = SLOT_W * COUNT * d;
        float barH = BAR_H * d;
        float left = (getWidth() - barW) / 2f;
        float top = (getHeight() - barH) / 2f;

        r.set(left, top, left + barW, top + barH);
        canvas.drawRoundRect(r, barH / 2f, barH / 2f, bar);

        float slot = barW / COUNT;
        float cy = top + barH / 2f;

        // The selector, sliding between slots. It squashes slightly at speed —
        // widening as it travels and settling round — which reads as weight
        // rather than as a circle teleporting.
        float travel = Math.abs(indicator - selected);
        float stretch = 1f + Math.min(0.34f, travel * 0.42f);
        float selCx = left + (indicator + 0.5f) * slot;
        sel.setColor(selColour);
        r.set(selCx - SEL_R * d * stretch, cy - SEL_R * d,
              selCx + SEL_R * d * stretch, cy + SEL_R * d);
        canvas.drawRoundRect(r, SEL_R * d, SEL_R * d, sel);

        for (int i = 0; i < COUNT; i++) {
            float cx = left + (i + 0.5f) * slot;

            // Press ripple, clipped to the selector's shape so it never spills
            // outside the pill.
            if (i == pressedTab && pressAmount > 0.01f) {
                press.setColor(iconOn);
                press.setAlpha((int) (26 * pressAmount));
                canvas.drawCircle(cx, cy, SEL_R * d * (0.72f + 0.28f * pressAmount), press);
            }

            float on = Math.max(0f, 1f - Math.abs(indicator - i));
            glyph.setColor(blend(iconOff, iconOn, on));

            // A pressed tab dips; the selected one sits a hair larger.
            float scale = (1f + 0.06f * on) * (i == pressedTab ? 1f - 0.10f * pressAmount : 1f);
            canvas.save();
            canvas.scale(scale, scale, cx, cy);
            Glyphs.draw(canvas, ICONS[i], cx, cy, 11f * d, glyph);
            canvas.restore();
        }
    }

    private static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /* ── Touch ───────────────────────────────────────────────────────── */

    /** Which slot a point falls in, or -1 for the gap around the pill. */
    private int tabAt(float x, float y) {
        float barW = SLOT_W * COUNT * d, barH = BAR_H * d;
        float left = (getWidth() - barW) / 2f, top = (getHeight() - barH) / 2f;
        if (x < left || x > left + barW || y < top || y > top + barH) return -1;
        return Math.max(0, Math.min(COUNT - 1, (int) ((x - left) / (barW / COUNT))));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int tab = tabAt(e.getX(), e.getY());

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (tab < 0) return false;      // a touch beside the pill is not ours
                setPressedTab(tab, 1f);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (tab != pressedTab) setPressedTab(tab, tab < 0 ? 0f : 1f);
                return true;

            case MotionEvent.ACTION_UP:
                if (pressedTab >= 0 && tab == pressedTab) select(pressedTab, true);
                performClick();
                setPressedTab(-1, 0f);
                return true;

            case MotionEvent.ACTION_CANCEL:
                setPressedTab(-1, 0f);
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private void setPressedTab(int tab, float target) {
        pressedTab = tab;
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(pressAmount, target);
        pressAnim.setDuration(PRESS_MS);
        pressAnim.addUpdateListener(a -> { pressAmount = (float) a.getAnimatedValue(); invalidate(); });
        pressAnim.start();
    }
}
