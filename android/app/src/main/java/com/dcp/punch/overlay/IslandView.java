package com.dcp.punch.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import com.dcp.punch.data.Gestures;
import com.dcp.punch.data.IslandStore;
import com.dcp.punch.data.Presentation;
import com.dcp.punch.mem.Appearance;

import java.util.ArrayList;
import java.util.List;

/**
 * The island itself, drawn on a Canvas.
 *
 * WHY NOT A WEBVIEW
 * An overlay window must be exactly the size of its touchable content, because
 * every pixel it covers is a pixel the app underneath cannot receive touches on.
 * The island changes size constantly, so the window is re-measured on every
 * animation frame — cheap for a custom View, ruinous for a WebView, which would
 * relayout its whole document 60 times a second and cost ~80 MB besides.
 *
 * GEOMETRY
 * Identical model to the web build: measure the content, animate width/height/
 * radius between two numbers, and let one interpolator carry the whole morph.
 * The interpolator is the same curve — cubic-bezier(.32,.72,0,1).
 *
 * THE CAMERA NEVER MOVES
 * The window is CENTER_HORIZONTAL and the island is centred inside it, so the
 * island's centre is always the screen's centre. The lens is drawn at a fixed
 * offset from the island's top edge, and --island-top is a per-variant constant.
 * No state change can shift the hole off the physical sensor.
 */
public class IslandView extends View {

    public interface Callbacks {
        /** The view's size changed; the window needs re-laying out. */
        void onGeometryChanged();
        /** Expanded state changed — the service adds/removes the dim behind. */
        void onExpandedChanged(boolean expanded);
        /** User asked to open the source app. */
        void onOpenContent(Presentation p);
    }

    /* ── Design constants, in dp ─────────────────────────────────────── */
    // Idle is a circle hugging the lens, not a bar: with nothing to show, the
    // island collapses back onto the camera cutout and reads as part of the
    // hardware. Variant A keeps its flat shoulders against the bezel, so its
    // chin radius is half its width and it still resolves as a circle.
    private static final float IDLE_A_W = 32, IDLE_A_H = 26, LENS_TOP_A = 7.5f;
    private static final float IDLE_B_W = 30, IDLE_B_H = 30, LENS_TOP_B = 9f;
    private static final float COMPACT_H = 37;
    private static final float LENS_SIZE = 12;
    private static final float CAMERA_GAP = 46;      // dead zone for the lens
    private static final float PAD_X = 14;
    private static final float EXP_W = 340;
    private static final float EXP_PAD_TOP = 30;     // clears the lens
    private static final float EXP_PAD_BOTTOM = 16;
    private static final float SHADOW_PAD = 20;      // room for the drop shadow
    private static final float MORPH_MS = 520;

    private static final int TOUCH_SLOP_DP = 9;
    private static final int SWIPE_MIN_DP = 44;
    private static final long HOLD_MS = 420;

    private final float d;                            // dp → px
    private final IslandStore store;
    private final Callbacks callbacks;
    private final Appearance look;
    private final Gestures gestures;
    private String variant;

    /** Time of the last completed tap, for telling a double tap from two taps. */
    private long lastTapAt;
    private static final long DOUBLE_TAP_MS = 260;

    /* ── Animated geometry ───────────────────────────────────────────── */
    private float curW, curH, curR;
    private float fromW, fromH, fromR;
    private float tgtW, tgtH, tgtR;
    private ValueAnimator morph;
    /** The three cancellable pieces of a close; see cancelClose(). */
    private ValueAnimator closeContentFade, closeViewFade;
    private Runnable closeHandoff;
    private float contentAlpha = 1f;
    private float pressScale = 1f;

    /** The presentation currently drawn — lags the store during a crossfade. */
    private Presentation shown;

    /* ── Paints ──────────────────────────────────────────────────────── */
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Glow only. Kept apart from `body` so the two shadow layers never fight. */
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint compactText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r1 = new RectF();
    // Preallocated: onDraw runs at 60fps for as long as the theme is enabled,
    // so anything allocated here is garbage generated forever.
    private final android.graphics.Path bezelPath = new android.graphics.Path();

    /* ── Touch state ─────────────────────────────────────────────────── */
    private float downX, downY;
    private boolean moved, held;
    private long downAt;
    private final Runnable holdRunnable;

    /** Hit rects for the expanded action buttons, rebuilt each draw. */
    private final List<RectF> actionRects = new ArrayList<>();

    public IslandView(Context ctx, IslandStore store, String variant, Callbacks cb) {
        super(ctx);
        this.store = store;
        this.variant = variant;
        this.callbacks = cb;
        this.look = Appearance.get(ctx);
        this.gestures = Gestures.get(ctx);
        this.d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                ctx.getResources().getDisplayMetrics());

        this.holdRunnable = () -> {
            if (moved) return;
            held = true;
            haptic(android.view.HapticFeedbackConstants.LONG_PRESS);
            run(gestures.actionFor(Gestures.Gesture.LONG_PRESS));
        };

        setLayerType(LAYER_TYPE_HARDWARE, null);

