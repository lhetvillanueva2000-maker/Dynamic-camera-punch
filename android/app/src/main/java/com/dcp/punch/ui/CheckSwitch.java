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
 * A switch whose thumb carries a tick when it is on and a cross when it is off.
 *
 * The framework switch says "on" with colour alone, which is the one channel a
 * colour-blind user does not have and a bright screen washes out. A tick and a
 * cross say it twice, and the second way survives both. The thumb slides and the
 * mark cross-fades on the same curve everything else here moves on.
 *
 * It is a plain View rather than a CompoundButton because it needs no text, no
 * drawable state list and no framework theming — and because the whole control
 * panel is drawn this way, so it matches without being made to match.
 */
public class CheckSwitch extends View {

    public interface OnChanged { void onChanged(CheckSwitch v, boolean checked); }

    private static final long SLIDE_MS = 260;

    private final float d;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private final int onTrack, offTrack, onThumb, offThumb, onMark, offMark;

    private boolean checked;
    /** 0 = fully off, 1 = fully on. Animated, so the thumb slides. */
    private float pos;
    private ValueAnimator anim;

    private OnChanged listener;
    /** Set while a value is being written in, so a bind does not fire a change. */
    private boolean silent;

    public CheckSwitch(Context c) { this(c, null); }

    public CheckSwitch(Context c, AttributeSet a) {
        super(c, a);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());

        onTrack = c.getColor(R.color.switch_on_track);
        offTrack = c.getColor(R.color.switch_off_track);
        onThumb = c.getColor(R.color.switch_on_thumb);
        offThumb = c.getColor(R.color.switch_off_thumb);
        onMark = c.getColor(R.color.switch_on_mark);
        offMark = c.getColor(R.color.switch_off_mark);

        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setStrokeJoin(Paint.Join.ROUND);
        mark.setStrokeWidth(2f * d);

        setClickable(true);
        setFocusable(true);
    }

    public void setOnChanged(OnChanged l) { this.listener = l; }

    public boolean isChecked() { return checked; }

    /** Set the value without telling the listener — for binding from storage. */
    public void bind(boolean value) {
        silent = true;
        setChecked(value, false);
        silent = false;
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value && (pos == (value ? 1f : 0f))) return;
        checked = value;
        float target = value ? 1f : 0f;

        if (anim != null) anim.cancel();
        if (!animate) {
            pos = target;
            invalidate();
        } else {
            anim = ValueAnimator.ofFloat(pos, target);
            anim.setDuration(SLIDE_MS);
            anim.setInterpolator(new PathInterpolator(0.32f, 0.72f, 0f, 1f));
            anim.addUpdateListener(a -> { pos = (float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }
        if (!silent && listener != null) listener.onChanged(this, checked);
    }

    public void toggle() { setChecked(!checked, true); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(Math.round(52 * d), Math.round(31 * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();

        track.setColor(blend(offTrack, onTrack, pos));
        r.set(0, 0, w, h);
        canvas.drawRoundRect(r, h / 2f, h / 2f, track);

        float radius = h / 2f - 3f * d;
        float cx = radius + 3f * d + pos * (w - 2f * (radius + 3f * d));
        float cy = h / 2f;

        thumb.setColor(blend(offThumb, onThumb, pos));
        canvas.drawCircle(cx, cy, radius, thumb);

        // Tick and cross cross-fade through the middle rather than swapping, so
        // there is no frame where the thumb is blank.
        float g = radius * 0.62f;
        if (pos < 0.98f) {
            mark.setColor(offMark);
            mark.setAlpha((int) (255 * (1f - pos)));
            Glyphs.draw(canvas, Glyphs.CROSS, cx, cy, g, mark);
        }
        if (pos > 0.02f) {
            mark.setColor(onMark);
            mark.setAlpha((int) (255 * pos));
            Glyphs.draw(canvas, Glyphs.CHECK, cx, cy, g, mark);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && isEnabled()) {
            performClick();
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    private static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
