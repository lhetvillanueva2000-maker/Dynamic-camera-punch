package com.dcp.punch.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.dcp.punch.mem.MemoryBudget;

/**
 * The memory gauge, in the app where a control panel belongs.
 *
 * This used to be a pull tab welded to the right edge of the screen as a second
 * always-on overlay window. A permanent handle on the side of the display is
 * something nobody asked for and everybody has to look at, so it lives here now.
 *
 * A speedometer rather than a slider: graduated ticks around a 220° arc,
 * labelled at the majors, with a needle for the ceiling you have set and a
 * second, filled band showing what the app is *actually* using against it. The
 * two are drawn on the same scale on purpose — the whole point is that they can
 * be compared, and on a healthy install the needle sits far above a very short
 * band.
 */
public class MemoryGaugeView extends View {

    public interface OnBudgetChange {
        void onBudgetChosen(long bytes);
    }

    private static final float START_ANGLE = 160, SWEEP = 220;
    private static final int TICKS = 44;
    private static final int TICK_MAJOR_EVERY = 11;

    private final float d;

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tiny = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    /* Read from resources rather than written twice: these are the same three
       status colours the rest of the app and the web tokens use. */
    private final int green, amber, red;

    private MemoryBudget budget;
    private OnBudgetChange listener;

    /** Chosen ceiling as a fraction of [min, max]. */
    private float t;
    private boolean dragging;

    public MemoryGaugeView(Context c) { this(c, null); }