        body.setColor(Color.BLACK);
        applySurface();
        // shadowLayer needs software rendering for the blur on some drivers;
        // hardware layers handle it since API 28, and below that it degrades to
        // a hard edge rather than failing.
        track.setColor(0x2EFFFFFF);
        waveStroke.setStyle(Paint.Style.STROKE);
        waveStroke.setStrokeCap(Paint.Cap.ROUND);
        waveStroke.setStrokeJoin(Paint.Join.ROUND);
        lensRing.setStyle(Paint.Style.STROKE);
        lensRing.setStrokeWidth(1.5f * d);

        compactText.setColor(Color.WHITE);
        compactText.setTextSize(12.5f * d);
        compactText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        titleText.setColor(Color.WHITE);
        titleText.setTextSize(15f * d);
        titleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        subText.setColor(0x8CFFFFFF);
        subText.setTextSize(12.5f * d);

        computeTarget();
        curW = tgtW; curH = tgtH; curR = tgtR;
    }

    /**
     * Re-read the appearance dials. Called when the control panel changes them,
     * so a running overlay updates without being torn down and rebuilt.
     */
    public void applyAppearance() {
        applySurface();
        computeTarget();
        curW = tgtW; curH = tgtH; curR = tgtR;
        requestLayout();
        invalidate();
    }

    private void applySurface() {
        // Background opacity multiplies whatever alpha the chosen colour already
        // carries, rather than replacing it, so a deliberately translucent
        // custom colour is not silently forced back to solid by a dial that is
        // sitting at 100.
        int bg = look.get(Appearance.BG_COLOR);
        int baseAlpha = (bg >>> 24) == 0 ? 255 : (bg >>> 24);
        int alpha = Math.round(baseAlpha * look.bgOpacity());
        body.setColor((bg & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24));

        float sh = look.shadowAlpha();
        if (sh > 0.02f) {
            body.setShadowLayer(14 * d, 0, 6 * d, ((int) (255 * sh)) << 24);
        } else {
            body.clearShadowLayer();
        }
        // Text scale is applied here rather than at each draw so the paints are
        // measured at their final size — ellipsizing against a paint that is
        // about to be resized is how labels end up cut a character short.
        float ts = look.textScale();
        compactText.setTextSize(12.5f * d * ts);
        titleText.setTextSize(15f * d * ts);
        subText.setTextSize(12.5f * d * ts);

        int bw = look.get(Appearance.BORDER_WIDTH);
        lensRing.setStrokeWidth(1.5f * d);
        outlineWidth = bw * d;
        outlineColour = look.get(Appearance.OUTLINE_COLOR);
    }

    private float outlineWidth;
    private int outlineColour;

    public void setVariant(String v) {
        this.variant = v;
        applyState(true);
    }

    /** Top offset of the island from the screen edge — a per-variant constant. */
    public float islandTopPx() {
        return isA() ? 0 : 11 * d;
    }

    public int shadowPadPx() { return Math.round(SHADOW_PAD * d); }

    private boolean isA() { return "a".equals(variant); }

    /* ══════════════════════════════════════════════════════════════════
       GEOMETRY
       ═════════════════════════════════════════════════════════════════ */

    /** Recompute the target box for the store's current state. */
    private void computeTarget() {
        Presentation p = shown;
        float maxW = maxWidthPx();

        float scale = look.idleScale();

        if (p == null) {
            tgtW = (isA() ? IDLE_A_W : IDLE_B_W) * d * scale;
            tgtH = (isA() ? IDLE_A_H : IDLE_B_H) * d * scale;
            tgtR = isA() ? Math.min(tgtH * 0.62f, 18 * d) : tgtH / 2f;
            return;
        }

        if (store.isExpanded()) {
            tgtW = Math.min(look.get(Appearance.EXP_WIDTH) * d, maxW);
            tgtH = expandedHeight(p, tgtW);
            tgtR = look.get(Appearance.EXP_RADIUS) * d;
            return;
        }

        tgtH = COMPACT_H * d;
        // A corner radius of half the height is a full pill, which is the
        // default; anything smaller squares it off. The dial can never exceed
        // half the height, or the shape would stop being drawable.
        int rDial = look.get(Appearance.CORNER_RADIUS);
        tgtR = Math.min(tgtH / 2f, rDial * d);
        if (rDial <= 0) tgtR = tgtH / 2f;

        // Symmetric slots: the camera gap has to land on the pill's centre,
        // because that is where the hole is. Padding both sides out to
        // max(lead, trail) is what puts it there.
        float lead = leadWidth(p);
        float trail = trailWidth(p);
        float gap = CAMERA_GAP * d;
        float maxHalf = Math.max(18 * d, (maxW - PAD_X * 2 * d - gap) / 2f);
        float half = Math.min(maxHalf, Math.max(lead, trail));

        float idleW = (isA() ? IDLE_A_W : IDLE_B_W) * d;
        tgtW = Math.max(idleW, Math.min(half * 2 + gap + PAD_X * 2 * d, maxW));
    }

    private float maxWidthPx() {
        int screen = getResources().getDisplayMetrics().widthPixels;
        return screen - 28 * d;
    }

    private float leadWidth(Presentation p) {
        if (p.art != null) return 24 * d;
        return 22 * d;
    }

    private float trailWidth(Presentation p) {
        switch (p.motif) {
            case EQUALISER: return 17 * d;
            case WAVEFORM:  return 26 * d;
            case PULSE:     return 9 * d;
            case RING:      return 26 * d + (TextUtils.isEmpty(p.compactText) ? 0
                                : compactText.measureText(p.compactText) + 6 * d);
            default: break;
        }
        if (TextUtils.isEmpty(p.compactText)) return 22 * d;
        return Math.min(compactText.measureText(p.compactText), 150 * d);
    }

    /**
     * Must mirror drawExpanded exactly. Every dial that changes a row's height
     * has to be read here too — a header that grew and a box that did not is
     * how content gets clipped, and it is invisible until someone opens the one
     * card that overflows.
     */
    private float expandedHeight(Presentation p, float w) {
        float h = EXP_PAD_TOP * d;
        h += Math.max(28 * d, look.get(Appearance.ART_SIZE) * d + 14 * d);   // header block
        if (p.progress >= 0 && look.flag(Appearance.SHOW_PROGRESS)) {
            h += 13 * d + 8 * d;                                             // gap + bar row
        }
        if (p.actions.length > 0) {
            h += 13 * d + look.get(Appearance.BUTTON_SIZE) * d;              // gap + buttons
        }
        h += EXP_PAD_BOTTOM * d;
        return h;
    }

    /* ══════════════════════════════════════════════════════════════════
       STATE → ANIMATION
       ═════════════════════════════════════════════════════════════════ */

    /**
     * Adopt the store's state. Order matters and matches the web build: fade the
     * old content out, swap and remeasure, then morph and fade in. Measuring
     * while the old content is still up would size the island to the wrong thing.
     */
    public void applyState(boolean animate) {
        Presentation next = store.current();

        if (!animate) {
            shown = next;
            computeTarget();
            curW = tgtW; curH = tgtH; curR = tgtR;
            contentAlpha = 1f;
            callbacks.onGeometryChanged();
            requestLayout();
            invalidate();
            return;
        }

        boolean sameContent = shown == next;
        if (sameContent) {
            // Same presentation, new geometry (e.g. expand/collapse): morph
            // without touching the content.
            startMorph();
            return;
        }

        animate(contentAlpha, 0f, 110, a -> {
            contentAlpha = a;
            invalidate();
        }, () -> {
            shown = next;
            startMorph();
            animate(0f, 1f, 190, a -> { contentAlpha = a; invalidate(); }, null);
        });
    }

    /**
     * Abandon a close in progress.
     *
     * A notification arriving while the island is on its way out would otherwise
     * be drawn by a view whose fade-out animator is still running, and the two
     * would fight all the way to alpha 0 — the island would appear and then
     * silently vanish. Every part of the close has to be cancellable: the
     * content fade, the delayed hand-off, and the view fade.
     */
    private void cancelClose() {
        removeCallbacks(singleTap);
        if (closeContentFade != null) { closeContentFade.cancel(); closeContentFade = null; }
        if (closeViewFade != null) { closeViewFade.cancel(); closeViewFade = null; }
        if (closeHandoff != null) { removeCallbacks(closeHandoff); closeHandoff = null; }
        animate().cancel();
    }

    /** Snap to the idle cutout with no animation, ready to be shown. */
    public void resetToIdle() {
        cancelClose();
        if (morph != null) morph.cancel();
        shown = null;
        contentAlpha = 1f;
        computeTarget();
        curW = tgtW; curH = tgtH; curR = tgtR;
        requestLayout();
        invalidate();
    }

    /**
     * Play the closing move, then hand back.
     *
     * Same shape as the demo: the content fades, the island shrinks back into
     * the camera hole on the morph curve, and only once it is a circle again
     * does the whole thing fade out. Collapsing and vanishing at the same time
     * reads as a glitch; collapsing *then* vanishing reads as the island going
     * back into the hardware.
     */
    public void playClose(Runnable onDone) {
        cancelClose();
        if (morph != null) morph.cancel();

        closeContentFade = animate(contentAlpha, 0f, 120,
                a -> { contentAlpha = a; invalidate(); }, () -> {
            closeContentFade = null;
            shown = null;
            startMorph();
            // Let the shrink land before the fade begins, or the two read as one
            // muddy dissolve instead of a collapse.
            closeHandoff = () -> {
                closeHandoff = null;
                closeViewFade = animate(1f, 0f, 150, this::setAlpha, () -> {
                    closeViewFade = null;
                    onDone.run();
                });
            };
            postDelayed(closeHandoff, (long) (MORPH_MS * 0.62f));
        });
    }

    /** Release anything rebuildable. Registered with MemoryBudget. */
    public void trim(boolean hard) {
        lens.setShader(null);            // regenerated on the next draw
        if (hard) {
            setLayerType(LAYER_TYPE_NONE, null);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }
        invalidate();
    }

    private void startMorph() {
        computeTarget();
        if (morph != null) morph.cancel();

        fromW = curW; fromH = curH; fromR = curR;
        if (Math.abs(fromW - tgtW) < 0.5f && Math.abs(fromH - tgtH) < 0.5f) {
            curW = tgtW; curH = tgtH; curR = tgtR;
            requestLayout();
            invalidate();
            return;
        }

        morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(Math.max(1, (long) (MORPH_MS * look.animScale())));
        float tension = look.bounceTension();
        morph.setInterpolator(tension <= 0.01f
                ? new PathInterpolator(0.32f, 0.72f, 0f, 1f)
                : new android.view.animation.OvershootInterpolator(tension));
        morph.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            curW = lerp(fromW, tgtW, t);
            curH = lerp(fromH, tgtH, t);
            curR = lerp(fromR, tgtR, t);
            // The window follows the view, so every frame is a re-measure.
            requestLayout();
            invalidate();
        });
        morph.start();
        callbacks.onGeometryChanged();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /**
     * Returns the animator so a caller that may need to abandon it can hold on.
     * onEnd deliberately does not fire on cancel — a cancelled close must not
     * run the hand-off that tears the window down.
     */
    private ValueAnimator animate(float from, float to, long ms,
                                  java.util.function.Consumer<Float> onUpdate, Runnable onEnd) {
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(ms);
        va.addUpdateListener(a -> onUpdate.accept((Float) a.getAnimatedValue()));
        if (onEnd != null) {
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(android.animation.Animator a) {
                    cancelled = true;
                }
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    if (!cancelled) onEnd.run();
                }
            });
        }
        va.start();
        return va;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int pad = shadowPadPx();
        setMeasuredDimension(
                (int) Math.ceil(curW) + pad * 2,
                (int) Math.ceil(curH) + pad);      // no top pad: island hugs the edge
    }

    /* ══════════════════════════════════════════════════════════════════
       DRAWING
       ═════════════════════════════════════════════════════════════════ */

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float left = cx - curW / 2f;
        float top = 0;

        canvas.save();
        if (pressScale != 1f) canvas.scale(pressScale, pressScale, cx, top + curH / 2f);

        // Body. The resting-opacity dial applies only when there is nothing to
        // show — a pill carrying content is always solid, or it is unreadable.
        boolean resting = shown == null;
        body.setAlpha(resting
                ? (int) (255 * Math.max(look.collapsedAlpha(), 0f))
                : 255);

        // Glow, behind everything: a soft halo in whatever colour the source
        // app gave its notification. Drawn as a blurred shadow on a throwaway
        // paint rather than a real blur, which would cost a render pass.
        if (shown != null && look.flag(Appearance.GLOW) && contentAlpha > 0.01f) {
            glow.setColor(0x00000000);
            glow.setShadowLayer(20 * d, 0, 0,
                    (shown.accent & 0x00FFFFFF) | ((int) (110 * contentAlpha) << 24));
            r1.set(left + 6 * d, top + 4 * d, left + curW - 6 * d, top + curH - 2 * d);
            canvas.drawRoundRect(r1, curR, curR, glow);
        }

        r1.set(left, top, left + curW, top + curH);
        if (isA()) {
            // Square shoulders against the bezel, rounded chin. Drawn as a path
            // so the top corners stay flat at every radius.
            bezelPath.rewind();
            bezelPath.moveTo(r1.left, r1.top);
            bezelPath.lineTo(r1.right, r1.top);
            bezelPath.lineTo(r1.right, r1.bottom - curR);
            bezelPath.quadTo(r1.right, r1.bottom, r1.right - curR, r1.bottom);
            bezelPath.lineTo(r1.left + curR, r1.bottom);
            bezelPath.quadTo(r1.left, r1.bottom, r1.left, r1.bottom - curR);
            bezelPath.close();
            canvas.drawPath(bezelPath, body);
        } else {
            canvas.drawRoundRect(r1, curR, curR, body);
        }

        if (outlineWidth > 0.01f) {
            lensRing.setColor(outlineColour);
            lensRing.setStrokeWidth(outlineWidth);
            if (isA()) canvas.drawPath(bezelPath, lensRing);
            else canvas.drawRoundRect(r1, curR, curR, lensRing);
            lensRing.setStrokeWidth(1.5f * d);
        }

        // Content
        if (shown != null && contentAlpha > 0.01f) {
            int alpha = (int) (255 * contentAlpha);
            canvas.save();
            canvas.clipRect(r1);
            if (store.isExpanded()) drawExpanded(canvas, r1, shown, alpha);
            else drawCompact(canvas, r1, shown, alpha);
            canvas.restore();
        }

        // The lens sits on top of everything, at its fixed physical position.
        drawLens(canvas, cx, top + (isA() ? LENS_TOP_A : LENS_TOP_B) * d);

        canvas.restore();

        // Keep the loop alive only while something is actually moving.
        if (shown != null && shown.animating && contentAlpha > 0.01f) {
            postInvalidateOnAnimation();
        }
    }

    private void drawLens(Canvas canvas, float cx, float top) {
        float r = LENS_SIZE * d / 2f;
        float cy = top + r;
        if (lens.getShader() == null) {
            lens.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.6f,
                    new int[]{0xFF2C2F3A, 0xFF0D0E12, 0xFF000000},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        }
        canvas.drawCircle(cx, cy, r, lens);
        lensRing.setColor(0x1AFFFFFF);
        canvas.drawCircle(cx, cy, r, lensRing);
        // Catch-light
        chip.setColor(0x61A0B4FF);
        canvas.drawCircle(cx - r * 0.32f, cy - r * 0.34f, r * 0.28f, chip);
    }

    /** Compact: leading slot · camera gap · trailing slot, all centred. */
    private void drawCompact(Canvas canvas, RectF box, Presentation p, int alpha) {
        float gap = CAMERA_GAP * d;
        float half = (box.width() - PAD_X * 2 * d - gap) / 2f;
        float leadL = box.left + PAD_X * d;
        float trailR = box.right - PAD_X * d;
        float cy = box.centerY();

        // Leading: album art if we have it, else an accent chip with the icon.
        if (p.art != null) {
            drawArt(canvas, p.art, leadL, cy - 12 * d, 24 * d, 7 * d, alpha);
        } else {
            drawChip(canvas, p, leadL + 11 * d, cy, 11 * d, alpha);
        }

        // Trailing.
        switch (p.motif) {
            case EQUALISER:
                drawEqualiser(canvas, trailR - 17 * d, cy, p.accent, alpha);
                return;
            case PULSE:
                drawPulse(canvas, trailR - 4.5f * d, cy, p.accent, alpha);
                return;
            case WAVEFORM:
                drawWaveform(canvas, trailR - 26 * d, cy, p.accent, alpha);
                return;
            default:
                break;
        }

        if (!TextUtils.isEmpty(p.compactText)) {
            compactText.setAlpha(alpha);
            CharSequence s = TextUtils.ellipsize(p.compactText, compactText, half, TextUtils.TruncateAt.END);
            float tw = compactText.measureText(s, 0, s.length());
            float baseline = cy - (compactText.descent() + compactText.ascent()) / 2f;
            canvas.drawText(s, 0, s.length(), trailR - tw, baseline, compactText);
        }
    }

    private void drawExpanded(Canvas canvas, RectF box, Presentation p, int alpha) {
        float padX = PAD_X * d;
        float fullL = box.left + padX, fullR = box.right - padX;
        float y = box.top + EXP_PAD_TOP * d;

        // Header: art / icon, then two lines of text.
        float artSize = Math.max(28 * d, look.get(Appearance.ART_SIZE) * d + 14 * d);
        float artRadius = look.get(Appearance.ART_RADIUS) * d;

        /* Centre the content block on the island's own axis.
           The art and the text are measured as one unit and that unit is
           centred, rather than the art being pinned to the left padding with
           the text trailing off it. With short content — a brief track title,
           an app name — a left-pinned block sits visibly off to one side while
           the action row underneath is centred, and the two rows disagree.
           Measuring and centring puts every row of the expanded view on the
           same axis. Content wider than the island simply fills the padded
           span, so nothing is ever squeezed to achieve it. */
        float blockW = Math.min(artSize + 12 * d + widestLine(p), fullR - fullL);
        float left = box.centerX() - blockW / 2f;
        float right = left + blockW;
        if (p.art != null) {
            drawArt(canvas, p.art, left, y, artSize, artRadius, alpha);
        } else {
            chip.setColor(withAlpha(p.accent, alpha));
            r1.set(left, y, left + artSize, y + artSize);
            canvas.drawRoundRect(r1, 13 * d, 13 * d, chip);
            drawIcon(canvas, p, left + artSize / 2f, y + artSize / 2f, artRadius, Color.WHITE, alpha);
        }

        float textL = left + artSize + 12 * d;
        float textR = right;

        // Trailing value (a percentage, an ETA) never shrinks; the titles do.
        String trailing = trailingValue(p);
        if (trailing != null) {
            compactText.setAlpha(alpha);
            compactText.setTextSize(15 * d);
            float tw = compactText.measureText(trailing);
            canvas.drawText(trailing, right - tw, y + 20 * d, compactText);
            compactText.setTextSize(12.5f * d);
            textR = right - tw - 10 * d;
        }

        float avail = Math.max(20 * d, textR - textL);
        titleText.setAlpha(alpha);
        CharSequence t = TextUtils.ellipsize(p.title, titleText, avail, TextUtils.TruncateAt.END);
        canvas.drawText(t, 0, t.length(), textL, y + 20 * d, titleText);

        if (!TextUtils.isEmpty(p.subtitle)) {
            subText.setAlpha((int) (alpha * 0.55f));
            CharSequence s = TextUtils.ellipsize(p.subtitle, subText, avail, TextUtils.TruncateAt.END);
            canvas.drawText(s, 0, s.length(), textL, y + 38 * d, subText);
        }

        y += artSize;

        if (p.progress >= 0 && look.flag(Appearance.SHOW_PROGRESS)) {
            y += 13 * d;

            float barL = left, barR = right;
            boolean times = look.flag(Appearance.SHOW_TIMES) && p.trackDurationMs > 0;

            // Elapsed on the left, remaining on the right as a negative — the
            // convention every music player uses, and the reason the remaining
            // side is the more useful of the two.
            if (times) {
                compactText.setAlpha((int) (alpha * 0.6f));
                String elapsed = clock(p.trackPositionMs);
                String left2 = "-" + clock(Math.max(0, p.trackDurationMs - p.trackPositionMs));
                float ew = compactText.measureText(elapsed);
                float lw = compactText.measureText(left2);
                float baseline = y + 4 * d - (compactText.descent() + compactText.ascent()) / 2f;
                canvas.drawText(elapsed, barL, baseline, compactText);
                canvas.drawText(left2, barR - lw, baseline, compactText);
                barL += ew + 10 * d;
                barR -= lw + 10 * d;
            }

            int fill = withAlpha(p.accent == 0 ? Color.WHITE : p.accent, alpha);
            if (look.get(Appearance.PROGRESS_STYLE) == 1) {
                drawWavyProgress(canvas, barL, barR, y + 4 * d, p.progress, fill, alpha);
            } else {
                float h = 4 * d;
                r1.set(barL, y + 2 * d, barR, y + 2 * d + h);
                track.setAlpha(alpha / 4);
                canvas.drawRoundRect(r1, h / 2f, h / 2f, track);
                chip.setColor(fill);
                r1.set(barL, y + 2 * d, barL + (barR - barL) * p.progress, y + 2 * d + h);
                canvas.drawRoundRect(r1, h / 2f, h / 2f, chip);
            }
            y += 8 * d;
        }

        actionRects.clear();
        if (p.actions.length > 0) {
            y += 13 * d;
            float size = look.get(Appearance.BUTTON_SIZE) * d;
            int n = p.actions.length;
            float spacing = Math.max(12 * d, size * 0.62f);
            float totalW = n * size + (n - 1) * spacing;

            // The row must fit inside the padded span, or the outermost button
            // is drawn past the window edge and simply is not there — which is
            // exactly what a missing "previous" looks like. Squeeze the spacing
            // before letting that happen.
            float span = right - left;
            if (totalW > span && n > 1) {
                spacing = Math.max(6 * d, (span - n * size) / (n - 1));
                totalW = n * size + (n - 1) * spacing;
            }

            float x = box.centerX() - totalW / 2f;
            for (int i = 0; i < n; i++) {
                Presentation.Action a = p.actions[i];
                RectF rect = new RectF(x, y, x + size, y + size);
                actionRects.add(rect);
                int aAlpha = a.enabled ? alpha : (int) (alpha * 0.32f);
                chip.setColor(withAlpha(0xFFFFFFFF, (int) (aAlpha * 0.13f)));
                canvas.drawOval(rect, chip);
                drawActionIcon(canvas, a, rect, aAlpha);
                x += size + spacing;
            }
        }
    }

    /**
     * Buzz, if the user wants buzzing.
     *
     * Routed through one place so the switch is honoured everywhere rather than
     * at whichever call sites happened to remember it.
     */
    private void haptic(int constant) {
        if (look.flag(Appearance.HAPTICS)) performHapticFeedback(constant);
    }

    /** m:ss, the only format a track position is ever wanted in. */
    private static String clock(long ms) {
        long total = Math.max(0, ms) / 1000L;
        return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60);
    }

    /**
     * The played part of the bar as a wave, the unplayed part flat.
     *
     * Sampled per pixel-ish step rather than as a Path of curves: the amplitude
     * has to fall to zero right at the playhead so the wave resolves into the
     * flat line, and that is a property of each sample rather than of a segment.
     */
    private void drawWavyProgress(Canvas canvas, float l, float r, float cy,
                                  float progress, int fill, int alpha) {
        float w = r - l;
        if (w <= 0) return;
        float head = l + w * Math.max(0f, Math.min(1f, progress));

        track.setAlpha(alpha / 4);
        track.setStrokeWidth(3 * d);
        track.setStyle(Paint.Style.STROKE);
        canvas.drawLine(head, cy, r, cy, track);
        track.setStyle(Paint.Style.FILL);

        wavePath.rewind();
        float amp = 2.6f * d, len = 13 * d, step = 1.5f * d;
        boolean started = false;
        for (float x = l; x <= head; x += step) {
            // Taper the last few points so the wave meets the flat line instead
            // of stopping mid-swing.
            float taper = Math.min(1f, (head - x) / (len * 0.9f));
            float yy = cy + (float) Math.sin((x - l) / len * Math.PI * 2f) * amp * taper;
            if (!started) { wavePath.moveTo(x, yy); started = true; }
            else wavePath.lineTo(x, yy);
        }
        if (started) {
            waveStroke.setColor(fill);
            waveStroke.setStrokeWidth(3 * d);
            canvas.drawPath(wavePath, waveStroke);
        }
        chip.setColor(fill);
        canvas.drawCircle(head, cy, 3.2f * d, chip);
    }

    /** Preallocated, like every other paint here: onDraw runs at 60fps. */
    private final android.graphics.Path wavePath = new android.graphics.Path();
    private final Paint waveStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * How wide the text column wants to be, so the header can be centred as a
     * unit. Measured, not guessed — the two lines use different paints and the
     * trailing value uses a third size.
     */
    private float widestLine(Presentation p) {
        float w = 0;
        if (!TextUtils.isEmpty(p.title)) w = titleText.measureText(p.title);
        if (!TextUtils.isEmpty(p.subtitle)) w = Math.max(w, subText.measureText(p.subtitle));

        String trailing = trailingValue(p);
        if (trailing != null) {
            float was = compactText.getTextSize();
            compactText.setTextSize(15 * d);
            w += compactText.measureText(trailing) + 10 * d;
            compactText.setTextSize(was);
        }
        return w;
    }

    private String trailingValue(Presentation p) {
        if (p.progress >= 0 && p.motif != Presentation.Motif.EQUALISER) {
            return Math.round(p.progress * 100) + "%";
        }
        return null;
    }

    private void drawChip(Canvas canvas, Presentation p, float cx, float cy, float r, int alpha) {
        chip.setColor(withAlpha(p.accent, alpha));
        canvas.drawCircle(cx, cy, r, chip);
        drawIcon(canvas, p, cx, cy, 6.5f * d, Color.WHITE, alpha);
    }

    private void drawIcon(Canvas canvas, Presentation p, float cx, float cy, float half,
                          int tint, int alpha) {
        Drawable icon = p.icon;
        if (icon == null) return;
        icon.mutate();
        icon.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
        icon.setAlpha(alpha);
        icon.setBounds((int) (cx - half), (int) (cy - half), (int) (cx + half), (int) (cy + half));
        icon.draw(canvas);
    }

    private void drawActionIcon(Canvas canvas, Presentation.Action a, RectF rect, int alpha) {
        if (a.iconRes == 0) {
            // Notification actions have no icon we can trust; use the initial.
            compactText.setAlpha(alpha);
            String s = a.label.isEmpty() ? "?" : a.label.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
            float tw = compactText.measureText(s);
            canvas.drawText(s, rect.centerX() - tw / 2f,
                    rect.centerY() - (compactText.descent() + compactText.ascent()) / 2f, compactText);
            return;
        }
        Drawable dr = getContext().getDrawable(a.iconRes);
        if (dr == null) return;
        dr.mutate();
        dr.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        dr.setAlpha(alpha);
        float h = 9.5f * d;
        dr.setBounds((int) (rect.centerX() - h), (int) (rect.centerY() - h),
                (int) (rect.centerX() + h), (int) (rect.centerY() + h));
        dr.draw(canvas);
    }

    private final Matrix artMatrix = new Matrix();
    private Bitmap artShaderFor;
    private BitmapShader artShader;

    private void drawArt(Canvas canvas, Bitmap bmp, float l, float t, float size, float radius, int alpha) {
        r1.set(l, t, l + size, t + size);
        Matrix m = artMatrix;
        m.reset();
        float scale = size / Math.min(bmp.getWidth(), bmp.getHeight());
        m.setScale(scale, scale);
        m.postTranslate(l - (bmp.getWidth() * scale - size) / 2f,
                        t - (bmp.getHeight() * scale - size) / 2f);
        // One shader per bitmap, not per frame; the art only changes on a track change.
        if (artShaderFor != bmp || artShader == null) {
            artShader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            artShaderFor = bmp;
        }
        artShader.setLocalMatrix(m);
        chip.setShader(artShader);
        chip.setAlpha(alpha);
        canvas.drawRoundRect(r1, radius, radius, chip);
        chip.setShader(null);
        chip.setAlpha(255);
    }

    /* ── Motifs ──────────────────────────────────────────────────────── */

    private void drawEqualiser(Canvas canvas, float l, float cy, int color, int alpha) {
        float bw = 2.5f * d, gap = 2 * d, maxH = 15 * d;
        chip.setColor(withAlpha(color, alpha));
        long t = SystemClock.uptimeMillis();
        for (int i = 0; i < 4; i++) {
            // Four bars on staggered periods — the classic "audio is live" tell.
            double phase = (t % 900) / 900.0 * Math.PI * 2 + i * 1.7;
            float amp = (float) (0.32 + 0.68 * (0.5 + 0.5 * Math.sin(phase)));
            float h = maxH * amp;
            float x = l + i * (bw + gap);
            r1.set(x, cy + maxH / 2f - h, x + bw, cy + maxH / 2f);
            canvas.drawRoundRect(r1, bw / 2f, bw / 2f, chip);
        }
    }

    private void drawWaveform(Canvas canvas, float l, float cy, int color, int alpha) {
        float bw = 2.5f * d, gap = 2 * d, maxH = 20 * d;
        chip.setColor(withAlpha(color, alpha));
        long t = SystemClock.uptimeMillis();
        for (int i = 0; i < 6; i++) {
            double phase = (t % 1100) / 1100.0 * Math.PI * 2 + i * 0.9;
            float h = maxH * (float) (0.18 + 0.82 * Math.abs(Math.sin(phase)));
            float x = l + i * (bw + gap);
            r1.set(x, cy - h / 2f, x + bw, cy + h / 2f);
            canvas.drawRoundRect(r1, bw / 2f, bw / 2f, chip);
        }
    }

    private void drawPulse(Canvas canvas, float cx, float cy, int color, int alpha) {
        long t = SystemClock.uptimeMillis() % 1600;
        float k = (float) (0.35 + 0.65 * (0.5 + 0.5 * Math.cos(t / 1600.0 * Math.PI * 2)));
        chip.setColor(withAlpha(color, (int) (alpha * k)));
        canvas.drawCircle(cx, cy, 4.5f * d * (0.82f + 0.18f * k), chip);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /* ══════════════════════════════════════════════════════════════════
       TOUCH
       ═════════════════════════════════════════════════════════════════ */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Fired because the window sets FLAG_WATCH_OUTSIDE_TOUCH: a tap anywhere
        // else on screen collapses, exactly like tapping outside on iOS.
        if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            if (store.isExpanded()) store.setExpanded(false);
            return false;
        }
        if (store.current() == null) return false;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); downAt = SystemClock.uptimeMillis();
                moved = false; held = false;
                setPressed(true);
                postDelayed(holdRunnable, HOLD_MS);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!moved && Math.hypot(dx, dy) > TOUCH_SLOP_DP * d) {
                    moved = true;
                    removeCallbacks(holdRunnable);
                    setPressed(false);
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                removeCallbacks(holdRunnable);
                setPressed(false);
                float dx = e.getX() - downX, dy = e.getY() - downY;

                if (held) return true;                       // already expanded

                if (!moved) {
                    tapX = e.getX(); tapY = e.getY();
                    handleTap();
                    return true;
                }
                if (Math.abs(dx) > SWIPE_MIN_DP * d && Math.abs(dx) > Math.abs(dy)) {
                    run(gestures.actionFor(dx < 0
                            ? Gestures.Gesture.SWIPE_LEFT
                            : Gestures.Gesture.SWIPE_RIGHT));
                } else if (dy > 26 * d && !store.isExpanded()) {
                    store.setExpanded(true);                 // swipe ↓
                } else if (dy < -26 * d && store.isExpanded()) {
                    store.setExpanded(false);                // swipe ↑
                }
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(holdRunnable);
                setPressed(false);
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    private float tapX, tapY;
    /* A stable instance, not a fresh lambda per call: removeCallbacks needs the
       same object it was posted with, or a pending single tap survives the
       double tap that was supposed to cancel it. A method reference to an
       instance method is also the one form that does not read `gestures` while
       the field initialisers are still running. */
    private final Runnable singleTap = this::fireSingleTap;

    private void fireSingleTap() {
        run(gestures.actionFor(Gestures.Gesture.TAP));
    }

    /**
     * Decide between one tap and two.
     *
     * A double tap can only be recognised by waiting to see whether a second one
     * arrives, and that wait is latency on every single tap. So it is only paid
     * when it buys something: if the double-tap gesture is set to do nothing,
     * the single tap fires immediately. Material Capsule warns about the same
     * trade-off in its own settings, and it is worth being explicit about.
     */
    private void handleTap() {
        // Announce the click once, here, before anything branches: accessibility
        // services and any registered listener need it on every tap, and the
        // deferred single-tap path would otherwise skip it for double taps and
        // for presses on the expanded view's own buttons.
        performClick();

        // A tap on an action button in the expanded view is not a gesture at
        // all — it is a button press, and it always wins.
        Presentation p = shown;
        if (p != null && store.isExpanded()) {
            for (int i = 0; i < actionRects.size() && i < p.actions.length; i++) {
                if (actionRects.get(i).contains(tapX, tapY)) {
                    Presentation.Action a = p.actions[i];
                    if (a.run != null) a.run.run();
                    return;
                }
            }
        }

        boolean doubleWanted =
                gestures.actionFor(Gestures.Gesture.DOUBLE_TAP) != Gestures.Action.NOTHING;
        long now = SystemClock.uptimeMillis();

        if (doubleWanted && now - lastTapAt < DOUBLE_TAP_MS) {
            removeCallbacks(singleTap);
            lastTapAt = 0;
            run(gestures.actionFor(Gestures.Gesture.DOUBLE_TAP));
            return;
        }
        lastTapAt = now;

        if (doubleWanted) postDelayed(singleTap, DOUBLE_TAP_MS);
        else singleTap.run();
    }

    @Override
    public boolean performClick() {
        return super.performClick();   // accessibility + any click listeners
    }

    /** Carry out one configured action. */
    private void run(Gestures.Action action) {
        Presentation p = shown;
        switch (action) {
            case NOTHING:
                return;
            case EXPAND:
                haptic(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                store.setExpanded(true);
                return;
            case COLLAPSE:
                store.setExpanded(false);
                return;
            case DISMISS:
                store.dismissCurrent();
                return;
            case SWAP:
                store.swap();
                return;
            case PLAY_PAUSE:
            case NEXT_TRACK:
            case PREVIOUS_TRACK: {
                if (p == null) return;
                // The transport controls arrive as the presentation's own
                // actions, supplied by MediaMonitor in a fixed order:
                // previous, play/pause, next.
                int index = action == Gestures.Action.PREVIOUS_TRACK ? 0
                        : action == Gestures.Action.NEXT_TRACK ? 2 : 1;
                if (p.actions != null && p.actions.length == 3
                        && p.actions[index] != null && p.actions[index].run != null) {
                    p.actions[index].run.run();
                } else if (p.tapAction != null && p.tapAction.run != null) {
                    p.tapAction.run.run();      // whatever the source called primary
                }
                return;
            }
            default: {
                if (p == null) return;
                if (p.tapAction != null && p.tapAction.run != null) p.tapAction.run.run();
                else callbacks.onOpenContent(p);
            }
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        float target = pressed ? 0.965f : 1f;
        if (pressScale == target) return;
        ValueAnimator va = ValueAnimator.ofFloat(pressScale, target);
        va.setDuration(pressed ? 130 : 260);
        va.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
        va.addUpdateListener(a -> { pressScale = (float) a.getAnimatedValue(); invalidate(); });
        va.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (morph != null) morph.cancel();
        removeCallbacks(holdRunnable);
        super.onDetachedFromWindow();
    }
}
