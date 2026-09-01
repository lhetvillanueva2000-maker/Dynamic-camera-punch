package com.dcp.punch.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.PathInterpolator;

import com.dcp.punch.R;

/**
 * One device statistic: a title, a right-aligned reading, and a bar.
 *
 * The bar animates from wherever it was to the new value rather than jumping,
 * which makes a refresh legible — you can see whether memory went up or down
 * instead of having to remember the previous number.
 */
public class StatBar extends View {

    private final float d;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint reading = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint detail = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private final int green, amber, red;

    private String label = "", value = "", sub = "";
    private float fraction, shown;
    private ValueAnimator anim;

    public StatBar(Context c) {
        super(c);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());

        green = c.getColor(R.color.green);
        amber = c.getColor(R.color.orange);
        red = c.getColor(R.color.red);

        track.setColor(c.getColor(R.color.line));
        title.setColor(c.getColor(R.color.text));
        title.setTextSize(14f * d);

        reading.setColor(c.getColor(R.color.text));
        reading.setTextSize(13.5f * d);
        reading.setTextAlign(Paint.Align.RIGHT);
        reading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        detail.setColor(c.getColor(R.color.text_faint));
        detail.setTextSize(11.5f * d);
    }

    /**
     * @param fraction 0..1 of the bar to fill, or negative for "no bar" — used
     *                 by readings like a chip name that have no scale.
     */
    public void set(String label, String value, String sub, float fraction) {
        this.label = label;
        this.value = value;
        this.sub = sub == null ? "" : sub;
        float target = Math.max(-1f, Math.min(1f, fraction));

        if (target < 0) {
            this.fraction = shown = target;
            invalidate();
            return;
        }
        this.fraction = target;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(Math.max(0f, shown), target);
        anim.setDuration(560);
        anim.setInterpolator(new PathInterpolator(0.32f, 0.72f, 0f, 1f));
        anim.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        boolean bar = fraction >= 0;
        setMeasuredDimension(MeasureSpec.getSize(wSpec),
                Math.round((sub.isEmpty() ? 0 : 15) * d + (bar ? 46 : 28) * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        canvas.drawText(label, 0, 16 * d, title);
        canvas.drawText(value, w, 16 * d, reading);

        float y = 16 * d;
        if (!sub.isEmpty()) {
            y += 16 * d;
            canvas.drawText(sub, 0, y, detail);
        }

        if (fraction < 0) return;

        float barY = y + 15 * d;
        float h = 6 * d;
        r.set(0, barY - h / 2f, w, barY + h / 2f);
        canvas.drawRoundRect(r, h / 2f, h / 2f, track);

        float t = Math.max(0f, shown);
        if (t > 0.001f) {
            fill.setColor(t < 0.7f ? green : t < 0.88f ? amber : red);
            r.set(0, barY - h / 2f, Math.max(h, w * t), barY + h / 2f);
            canvas.drawRoundRect(r, h / 2f, h / 2f, fill);
        }
    }
}
