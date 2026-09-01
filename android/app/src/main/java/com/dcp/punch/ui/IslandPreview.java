package com.dcp.punch.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import com.dcp.punch.R;
import com.dcp.punch.mem.Appearance;

/**
 * A live preview of the island, using whatever the dials currently say.
 *
 * It cycles idle → compact → expanded → idle on a loop, because half the
 * settings on this screen are about *movement* — speed, bounce, the expand
 * style — and a still frame cannot show any of them. Every value is read fresh
 * on each morph, so dragging a dial is reflected on the very next cycle.
 *
 * The animation only runs while the view is attached and the window is visible;
 * a preview quietly spinning behind another screen is exactly the sort of thing
 * that shows up later as battery drain nobody can account for.
 */
public class IslandPreview extends View {

    private static final int IDLE = 0, COMPACT = 1, EXPANDED = 2;
    private static final long HOLD_MS = 1150;

    private final float d;
    private final Appearance look;

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallpaper = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();

    private int state = IDLE;
    private float w, h, radius;
    private float fromW, fromH, fromR, toW, toH, toR;

    private ValueAnimator morph;
    private final Runnable advance = this::step;
    private boolean running;

    public IslandPreview(Context c) { this(c, null); }

    public IslandPreview(Context c, AttributeSet a) {
        super(c, a);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());
        look = Appearance.get(c);

        outline.setStyle(Paint.Style.STROKE);
        text.setColor(Color.WHITE);
        text.setTextSize(11f * d);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        wallpaper.setColor(c.getColor(R.color.card_elev));

        applyImmediate(IDLE);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), Math.round(168 * d));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) start(); else stop();
    }

    public void start() {
        if (running) return;
        running = true;
        postDelayed(advance, 400);
    }

    public void stop() {
        running = false;
        removeCallbacks(advance);
        if (morph != null) morph.cancel();
    }

    /** Re-read the dials and restart the cycle from idle. */
    public void refresh() {
        applyImmediate(IDLE);
        state = IDLE;
        removeCallbacks(advance);
        if (running) postDelayed(advance, 250);
        invalidate();
    }

    /* ── The loop ────────────────────────────────────────────────────── */

    private void step() {
        if (!running) return;
        state = (state + 1) % 3;
        startMorph();
        postDelayed(advance, HOLD_MS + duration());
    }

    private long duration() {
        return Math.max(40, (long) (520 * look.animScale()));
    }

    private void targetFor(int s) {
        float scale = look.idleScale();
        switch (s) {
            case COMPACT:
                toW = 190 * d; toH = 37 * d;
                toR = Math.min(toH / 2f, look.get(Appearance.CORNER_RADIUS) * d);
                if (toR <= 0) toR = toH / 2f;
                break;
            case EXPANDED:
                toW = Math.min(look.get(Appearance.EXP_WIDTH) * d, getWidth() - 40 * d);
                toH = 92 * d;
                toR = look.get(Appearance.EXP_RADIUS) * d;
                break;
            default:
                toW = 30 * d * scale; toH = 30 * d * scale; toR = toH / 2f;
        }
    }

    private void applyImmediate(int s) {
        targetFor(s);
        w = toW; h = toH; radius = toR;
    }

    private void startMorph() {
        if (morph != null) morph.cancel();
        fromW = w; fromH = h; fromR = radius;
        targetFor(state);

        morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(duration());
        float tension = look.bounceTension();
        morph.setInterpolator(tension <= 0.01f
                ? new PathInterpolator(0.32f, 0.72f, 0f, 1f)
                : new OvershootInterpolator(tension));
        morph.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            w = fromW + (toW - fromW) * t;
            h = fromH + (toH - fromH) * t;
            radius = fromR + (toR - fromR) * t;
            invalidate();
        });
        morph.start();
    }

    /* ── Drawing ─────────────────────────────────────────────────────── */

    @Override
    protected void onDraw(Canvas canvas) {
        float cw = getWidth(), ch = getHeight();

        // A stand-in for the top of a screen, so the island has something to sit
        // against and the offsets mean something.
        r.set(10 * d, 10 * d, cw - 10 * d, ch - 10 * d);
        canvas.drawRoundRect(r, 22 * d, 22 * d, wallpaper);
        canvas.save();
        clip.rewind();
        clip.addRoundRect(r, 22 * d, 22 * d, Path.Direction.CW);
        canvas.clipPath(clip);

        float cx = cw / 2f + look.get(Appearance.OFFSET_X) * d;
        float top = 10 * d + 16 * d + look.get(Appearance.OFFSET_Y) * d;

        r.set(cx - w / 2f, top, cx + w / 2f, top + h);

        int bg = look.get(Appearance.BG_COLOR);
        boolean idle = state == IDLE && (morph == null || !morph.isRunning());
        // The collapsed-opacity dial only applies at rest; a pill showing
        // content is always solid or it would be unreadable.
        float alpha = idle ? Math.max(look.collapsedAlpha(), 0.06f) : 1f;

        body.setColor(bg);
        body.setAlpha((int) (255 * alpha));
        float sh = look.shadowAlpha();
        if (sh > 0.02f) {
            body.setShadowLayer(13 * d, 0, 5 * d,
                    (((int) (255 * sh)) << 24));
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        } else {
            body.clearShadowLayer();
        }
        canvas.drawRoundRect(r, radius, radius, body);

        int bw = look.get(Appearance.BORDER_WIDTH);
        if (bw > 0) {
            outline.setStrokeWidth(bw * d);
            outline.setColor(look.get(Appearance.OUTLINE_COLOR));
            canvas.drawRoundRect(r, radius, radius, outline);
        }

        // Content, faded in as the pill grows enough to hold it.
        float room = Math.max(0f, Math.min(1f, (w - 60 * d) / (90 * d)));
        if (room > 0.02f) {
            int a = (int) (255 * room);
            chip.setColor(0xFF0A84FF);
            chip.setAlpha(a);
            canvas.drawCircle(r.left + 20 * d, r.centerY(), 9 * d, chip);
            text.setAlpha(a);
            canvas.drawText(state == EXPANDED ? "Expanded preview" : "Now playing",
                    r.left + 36 * d, r.centerY() + 4 * d, text);
        }

        // The lens sits at a fixed offset from the top, exactly as it does in
        // the overlay: this is the invariant the whole geometry protects.
        lens.setColor(0xFF08080A);
        canvas.drawCircle(cx, top + 15 * d, 6 * d, lens);
        lens.setColor(0x33A0B4FF);
        canvas.drawCircle(cx - 2 * d, top + 13 * d, 2 * d, lens);

        canvas.restore();
    }
}
