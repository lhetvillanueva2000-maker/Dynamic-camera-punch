package com.dcp.punch.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import com.dcp.punch.R;

/**
 * The three-tab bar along the bottom.
 *
 * Hand-drawn rather than assembled from widgets, for the same reason the island
 * is: it has to animate continuously and it has exactly one job. A stack of
 * LinearLayouts and ImageViews would need a layout pass per frame to slide the
 * indicator; a Canvas needs none.
 *
 * The indicator is a rounded pill that travels to the tab you pick, on the same
 * curve the island morphs with. Touch feedback is a highlight that grows under
 * the finger and a slight shrink of the tab it is on — the pointer-hover state a
 * mouse or stylus would get, done in a way a finger can also see.
 */
public class TabBar extends View {

    public interface OnTabSelected { void onTabSelected(int index); }

    public static final int TAB_CARDS = 0;
    public static final int TAB_LOOK = 1;
    public static final int TAB_SETTINGS = 2;

    private static final long SLIDE_MS = 420;
    private static final long PRESS_MS = 140;

    private final float d;
    private final String[] labels = new String[3];

    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint press = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    private final int accent, dim, onAccent;

    private int selected = TAB_CARDS;
    /** Animated position of the indicator, in tab units — 0..2, fractional. */
    private float indicator;
    private ValueAnimator slide;

    /** Which tab the finger is on, and how far the highlight has grown. */
    private int pressedTab = -1;
    private float pressAmount;
    private ValueAnimator pressAnim;

    private OnTabSelected listener;

    public TabBar(Context c) { this(c, null); }

    public TabBar(Context c, AttributeSet a) {
        super(c, a);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());

        accent = c.getColor(R.color.accent);
        dim = c.getColor(R.color.text_dim);
        onAccent = c.getColor(R.color.text);

        labels[0] = c.getString(R.string.tab_cards);
        labels[1] = c.getString(R.string.tab_look);
        labels[2] = c.getString(R.string.tab_settings);

        text.setTextSize(11.5f * d);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        glyph.setStyle(Paint.Style.STROKE);
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStrokeJoin(Paint.Join.ROUND);
        glyph.setStrokeWidth(2f * d);

        setBackgroundColor(c.getColor(R.color.card));
        setClickable(true);
    }

    public void setOnTabSelected(OnTabSelected l) { this.listener = l; }

    public int getSelected() { return selected; }

    public void select(int index, boolean animate) {
        index = Math.max(0, Math.min(2, index));
        if (index == selected && animate) return;
        selected = index;
        if (!animate) {
            indicator = index;
            invalidate();
        } else {
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
        setMeasuredDimension(MeasureSpec.getSize(wSpec), Math.round(66 * d));
    }

    /* ── Drawing ─────────────────────────────────────────────────────── */

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() / 3f, h = getHeight();

        // Touch highlight, under everything, on whichever tab holds the finger.
        if (pressedTab >= 0 && pressAmount > 0.01f) {
            press.setColor(accent);
            press.setAlpha((int) (34 * pressAmount));
            r.set(pressedTab * w + 10 * d, 7 * d, (pressedTab + 1) * w - 10 * d, h - 7 * d);
            float grow = 4 * d * (1 - pressAmount);
            r.inset(grow, grow);
            canvas.drawRoundRect(r, 18 * d, 18 * d, press);
        }

        // The travelling indicator.
        float cx = (indicator + 0.5f) * w;
        pill.setColor(accent);
        pill.setAlpha(46);
        r.set(cx - 30 * d, 8 * d, cx + 30 * d, 8 * d + 30 * d);
        canvas.drawRoundRect(r, 15 * d, 15 * d, pill);

        for (int i = 0; i < 3; i++) {
            float centre = (i + 0.5f) * w;

            // How "selected" this tab is, 0..1, so colour crossfades with the
            // indicator instead of snapping when it arrives.
            float on = Math.max(0f, 1f - Math.abs(indicator - i));
            int colour = blend(dim, onAccent, on);

            float shrink = (i == pressedTab) ? 1f - 0.06f * pressAmount : 1f;
            canvas.save();
            canvas.scale(shrink, shrink, centre, h / 2f);

            glyph.setColor(colour);
            drawGlyph(canvas, i, centre, 23 * d, 9.5f * d, on);

            text.setColor(colour);
            text.setAlpha(on > 0.5f ? 255 : 205);
            canvas.drawText(labels[i], centre, h - 13 * d, text);

            canvas.restore();
        }
    }

    /** Three glyphs, drawn as paths so there are no drawables to load. */
    private void drawGlyph(Canvas canvas, int index, float cx, float cy, float s, float on) {
        path.rewind();
        switch (index) {
            case TAB_CARDS: {
                // A stack of cards, the back one peeking out.
                float w = s * 1.25f, h = s * 0.78f;
                r.set(cx - w / 2f, cy - h / 2f + s * 0.16f, cx + w / 2f, cy + h / 2f + s * 0.16f);
                canvas.drawRoundRect(r, 3.5f * d, 3.5f * d, glyph);
                r.set(cx - w / 2f + 3.5f * d, cy - h / 2f - s * 0.30f,
                      cx + w / 2f - 3.5f * d, cy - h / 2f + s * 0.02f);
                canvas.drawRoundRect(r, 3f * d, 3f * d, glyph);
                return;
            }
            case TAB_LOOK: {
                // The island itself: a pill with its lens, which is what this
                // tab edits. It swells slightly as the tab becomes active.
                float grow = 1f + 0.10f * on;
                float w = s * 1.5f * grow, h = s * 0.82f * grow;
                r.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
                canvas.drawRoundRect(r, h / 2f, h / 2f, glyph);
                Paint.Style was = glyph.getStyle();
                glyph.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx + w * 0.24f, cy, h * 0.17f, glyph);
                glyph.setStyle(was);
                return;
            }
            default: {
                // A gear: a ring plus eight teeth.
                canvas.drawCircle(cx, cy, s * 0.42f, glyph);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    canvas.drawLine(cx + ca * s * 0.62f, cy + sa * s * 0.62f,
                                    cx + ca * s * 0.92f, cy + sa * s * 0.92f, glyph);
                }
            }
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

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int tab = (int) (e.getX() / (getWidth() / 3f));
        tab = Math.max(0, Math.min(2, tab));

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(tab, 1f);
                return true;

            case MotionEvent.ACTION_MOVE:
                // Sliding onto a different tab moves the highlight with the
                // finger, so it always sits under where a lift would land.
                if (tab != pressedTab) setPressed(tab, 1f);
                return true;

            case MotionEvent.ACTION_UP:
                if (pressedTab >= 0) select(pressedTab, true);
                performClick();
                setPressed(-1, 0f);
                return true;

            case MotionEvent.ACTION_CANCEL:
                setPressed(-1, 0f);
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private void setPressed(int tab, float target) {
        pressedTab = tab;
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(pressAmount, target);
        pressAnim.setDuration(PRESS_MS);
        pressAnim.addUpdateListener(a -> { pressAmount = (float) a.getAnimatedValue(); invalidate(); });
        pressAnim.start();
    }
}
