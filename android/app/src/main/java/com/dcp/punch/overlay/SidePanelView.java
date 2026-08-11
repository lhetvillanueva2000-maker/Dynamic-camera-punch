package com.dcp.punch.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import com.dcp.punch.mem.MemoryBudget;

/**
 * The right-edge pull tab and the semicircular RAM dial behind it.
 *
 * Collapsed it is a thin grab handle a few dp wide. Drag it left (or tap it) and
 * it opens into a half-disc anchored to the screen edge, with the budget on an
 * arc you drag along.
 *
 * Like the island, this lives in a window sized exactly to its content and
 * re-measured every animation frame, so the rest of the screen keeps receiving
 * touches while it is collapsed.
 *
 * The dial is wired to real memory — see MemoryBudget. It shows the budget and
 * the *measured* footprint side by side precisely so the two can be compared;
 * a dial that only ever echoed its own setting back would be theatre.
 */
public class SidePanelView extends View {

    public interface Callbacks {
        void onPanelOpenChanged(boolean open);
        void onGeometryChanged();
    }

    private static final float HANDLE_W = 7;      // dp, collapsed
    private static final float HANDLE_H = 74;
    private static final float RADIUS = 168;      // dp, open
    private static final float TRACK_INSET = 30;
    private static final long ANIM_MS = 380;

    private final float d;
    private final MemoryBudget budget;
    private final Callbacks callbacks;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tiny = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    // Preallocated for the same reason as IslandView: this draws continuously
    // while the dial is open.
    private final Path bodyPath = new Path();
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 0 = collapsed handle, 1 = fully open dial. */
    private float open;
    private boolean isOpen;
    private ValueAnimator anim;

    /** Live-dragged budget fraction, 0..1 across [MIN, max]. */
    private float t;

    private float downX, downY;
    private boolean draggingArc, moved;
    private long lastRefresh;

    public SidePanelView(Context ctx, MemoryBudget budget, Callbacks cb) {
        super(ctx);
        this.budget = budget;
        this.callbacks = cb;
        this.d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                ctx.getResources().getDisplayMetrics());

        setLayerType(LAYER_TYPE_HARDWARE, null);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(9 * d);

        big.setColor(Color.WHITE);
        big.setTextSize(27 * d);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        small.setColor(0xB3FFFFFF);
        small.setTextSize(12 * d);

        tiny.setColor(0x8AFFFFFF);
        tiny.setTextSize(10.5f * d);

