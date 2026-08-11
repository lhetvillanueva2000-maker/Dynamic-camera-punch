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

import com.dcp.punch.data.IslandStore;
import com.dcp.punch.data.Presentation;

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
    private String variant;

    /* ── Animated geometry ───────────────────────────────────────────── */
    private float curW, curH, curR;
    private float fromW, fromH, fromR;
    private float tgtW, tgtH, tgtR;
    private ValueAnimator morph;
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
        this.d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                ctx.getResources().getDisplayMetrics());

        this.holdRunnable = () -> {
            if (moved) return;
            held = true;
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            store.toggleExpanded();
        };

        setLayerType(LAYER_TYPE_HARDWARE, null);

        body.setColor(Color.BLACK);
        body.setShadowLayer(14 * d, 0, 6 * d, 0x8C000000);
        // shadowLayer needs software rendering for the blur on some drivers;
        // hardware layers handle it since API 28, and below that it degrades to
        // a hard edge rather than failing.
        track.setColor(0x2EFFFFFF);
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

        if (p == null) {
            tgtW = (isA() ? IDLE_A_W : IDLE_B_W) * d;
            tgtH = (isA() ? IDLE_A_H : IDLE_B_H) * d;
            tgtR = isA() ? Math.min(tgtH * 0.62f, 18 * d) : tgtH / 2f;
            return;
        }

        if (store.isExpanded()) {
            tgtW = Math.min(EXP_W * d, maxW);
            tgtH = expandedHeight(p, tgtW);
            tgtR = 34 * d;
            return;
        }

        tgtH = COMPACT_H * d;
        tgtR = tgtH / 2f;

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

    private float expandedHeight(Presentation p, float w) {
        float h = EXP_PAD_TOP * d;
        h += 52 * d;                                        // header block
        if (p.progress >= 0) h += 13 * d + 4 * d;           // gap + bar
        if (p.actions.length > 0) h += 13 * d + 42 * d;     // gap + buttons
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
        morph.setDuration((long) MORPH_MS);
        morph.setInterpolator(new PathInterpolator(0.32f, 0.72f, 0f, 1f));
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

    private void animate(float from, float to, long ms,
                         java.util.function.Consumer<Float> onUpdate, Runnable onEnd) {
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(ms);
        va.addUpdateListener(a -> onUpdate.accept((Float) a.getAnimatedValue()));
        if (onEnd != null) {
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    onEnd.run();
                }
            });
        }
        va.start();
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

        // Body
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
        float left = box.left + PAD_X * d;
        float right = box.right - PAD_X * d;
        float y = box.top + EXP_PAD_TOP * d;

        // Header: art / icon, then two lines of text.
        float artSize = 52 * d;
        if (p.art != null) {
            drawArt(canvas, p.art, left, y, artSize, 13 * d, alpha);
        } else {
            chip.setColor(withAlpha(p.accent, alpha));
            r1.set(left, y, left + artSize, y + artSize);
            canvas.drawRoundRect(r1, 13 * d, 13 * d, chip);
            drawIcon(canvas, p, left + artSize / 2f, y + artSize / 2f, 13 * d, Color.WHITE, alpha);
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

        if (p.progress >= 0) {
            y += 13 * d;
            float h = 4 * d;
            r1.set(left, y, right, y + h);
            track.setAlpha(alpha / 4);
            canvas.drawRoundRect(r1, h / 2f, h / 2f, track);
            chip.setColor(withAlpha(p.accent == 0 ? Color.WHITE : p.accent, alpha));
            r1.set(left, y, left + (right - left) * p.progress, y + h);
            canvas.drawRoundRect(r1, h / 2f, h / 2f, chip);
            y += h;
        }

        actionRects.clear();
        if (p.actions.length > 0) {
            y += 13 * d;
            float size = 42 * d;
            int n = p.actions.length;
            float spacing = 26 * d;
            float totalW = n * size + (n - 1) * spacing;
            float x = box.centerX() - totalW / 2f;
            for (int i = 0; i < n; i++) {
                RectF rect = new RectF(x, y, x + size, y + size);
                actionRects.add(rect);
                chip.setColor(withAlpha(0xFFFFFFFF, (int) (alpha * 0.13f)));
                canvas.drawOval(rect, chip);
                drawActionIcon(canvas, p.actions[i], rect, alpha);
                x += size + spacing;
            }
        }
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
                    performClick();
                    return true;
                }
                if (Math.abs(dx) > SWIPE_MIN_DP * d && Math.abs(dx) > Math.abs(dy)) {
                    store.swap();                            // swipe ← / →
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

    @Override
    public boolean performClick() {
        super.performClick();          // fires accessibility + click listeners
        onTap(tapX, tapY);
        return true;
    }

    private void onTap(float x, float y) {
        Presentation p = shown;
        if (p == null) return;

        if (store.isExpanded()) {
            for (int i = 0; i < actionRects.size() && i < p.actions.length; i++) {
                if (actionRects.get(i).contains(x, y)) {
                    Presentation.Action a = p.actions[i];
                    if (a.run != null) a.run.run();
                    return;
                }
            }
            // Tapping the body of the expanded view opens the source app.
            callbacks.onOpenContent(p);
            return;
        }

        if (p.tapAction != null && p.tapAction.run != null) p.tapAction.run.run();
        else callbacks.onOpenContent(p);
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