    public MemoryGaugeView(Context c, AttributeSet a) {
        super(c, a);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());
        green = c.getColor(com.dcp.punch.R.color.green);
        amber = c.getColor(com.dcp.punch.R.color.orange);
        red = c.getColor(com.dcp.punch.R.color.red);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(7 * d);

        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);

        needle.setStyle(Paint.Style.STROKE);
        needle.setStrokeCap(Paint.Cap.ROUND);

        big.setColor(Color.WHITE);
        big.setTextSize(30 * d);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        big.setTextAlign(Paint.Align.CENTER);

        small.setColor(0xB3FFFFFF);
        small.setTextSize(12 * d);
        small.setTextAlign(Paint.Align.CENTER);

        tiny.setColor(0x8AFFFFFF);
        tiny.setTextSize(10.5f * d);
        tiny.setTextAlign(Paint.Align.CENTER);
    }

    public void bind(MemoryBudget b, OnBudgetChange l) {
        this.budget = b;
        this.listener = l;
        this.t = fractionOf(b.getBudgetBytes());
        invalidate();
    }

    /** Re-read the measured footprint and redraw. */
    public void refresh() {
        if (budget == null) return;
        budget.sample();
        if (!dragging) t = fractionOf(budget.getBudgetBytes());
        invalidate();
    }

    private float fractionOf(long bytes) {
        long min = budget.minBudgetBytes(), max = budget.maxBudgetBytes();
        if (max <= min) return 0f;
        return Math.max(0f, Math.min(1f, (bytes - min) / (float) (max - min)));
    }

    private long bytesOf(float f) {
        long min = budget.minBudgetBytes(), max = budget.maxBudgetBytes();
        return min + (long) (f * (max - min));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Math.round(196 * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (budget == null) return;

        float cx = getWidth() / 2f;
        float cy = getHeight() - 46 * d;
        float r = Math.min(getWidth() / 2f - 22 * d, getHeight() - 66 * d);
        if (r <= 0) return;

        // Ticks, lit up to the chosen ceiling.
        for (int i = 0; i <= TICKS; i++) {
            float f = i / (float) TICKS;
            boolean major = i % TICK_MAJOR_EVERY == 0;
            double a = Math.toRadians(START_ANGLE + SWEEP * f);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float inner = r - (major ? 14 * d : 8 * d);

            boolean lit = f <= t;
            tick.setColor(lit ? accent() : 0xFFFFFFFF);
            tick.setAlpha(lit ? 255 : (major ? 90 : 45));
            tick.setStrokeWidth((major ? 2.4f : 1.3f) * d);
            canvas.drawLine(cx + r * ca, cy + r * sa, cx + inner * ca, cy + inner * sa, tick);

            if (major) {
                float lr = inner - 12 * d;
                tiny.setAlpha(190);
                canvas.drawText(scaleLabel(bytesOf(f)),
                        cx + lr * ca, cy + lr * sa + 3.5f * d, tiny);
            }
        }

        // Ceiling band.
        float bandR = r + 6 * d;
        box.set(cx - bandR, cy - bandR, cx + bandR, cy + bandR);
        arc.setColor(0xFFFFFFFF); arc.setAlpha(28);
        canvas.drawArc(box, START_ANGLE, SWEEP, false, arc);
        arc.setColor(accent()); arc.setAlpha(255);
        canvas.drawArc(box, START_ANGLE, SWEEP * t, false, arc);

        // What the app is actually using, on the same scale. Usually a stub —
        // that is the point.
        float used = Math.max(0f, Math.min(1f,
                fractionOf(Math.max(budget.minBudgetBytes(), budget.actualUsageBytes()))));
        float usedR = r - 26 * d;
        box.set(cx - usedR, cy - usedR, cx + usedR, cy + usedR);
        arc.setColor(green); arc.setAlpha(230);
        arc.setStrokeWidth(4.5f * d);
        canvas.drawArc(box, START_ANGLE, Math.max(1.5f, SWEEP * used), false, arc);
        arc.setStrokeWidth(7 * d);

        // Needle.
        double ang = Math.toRadians(START_ANGLE + SWEEP * t);
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        needle.setColor(accent());
        needle.setStrokeWidth(3.2f * d);
        canvas.drawLine(cx + 10 * d * ca, cy + 10 * d * sa,
                cx + (r - 20 * d) * ca, cy + (r - 20 * d) * sa, needle);
        knob.setColor(c0());
        canvas.drawCircle(cx, cy, 13 * d, knob);
        knob.setColor(accent());
        canvas.drawCircle(cx, cy, 6 * d, knob);

        // Readout.
        canvas.drawText(MemoryBudget.readable(bytesOf(t)), cx, cy - 44 * d, big);
        canvas.drawText("ceiling", cx, cy - 64 * d, small);
        canvas.drawText("using " + MemoryBudget.mb(budget.actualUsageBytes())
                + (budget.isAutoManage() ? "  ·  managed automatically" : ""),
                cx, cy - 22 * d, tiny);
    }

    /** Green while there is plenty of head-room, warmer as the ceiling rises. */
    private int accent() {
        if (t < 0.5f) return green;
        if (t < 0.8f) return amber;
        return red;
    }

    /** The card colour behind the needle hub, so it reads as a cut-out. */
    private int c0() { return getContext().getColor(com.dcp.punch.R.color.card_elev); }

    private static String scaleLabel(long bytes) {
        double g = bytes / (1024.0 * 1024 * 1024);
        return g < 1 ? (bytes / (1024 * 1024)) + "M"
                     : String.format(java.util.Locale.US, "%.1fG", g);
    }

    /* ── Touch ───────────────────────────────────────────────────────── */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (budget == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);  // beat the ScrollView
                updateFromTouch(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                if (listener != null) listener.onBudgetChosen(bytesOf(t));
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private void updateFromTouch(float x, float y) {
        float cx = getWidth() / 2f;
        float cy = getHeight() - 46 * d;
        double deg = Math.toDegrees(Math.atan2(y - cy, x - cx));
        if (deg < 0) deg += 360;

        // The arc runs 160° → 380°, and 380 wraps round to 20, so the low end
        // has to be lifted back above the high end before the two can be
        // compared. The gap the arc does not cover is 20°→160°, straight down;
        // splitting it at its bisector (90°) is what makes a touch below the
        // hub snap to whichever end of the scale it is actually nearer, rather
        // than always to the top.
        if (deg < (SWEEP + 2 * START_ANGLE - 360) / 2) deg += 360;
        t = Math.max(0f, Math.min(1f, (float) ((deg - START_ANGLE) / SWEEP)));
        invalidate();
    }
}
