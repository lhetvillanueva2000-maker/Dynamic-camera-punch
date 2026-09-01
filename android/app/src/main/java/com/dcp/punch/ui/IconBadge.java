package com.dcp.punch.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * A glyph inside a tinted circle — the thing at the left of every row and inside
 * every summary chip.
 *
 * The tint is the row's category rather than its state, which is what lets a
 * screenful of rows be scanned by colour: system events in one hue, live cards
 * in another. It carries no meaning a colour-blind reader would lose, because
 * the glyph inside it already says what the row is.
 */
public class IconBadge extends View {

    private final float d;
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int icon = Glyphs.BELL;
    /** Diameter in dp. */
    private float size = 44f;

    public IconBadge(Context c) { this(c, null); }

    public IconBadge(Context c, AttributeSet a) {
        super(c, a);
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                c.getResources().getDisplayMetrics());
        glyph.setStyle(Paint.Style.STROKE);
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStrokeJoin(Paint.Join.ROUND);
        glyph.setStrokeWidth(1.9f * d);
    }

    /** @param fill circle colour, @param tint glyph colour. */
    public IconBadge set(int glyphId, int fill, int tint) {
        icon = glyphId;
        disc.setColor(fill);
        glyph.setColor(tint);
        invalidate();
        return this;
    }

    public IconBadge size(float dp) {
        size = dp;
        glyph.setStrokeWidth(Math.max(1.5f, dp / 23f) * d);
        requestLayout();
        return this;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int px = Math.round(size * d);
        setMeasuredDimension(px, px);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        c.drawCircle(cx, cy, Math.min(cx, cy), disc);
        Glyphs.draw(c, icon, cx, cy, size * d * 0.23f, glyph);
    }
}
