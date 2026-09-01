package com.dcp.punch.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.dcp.punch.R;

/**
 * A labelled dial: name on the left, value on the right, track underneath.
 *
 * One custom View rather than a TextView + SeekBar + TextView, because a screen
 * of these is otherwise fifty-odd views to inflate and lay out, and the platform
 * SeekBar cannot be styled to match without dragging in AppCompat.
 *
 * Dragging reports continuously so a preview can follow the finger, and commits
 * once on release so nothing expensive runs per touch event.
 */
public class SliderRow extends View {

    public interface Listener {
        /** Called on every movement. Cheap work only. */
        void onSliding(SliderRow row, int value);
        /** Called once, on release. */
        void onCommitted(SliderRow row, int value);
    }

    /** Turns a raw value into what the user should read. */
    public interface Formatter { String format(int value); }

    private final float d;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint value = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private final int accent;

    private String key = "";
    private String title = "";
    private int min, max, current;
    private Formatter formatter;
    private Listener listener;

    private boolean dragging;
    /** Grows while the finger is down, so the knob reacts to being held. */
    private float grab;
    private ValueAnimator grabAnim;

    public SliderRow(Context c) {
        super(c);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());
        accent = c.getColor(R.color.accent);

        track.setColor(c.getColor(R.color.line));
        fill.setColor(accent);
        knob.setColor(c.getColor(R.color.text));

        label.setColor(c.getColor(R.color.text));
        label.setTextSize(14f * d);

        value.setColor(c.getColor(R.color.text_dim));
        value.setTextSize(13f * d);
        value.setTextAlign(Paint.Align.RIGHT);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        setClickable(true);
    }

    public SliderRow bind(String key, String title, int min, int max, int current,
                          Formatter f, Listener l) {
        this.key = key;
        this.title = title;
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.current = Math.max(min, Math.min(this.max, current));
        this.formatter = f;
        this.listener = l;
        invalidate();
        return this;
    }

    public String key() { return key; }
    public int value() { return current; }

    public void setValue(int v) {
        current = Math.max(min, Math.min(max, v));
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), Math.round(60 * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float trackY = getHeight() - 17 * d;

        canvas.drawText(title, 0, 20 * d, label);
        String shown = formatter == null ? String.valueOf(current) : formatter.format(current);
        canvas.drawText(shown, w, 20 * d, value);

        float h = 4 * d;
        r.set(0, trackY - h / 2f, w, trackY + h / 2f);
        canvas.drawRoundRect(r, h / 2f, h / 2f, track);

        float t = (current - min) / (float) (max - min);
        float x = t * w;
        if (x > 0) {
            r.set(0, trackY - h / 2f, x, trackY + h / 2f);
            canvas.drawRoundRect(r, h / 2f, h / 2f, fill);
        }

        // A halo while held, so the knob reads as picked up rather than just
        // moving on its own.
        if (grab > 0.01f) {
            knob.setColor(accent);
            knob.setAlpha((int) (52 * grab));
            canvas.drawCircle(x, trackY, (11 + 7 * grab) * d, knob);
            knob.setAlpha(255);
        }
        knob.setColor(getContext().getColor(R.color.text));
        canvas.drawCircle(x, trackY, (7.5f + 1.5f * grab) * d, knob);
    }

    /* ── Touch ───────────────────────────────────────────────────────── */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                // A dial inside a ScrollView must claim the gesture, or the
                // first vertical wobble hands it to the scroller mid-drag.
                getParent().requestDisallowInterceptTouchEvent(true);
                animateGrab(1f);
                update(e.getX(), true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging) update(e.getX(), true);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                animateGrab(0f);
                update(e.getX(), false);
                performClick();
                if (listener != null) listener.onCommitted(this, current);
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private void update(float x, boolean notify) {
        float t = Math.max(0f, Math.min(1f, x / Math.max(1f, getWidth())));
        int next = Math.round(min + t * (max - min));
        if (next != current) {
            current = next;
            invalidate();
            if (notify && listener != null) listener.onSliding(this, current);
        }
    }

    private void animateGrab(float target) {
        if (grabAnim != null) grabAnim.cancel();
        grabAnim = ValueAnimator.ofFloat(grab, target);
        grabAnim.setDuration(150);
        grabAnim.addUpdateListener(a -> { grab = (float) a.getAnimatedValue(); invalidate(); });
        grabAnim.start();
    }
}