        t = fractionOf(budget.getBudgetBytes());
    }

    /* ── Budget ↔ arc fraction ───────────────────────────────────────── */

    private float fractionOf(long bytes) {
        long min = MemoryBudget.MIN_BUDGET_BYTES;
        long max = budget.maxBudgetBytes();
        if (max <= min) return 0f;
        return Math.max(0f, Math.min(1f, (bytes - min) / (float) (max - min)));
    }

    private long bytesOf(float fraction) {
        long min = MemoryBudget.MIN_BUDGET_BYTES;
        long max = budget.maxBudgetBytes();
        return min + (long) (fraction * (max - min));
    }

    /* ── Open / close ────────────────────────────────────────────────── */

    public boolean isOpen() { return isOpen; }

    public void setOpen(boolean want) {
        if (isOpen == want) return;
        isOpen = want;
        if (want) t = fractionOf(budget.getBudgetBytes());

        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(open, want ? 1f : 0f);
        anim.setDuration(ANIM_MS);
        anim.setInterpolator(new PathInterpolator(0.32f, 0.72f, 0f, 1f));
        anim.addUpdateListener(a -> {
            open = (float) a.getAnimatedValue();
            requestLayout();
            invalidate();
        });
        anim.start();
        callbacks.onPanelOpenChanged(want);

        if (!want) {
            // Commit on release, so dragging does not churn the ballast.
            budget.setBudgetBytes(bytesOf(t));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float w = lerp(HANDLE_W * d, RADIUS * d, open);
        float h = lerp(HANDLE_H * d, RADIUS * 2 * d, open);
        setMeasuredDimension((int) Math.ceil(w), (int) Math.ceil(h));
    }

    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }

    /* ── Drawing ─────────────────────────────────────────────────────── */

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();

        if (open < 0.02f) {
            // Collapsed: a soft grab bar hugging the edge.
            fill.setColor(0x59FFFFFF);
            box.set(w - HANDLE_W * d, 0, w + HANDLE_W * d, h);
            canvas.drawRoundRect(box, HANDLE_W * d, HANDLE_W * d, fill);
            return;
        }

        float cx = w;                 // flat side pinned to the screen edge
        float cy = h / 2f;
        float radius = w;

        // Half-disc body.
        bodyPath.rewind();
        box.set(cx - radius, cy - radius, cx + radius, cy + radius);
        bodyPath.addArc(box, 90, 180);
        bodyPath.close();
        fill.setColor(0xF21A1A20);
        canvas.drawPath(bodyPath, fill);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1 * d);
        edge.setColor(0x2EFFFFFF);
        canvas.drawPath(bodyPath, edge);

        if (open < 0.55f) return;     // text only once there is room for it
        int alpha = (int) (255 * Math.min(1f, (open - 0.55f) / 0.45f));

        // Arc track: bottom → left → top is startAngle 90, sweep 180.
        float trackR = radius - TRACK_INSET * d;
        box.set(cx - trackR, cy - trackR, cx + trackR, cy + trackR);
        arc.setColor(withAlpha(0xFFFFFFFF, alpha / 6));
        canvas.drawArc(box, 90, 180, false, arc);

        // Filled portion.
        arc.setColor(withAlpha(colorForLoad(), alpha));
        canvas.drawArc(box, 90, 180 * t, false, arc);

        // Knob.
        double ang = Math.toRadians(90 + 180 * t);
        float kx = cx + (float) (trackR * Math.cos(ang));
        float ky = cy + (float) (trackR * Math.sin(ang));
        knob.setColor(withAlpha(0xFFFFFFFF, alpha));
        canvas.drawCircle(kx, ky, 8.5f * d, knob);
        knob.setColor(withAlpha(colorForLoad(), alpha));
        canvas.drawCircle(kx, ky, 4.5f * d, knob);

        // Readout. Right-aligned into the disc, clear of the curve.
        float textR = cx - 22 * d;
        long chosen = bytesOf(t);

        big.setAlpha(alpha);
        String head = (chosen / (1024 * 1024)) + " MB";
        canvas.drawText(head, textR - big.measureText(head), cy - 6 * d, big);

        small.setAlpha(alpha);
        String l1 = "budget";
        canvas.drawText(l1, textR - small.measureText(l1), cy - 30 * d, small);

        // Refresh the measured figure about twice a second; PSS is not free.
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastRefresh > 500) { lastRefresh = now; }
        long actual = budget.actualUsageBytes();

        tiny.setAlpha(alpha);
        String l2 = "actual " + MemoryBudget.mb(actual);
        canvas.drawText(l2, textR - tiny.measureText(l2), cy + 16 * d, tiny);

        String l3 = MemoryBudget.gb(budget.totalDeviceBytes()) + " device"
                + " · " + MemoryBudget.gb(MemoryBudget.OS_RESERVE_BYTES) + " reserved";
        canvas.drawText(l3, textR - tiny.measureText(l3), cy + 32 * d, tiny);

        if (budget.isShed()) {
            tiny.setColor(0xFFFF9F0A);
            String l4 = "shed — system low on memory";
            canvas.drawText(l4, textR - tiny.measureText(l4), cy + 48 * d, tiny);
            tiny.setColor(0x8AFFFFFF);
        }

        if (isOpen) postInvalidateOnAnimation();
    }

    private int colorForLoad() {
        // Green through amber to red as the dial approaches the device ceiling.
        if (t < 0.5f) return 0xFF30D158;
        if (t < 0.8f) return 0xFFFF9F0A;
        return 0xFFFF453A;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /* ── Touch ───────────────────────────────────────────────────────── */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            if (isOpen) setOpen(false);
            return false;
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                moved = false;
                draggingArc = isOpen;
                if (isOpen) updateFromTouch(e.getX(), e.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!moved && Math.hypot(e.getX() - downX, e.getY() - downY) > 8 * d) moved = true;
                if (draggingArc) {
                    updateFromTouch(e.getX(), e.getY());
                } else if (moved && downX - e.getX() > 18 * d) {
                    setOpen(true);                       // pulled left off the edge
                    draggingArc = true;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!moved && !draggingArc) performClick();          // tapped the tab
                else if (draggingArc && isOpen) budget.setBudgetBytes(bytesOf(t));
                draggingArc = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                draggingArc = false;
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        setOpen(true);
        return true;
    }

    /** Map a touch to a position on the arc. */
    private void updateFromTouch(float x, float y) {
        float cx = getWidth(), cy = getHeight() / 2f;
        double angle = Math.toDegrees(Math.atan2(y - cy, x - cx));   // -180..180
        // The arc runs 90 → 270 clockwise; normalise into that band.
        if (angle < 0) angle += 360;
        float frac = (float) ((angle - 90) / 180.0);
        t = Math.max(0f, Math.min(1f, frac));
        invalidate();
    }
}
