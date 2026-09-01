package com.dcp.punch.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Every icon in the control panel, drawn rather than loaded.
 *
 * There are around forty of them. As vector drawables that would be forty XML
 * files, forty resource entries, and forty inflate-and-cache cycles the first
 * time each screen scrolls into view. As code it is one class, no resource
 * lookups, and — the part that actually matters here — no per-icon Drawable
 * objects sitting in memory for rows that are off screen.
 *
 * Everything is drawn inside a box of side {@code 2*r} centred on (cx, cy) and
 * built from the same handful of primitives at the same stroke weight, which is
 * what makes a set drawn by hand look like a set rather than a collection.
 *
 * Nothing here allocates: the Path and RectF are reused, so a list scrolling at
 * 60fps does not hand the collector a bag of geometry every frame.
 */
public final class Glyphs {

    private Glyphs() { }

    /* Island content */
    public static final int BOLT = 0;
    public static final int BATTERY_LOW = 1;
    public static final int WIFI = 2;
    public static final int WIFI_OFF = 3;
    public static final int BLUETOOTH = 4;
    public static final int HEADPHONES = 5;
    public static final int VIBRATE = 6;
    public static final int PLANE = 7;
    public static final int USB = 8;
    public static final int BRIGHTNESS = 9;
    public static final int LOCK = 10;

    /* Live cards */
    public static final int PLAY = 11;
    public static final int VOLUME = 12;
    public static final int PROGRESS = 13;
    public static final int TIMER = 14;
    public static final int FLASHLIGHT = 15;
    public static final int PHONE = 16;
    public static final int NAVIGATION = 17;
    public static final int MESSAGE = 18;
    public static final int BELL = 19;

    /* Gestures */
    public static final int TAP = 20;
    public static final int DOUBLE_TAP = 21;
    public static final int LONG_PRESS = 22;
    public static final int SWIPE_LEFT = 23;
    public static final int SWIPE_RIGHT = 24;

    /* Navigation and chrome */
    public static final int CARDS = 25;
    public static final int ISLAND = 26;
    public static final int GEAR = 27;
    public static final int SLIDERS = 28;
    public static final int CHEVRON = 29;
    public static final int BACK = 30;
    public static final int PLUS = 31;
    public static final int CHECK = 32;
    public static final int CROSS = 33;

    /* Look and settings */
    public static final int PAINT = 34;
    public static final int SPEED = 35;
    public static final int BOUNCE = 36;
    public static final int PHONE_PORTRAIT = 37;
    public static final int PHONE_LANDSCAPE = 38;
    public static final int MEMORY = 39;
    public static final int CPU = 40;
    public static final int STORAGE = 41;
    public static final int HEART = 42;
    public static final int PERSON = 43;
    public static final int APPS = 44;
    public static final int MOON = 45;
    public static final int SHIELD = 46;
    public static final int BUG = 47;
    public static final int SPARKLE = 48;

    private static final Path P = new Path();
    private static final RectF R = new RectF();

    /**
     * Draw one glyph. {@code p} supplies the colour and stroke width; its style
     * is restored before returning, so a caller's paint is never left altered.
     */
    public static void draw(Canvas c, int id, float cx, float cy, float r, Paint p) {
        Paint.Style style = p.getStyle();
        p.setStyle(Paint.Style.STROKE);
        switch (id) {

            case BOLT: {
                P.rewind();
                P.moveTo(cx + r * 0.28f, cy - r * 0.95f);
                P.lineTo(cx - r * 0.42f, cy + r * 0.10f);
                P.lineTo(cx + r * 0.06f, cy + r * 0.10f);
                P.lineTo(cx - r * 0.14f, cy + r * 0.95f);
                P.lineTo(cx + r * 0.52f, cy - r * 0.16f);
                P.lineTo(cx + r * 0.04f, cy - r * 0.16f);
                P.close();
                c.drawPath(P, p);
                break;
            }

            case BATTERY_LOW: {
                R.set(cx - r * 0.42f, cy - r * 0.86f, cx + r * 0.42f, cy + r * 0.86f);
                c.drawRoundRect(R, r * 0.18f, r * 0.18f, p);
                c.drawLine(cx - r * 0.16f, cy - r * 0.98f, cx + r * 0.16f, cy - r * 0.98f, p);
                // The "low" part: a short bar at the bottom, and nothing above it.
                p.setStyle(Paint.Style.FILL);
                R.set(cx - r * 0.26f, cy + r * 0.30f, cx + r * 0.26f, cy + r * 0.68f);
                c.drawRoundRect(R, r * 0.08f, r * 0.08f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            }

            case WIFI:
            case WIFI_OFF: {
                for (int i = 0; i < 3; i++) {
                    float rad = r * (0.34f + i * 0.28f);
                    R.set(cx - rad, cy + r * 0.42f - rad, cx + rad, cy + r * 0.42f + rad);
                    c.drawArc(R, 216f, 108f, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy + r * 0.46f, r * 0.13f, p);
                p.setStyle(Paint.Style.STROKE);
                if (id == WIFI_OFF) slash(c, cx, cy, r, p);
                break;
            }

            case BLUETOOTH: {
                P.rewind();
                P.moveTo(cx - r * 0.42f, cy - r * 0.40f);
                P.lineTo(cx + r * 0.42f, cy + r * 0.42f);
                P.lineTo(cx, cy + r * 0.86f);
                P.lineTo(cx, cy - r * 0.86f);
                P.lineTo(cx + r * 0.42f, cy - r * 0.42f);
                P.lineTo(cx - r * 0.42f, cy + r * 0.40f);
                c.drawPath(P, p);
                break;
            }

            case HEADPHONES: {
                R.set(cx - r * 0.74f, cy - r * 0.78f, cx + r * 0.74f, cy + r * 0.36f);
                c.drawArc(R, 180f, 180f, false, p);
                R.set(cx - r * 0.78f, cy - r * 0.14f, cx - r * 0.30f, cy + r * 0.74f);
                c.drawRoundRect(R, r * 0.22f, r * 0.22f, p);
                R.set(cx + r * 0.30f, cy - r * 0.14f, cx + r * 0.78f, cy + r * 0.74f);
                c.drawRoundRect(R, r * 0.22f, r * 0.22f, p);
                break;
            }

            case VIBRATE: {
                R.set(cx - r * 0.34f, cy - r * 0.80f, cx + r * 0.34f, cy + r * 0.80f);
                c.drawRoundRect(R, r * 0.16f, r * 0.16f, p);
                c.drawLine(cx - r * 0.70f, cy - r * 0.26f, cx - r * 0.70f, cy + r * 0.26f, p);
                c.drawLine(cx + r * 0.70f, cy - r * 0.26f, cx + r * 0.70f, cy + r * 0.26f, p);
                break;
            }

            case PLANE: {
                P.rewind();
                P.moveTo(cx - r * 0.88f, cy + r * 0.16f);
                P.lineTo(cx + r * 0.88f, cy - r * 0.34f);
                P.lineTo(cx + r * 0.60f, cy + r * 0.30f);
                P.lineTo(cx - r * 0.20f, cy + r * 0.56f);
                P.close();
                c.drawPath(P, p);
                c.drawLine(cx - r * 0.30f, cy + r * 0.52f, cx - r * 0.52f, cy + r * 0.88f, p);
                break;
            }

            case USB: {
                c.drawLine(cx, cy + r * 0.88f, cx, cy - r * 0.50f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy + r * 0.86f, r * 0.16f, p);
                P.rewind();
                P.moveTo(cx, cy - r * 0.92f);
                P.lineTo(cx - r * 0.24f, cy - r * 0.52f);
                P.lineTo(cx + r * 0.24f, cy - r * 0.52f);
                P.close();
                c.drawPath(P, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx, cy + r * 0.16f, cx - r * 0.52f, cy - r * 0.16f, p);
                c.drawLine(cx, cy - r * 0.10f, cx + r * 0.52f, cy - r * 0.40f, p);
                break;
            }

            case BRIGHTNESS: {
                c.drawCircle(cx, cy, r * 0.40f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    c.drawLine(cx + ca * r * 0.64f, cy + sa * r * 0.64f,
                               cx + ca * r * 0.92f, cy + sa * r * 0.92f, p);
                }
                break;
            }

            case LOCK: {
                R.set(cx - r * 0.62f, cy - r * 0.10f, cx + r * 0.62f, cy + r * 0.80f);
                c.drawRoundRect(R, r * 0.18f, r * 0.18f, p);
                R.set(cx - r * 0.38f, cy - r * 0.82f, cx + r * 0.38f, cy + r * 0.14f);
                c.drawArc(R, 180f, 180f, false, p);
                break;
            }

            case PLAY: {
                c.drawCircle(cx, cy, r * 0.88f, p);
                p.setStyle(Paint.Style.FILL);
                P.rewind();
                P.moveTo(cx - r * 0.22f, cy - r * 0.38f);
                P.lineTo(cx + r * 0.40f, cy);
                P.lineTo(cx - r * 0.22f, cy + r * 0.38f);
                P.close();
                c.drawPath(P, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            }

            case VOLUME: {
                p.setStyle(Paint.Style.FILL);
                P.rewind();
                P.moveTo(cx - r * 0.80f, cy - r * 0.26f);
                P.lineTo(cx - r * 0.44f, cy - r * 0.26f);
                P.lineTo(cx - r * 0.06f, cy - r * 0.70f);
                P.lineTo(cx - r * 0.06f, cy + r * 0.70f);
                P.lineTo(cx - r * 0.44f, cy + r * 0.26f);
                P.lineTo(cx - r * 0.80f, cy + r * 0.26f);
                P.close();
                c.drawPath(P, p);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 2; i++) {
                    float rad = r * (0.30f + i * 0.28f);
                    R.set(cx + r * 0.12f - rad, cy - rad, cx + r * 0.12f + rad, cy + rad);
                    c.drawArc(R, -52f, 104f, false, p);
                }
                break;
            }

            case PROGRESS: {
                // A dashed ring with a download arrow inside it.
                for (int i = 0; i < 8; i++) {
                    R.set(cx - r * 0.88f, cy - r * 0.88f, cx + r * 0.88f, cy + r * 0.88f);
                    c.drawArc(R, i * 45f + 6f, 33f, false, p);
                }
                c.drawLine(cx, cy - r * 0.42f, cx, cy + r * 0.34f, p);
                P.rewind();
                P.moveTo(cx - r * 0.26f, cy + r * 0.06f);
                P.lineTo(cx, cy + r * 0.36f);
                P.lineTo(cx + r * 0.26f, cy + r * 0.06f);
                c.drawPath(P, p);
                break;
            }

            case TIMER: {
                c.drawCircle(cx, cy + r * 0.12f, r * 0.74f, p);
                c.drawLine(cx - r * 0.28f, cy - r * 0.88f, cx + r * 0.28f, cy - r * 0.88f, p);
                c.drawLine(cx, cy + r * 0.12f, cx, cy - r * 0.36f, p);
                break;
            }

            case FLASHLIGHT: {
                P.rewind();
                P.moveTo(cx - r * 0.52f, cy - r * 0.88f);
                P.lineTo(cx + r * 0.52f, cy - r * 0.88f);
                P.lineTo(cx + r * 0.26f, cy - r * 0.34f);
                P.lineTo(cx + r * 0.26f, cy + r * 0.86f);
                P.lineTo(cx - r * 0.26f, cy + r * 0.86f);
                P.lineTo(cx - r * 0.26f, cy - r * 0.34f);
                P.close();
                c.drawPath(P, p);
                c.drawLine(cx - r * 0.26f, cy - r * 0.34f, cx + r * 0.26f, cy - r * 0.34f, p);
                break;
            }

            case PHONE: {
                P.rewind();
                P.moveTo(cx - r * 0.86f, cy - r * 0.56f);
                P.rQuadTo(r * 0.10f, r * 1.30f, r * 1.42f, r * 1.42f);
                c.drawPath(P, p);
                c.drawLine(cx - r * 0.86f, cy - r * 0.56f, cx - r * 0.34f, cy - r * 0.86f, p);
                c.drawLine(cx + r * 0.56f, cy + r * 0.86f, cx + r * 0.86f, cy + r * 0.34f, p);
                break;
            }

            case NAVIGATION: {
                P.rewind();
                P.moveTo(cx, cy - r * 0.92f);
                P.lineTo(cx + r * 0.72f, cy + r * 0.84f);
                P.lineTo(cx, cy + r * 0.36f);
                P.lineTo(cx - r * 0.72f, cy + r * 0.84f);
                P.close();
                c.drawPath(P, p);
                break;
            }

            case MESSAGE: {
                R.set(cx - r * 0.90f, cy - r * 0.74f, cx + r * 0.90f, cy + r * 0.46f);
                c.drawRoundRect(R, r * 0.26f, r * 0.26f, p);
                P.rewind();
                P.moveTo(cx - r * 0.40f, cy + r * 0.46f);
                P.lineTo(cx - r * 0.44f, cy + r * 0.92f);
                P.lineTo(cx + r * 0.02f, cy + r * 0.46f);
                c.drawPath(P, p);
                break;
            }

            case BELL: {
                P.rewind();
                P.moveTo(cx - r * 0.72f, cy + r * 0.42f);
                P.lineTo(cx + r * 0.72f, cy + r * 0.42f);
                P.lineTo(cx + r * 0.46f, cy + r * 0.06f);
                P.lineTo(cx + r * 0.46f, cy - r * 0.22f);
                P.rQuadTo(0f, -r * 0.62f, -r * 0.46f, -r * 0.62f);
                P.rQuadTo(-r * 0.46f, 0f, -r * 0.46f, r * 0.62f);
                P.lineTo(cx - r * 0.46f, cy + r * 0.06f);
                P.close();
                c.drawPath(P, p);
                R.set(cx - r * 0.24f, cy + r * 0.42f, cx + r * 0.24f, cy + r * 0.90f);
                c.drawArc(R, 0f, 180f, false, p);
                break;
            }

            case TAP:
            case DOUBLE_TAP:
            case LONG_PRESS: {
                hand(c, cx + r * 0.10f, cy + r * 0.16f, r, p);
                if (id == TAP) {
                    arcsAbove(c, cx - r * 0.30f, cy - r * 0.46f, r, p, 1);
                } else if (id == DOUBLE_TAP) {
                    arcsAbove(c, cx - r * 0.30f, cy - r * 0.46f, r, p, 2);
                } else {
                    // Held: a ring of dots rather than a ripple.
                    p.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 5; i++) {
                        double a = Math.PI * (0.78 + i * 0.16);
                        c.drawCircle(cx - r * 0.30f + (float) Math.cos(a) * r * 0.62f,
                                     cy - r * 0.46f + (float) Math.sin(a) * r * 0.62f,
                                     r * 0.08f, p);
                    }
                    p.setStyle(Paint.Style.STROKE);
                }
                break;
            }

            case SWIPE_LEFT:
            case SWIPE_RIGHT: {
                float dir = id == SWIPE_RIGHT ? 1f : -1f;
                hand(c, cx + r * 0.16f * -dir, cy + r * 0.20f, r, p);
                float ax = cx + dir * r * 0.62f, ay = cy - r * 0.40f;
                c.drawLine(ax - dir * r * 0.52f, ay, ax, ay, p);
                P.rewind();
                P.moveTo(ax - dir * r * 0.26f, ay - r * 0.26f);
                P.lineTo(ax, ay);
                P.lineTo(ax - dir * r * 0.26f, ay + r * 0.26f);
                c.drawPath(P, p);
                break;
            }

            case CARDS: {
                R.set(cx - r * 0.78f, cy - r * 0.30f, cx + r * 0.78f, cy + r * 0.76f);
                c.drawRoundRect(R, r * 0.22f, r * 0.22f, p);
                R.set(cx - r * 0.52f, cy - r * 0.76f, cx + r * 0.52f, cy - r * 0.44f);
                c.drawRoundRect(R, r * 0.16f, r * 0.16f, p);
                break;
            }

            case ISLAND: {
                R.set(cx - r * 0.92f, cy - r * 0.40f, cx + r * 0.92f, cy + r * 0.40f);
                c.drawRoundRect(R, r * 0.40f, r * 0.40f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx + r * 0.46f, cy, r * 0.16f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            }

            case GEAR: {
                c.drawCircle(cx, cy, r * 0.36f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    c.drawLine(cx + ca * r * 0.58f, cy + sa * r * 0.58f,
                               cx + ca * r * 0.92f, cy + sa * r * 0.92f, p);
                }
                break;
            }

            case SLIDERS: {
                for (int i = 0; i < 3; i++) {
                    float y = cy + (i - 1) * r * 0.62f;
                    c.drawLine(cx - r * 0.88f, y, cx + r * 0.88f, y, p);
                    p.setStyle(Paint.Style.FILL);
                    float knob = cx + (i == 1 ? r * 0.40f : -r * 0.30f + i * r * 0.72f);
                    c.drawCircle(knob, y, r * 0.20f, p);
                    p.setStyle(Paint.Style.STROKE);
                }
                break;
            }

            case CHEVRON: {
                P.rewind();
                P.moveTo(cx - r * 0.30f, cy - r * 0.62f);
                P.lineTo(cx + r * 0.30f, cy);
                P.lineTo(cx - r * 0.30f, cy + r * 0.62f);
                c.drawPath(P, p);
                break;
            }

            case BACK: {
                c.drawLine(cx - r * 0.72f, cy, cx + r * 0.74f, cy, p);
                P.rewind();
                P.moveTo(cx - r * 0.18f, cy - r * 0.54f);
                P.lineTo(cx - r * 0.74f, cy);
                P.lineTo(cx - r * 0.18f, cy + r * 0.54f);
                c.drawPath(P, p);
                break;
            }

            case PLUS: {
                c.drawLine(cx - r * 0.66f, cy, cx + r * 0.66f, cy, p);
                c.drawLine(cx, cy - r * 0.66f, cx, cy + r * 0.66f, p);
                break;
            }

            case CHECK: {
                P.rewind();
                P.moveTo(cx - r * 0.60f, cy + r * 0.04f);
                P.lineTo(cx - r * 0.16f, cy + r * 0.48f);
                P.lineTo(cx + r * 0.62f, cy - r * 0.44f);
                c.drawPath(P, p);
                break;
            }

            case CROSS: {
                c.drawLine(cx - r * 0.48f, cy - r * 0.48f, cx + r * 0.48f, cy + r * 0.48f, p);
                c.drawLine(cx + r * 0.48f, cy - r * 0.48f, cx - r * 0.48f, cy + r * 0.48f, p);
                break;
            }

            case PAINT: {
                // A paint bucket tipping.
                P.rewind();
                P.moveTo(cx - r * 0.76f, cy + r * 0.06f);
                P.lineTo(cx - r * 0.06f, cy - r * 0.64f);
                P.lineTo(cx + r * 0.62f, cy + r * 0.04f);
                P.lineTo(cx - r * 0.08f, cy + r * 0.72f);
                P.close();
                c.drawPath(P, p);
                c.drawLine(cx - r * 0.34f, cy - r * 0.36f, cx - r * 0.58f, cy - r * 0.86f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx + r * 0.82f, cy + r * 0.52f, r * 0.20f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            }

            case SPEED: {
                R.set(cx - r * 0.86f, cy - r * 0.66f, cx + r * 0.86f, cy + r * 1.06f);
                c.drawArc(R, 180f, 180f, false, p);
                c.drawLine(cx, cy + r * 0.20f, cx + r * 0.44f, cy - r * 0.36f, p);
                break;
            }

            case BOUNCE: {
                P.rewind();
                P.moveTo(cx - r * 0.86f, cy + r * 0.62f);
                P.rQuadTo(r * 0.42f, -r * 1.50f, r * 0.84f, 0f);
                P.rQuadTo(r * 0.30f, -r * 0.94f, r * 0.60f, 0f);
                c.drawPath(P, p);
                break;
            }

            case PHONE_PORTRAIT:
            case PHONE_LANDSCAPE: {
                float w = id == PHONE_PORTRAIT ? r * 0.56f : r * 0.92f;
                float h = id == PHONE_PORTRAIT ? r * 0.92f : r * 0.56f;
                R.set(cx - w, cy - h, cx + w, cy + h);
                c.drawRoundRect(R, r * 0.20f, r * 0.20f, p);
                p.setStyle(Paint.Style.FILL);
                if (id == PHONE_PORTRAIT) {
                    R.set(cx - r * 0.16f, cy - h + r * 0.14f, cx + r * 0.16f, cy - h + r * 0.26f);
                } else {
                    R.set(cx - w + r * 0.14f, cy - r * 0.16f, cx - w + r * 0.26f, cy + r * 0.16f);
                }
                c.drawRoundRect(R, r * 0.06f, r * 0.06f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            }

            case MEMORY: {
                R.set(cx - r * 0.58f, cy - r * 0.58f, cx + r * 0.58f, cy + r * 0.58f);
                c.drawRoundRect(R, r * 0.14f, r * 0.14f, p);
                for (int i = -1; i <= 1; i++) {
                    c.drawLine(cx + i * r * 0.40f, cy - r * 0.92f, cx + i * r * 0.40f, cy - r * 0.58f, p);
                    c.drawLine(cx + i * r * 0.40f, cy + r * 0.58f, cx + i * r * 0.40f, cy + r * 0.92f, p);
                    c.drawLine(cx - r * 0.92f, cy + i * r * 0.40f, cx - r * 0.58f, cy + i * r * 0.40f, p);
                    c.drawLine(cx + r * 0.58f, cy + i * r * 0.40f, cx + r * 0.92f, cy + i * r * 0.40f, p);
                }
                break;
            }

            case CPU: {
                R.set(cx - r * 0.58f, cy - r * 0.58f, cx + r * 0.58f, cy + r * 0.58f);
                c.drawRoundRect(R, r * 0.14f, r * 0.14f, p);
                R.set(cx - r * 0.22f, cy - r * 0.22f, cx + r * 0.22f, cy + r * 0.22f);
                c.drawRoundRect(R, r * 0.06f, r * 0.06f, p);
                for (int i = -1; i <= 1; i++) {
                    c.drawLine(cx + i * r * 0.34f, cy - r * 0.92f, cx + i * r * 0.34f, cy - r * 0.58f, p);
                    c.drawLine(cx + i * r * 0.34f, cy + r * 0.58f, cx + i * r * 0.34f, cy + r * 0.92f, p);
                }
                break;
            }

            case STORAGE: {
                R.set(cx - r * 0.88f, cy - r * 0.72f, cx + r * 0.88f, cy - r * 0.06f);
                c.drawRoundRect(R, r * 0.16f, r * 0.16f, p);
                R.set(cx - r * 0.88f, cy + r * 0.12f, cx + r * 0.88f, cy + r * 0.78f);
                c.drawRoundRect(R, r * 0.16f, r * 0.16f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx + r * 0.58f, cy - r * 0.39f, r * 0.11f, p);
                c.drawCircle(cx + r * 0.58f, cy + r * 0.45f, r * 0.11f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            }

            case HEART: {
                P.rewind();
                P.moveTo(cx, cy + r * 0.78f);
                P.cubicTo(cx - r * 1.30f, cy - r * 0.10f, cx - r * 0.56f, cy - r * 0.98f, cx, cy - r * 0.30f);
                P.cubicTo(cx + r * 0.56f, cy - r * 0.98f, cx + r * 1.30f, cy - r * 0.10f, cx, cy + r * 0.78f);
                P.close();
                c.drawPath(P, p);
                break;
            }

            case PERSON: {
                c.drawCircle(cx, cy - r * 0.36f, r * 0.36f, p);
                R.set(cx - r * 0.70f, cy + r * 0.10f, cx + r * 0.70f, cy + r * 1.20f);
                c.drawArc(R, 180f, 180f, false, p);
                break;
            }

            case APPS: {
                for (int i = 0; i < 4; i++) {
                    float dx = (i % 2 == 0) ? -r * 0.42f : r * 0.42f;
                    float dy = (i < 2) ? -r * 0.42f : r * 0.42f;
                    R.set(cx + dx - r * 0.30f, cy + dy - r * 0.30f,
                          cx + dx + r * 0.30f, cy + dy + r * 0.30f);
                    c.drawRoundRect(R, r * 0.11f, r * 0.11f, p);
                }
                break;
            }

            case MOON: {
                P.rewind();
                P.addCircle(cx + r * 0.10f, cy, r * 0.80f, Path.Direction.CW);
                P.addCircle(cx + r * 0.62f, cy - r * 0.42f, r * 0.72f, Path.Direction.CCW);
                c.save();
                c.clipPath(P);
                c.drawCircle(cx + r * 0.10f, cy, r * 0.80f, p);
                c.restore();
                // The clip hides the far side of the stroke, so trace the crescent.
                R.set(cx - r * 0.70f, cy - r * 0.80f, cx + r * 0.90f, cy + r * 0.80f);
                c.drawArc(R, 60f, 240f, false, p);
                break;
            }

            case SHIELD: {
                P.rewind();
                P.moveTo(cx, cy - r * 0.88f);
                P.lineTo(cx + r * 0.72f, cy - r * 0.52f);
                P.lineTo(cx + r * 0.72f, cy + r * 0.10f);
                P.rQuadTo(0f, r * 0.62f, -r * 0.72f, r * 0.78f);
                P.rQuadTo(-r * 0.72f, -r * 0.16f, -r * 0.72f, -r * 0.78f);
                P.lineTo(cx - r * 0.72f, cy - r * 0.52f);
                P.close();
                c.drawPath(P, p);
                break;
            }

            case BUG: {
                R.set(cx - r * 0.46f, cy - r * 0.50f, cx + r * 0.46f, cy + r * 0.70f);
                c.drawRoundRect(R, r * 0.46f, r * 0.46f, p);
                c.drawLine(cx - r * 0.46f, cy - r * 0.06f, cx - r * 0.90f, cy - r * 0.06f, p);
                c.drawLine(cx + r * 0.46f, cy - r * 0.06f, cx + r * 0.90f, cy - r * 0.06f, p);
                c.drawLine(cx - r * 0.40f, cy - r * 0.44f, cx - r * 0.78f, cy - r * 0.76f, p);
                c.drawLine(cx + r * 0.40f, cy - r * 0.44f, cx + r * 0.78f, cy - r * 0.76f, p);
                c.drawLine(cx - r * 0.40f, cy + r * 0.42f, cx - r * 0.80f, cy + r * 0.70f, p);
                c.drawLine(cx + r * 0.40f, cy + r * 0.42f, cx + r * 0.80f, cy + r * 0.70f, p);
                break;
            }

            case SPARKLE: {
                star(c, cx - r * 0.14f, cy - r * 0.10f, r * 0.78f, p);
                star(c, cx + r * 0.58f, cy + r * 0.56f, r * 0.36f, p);
                break;
            }

            default:
                c.drawCircle(cx, cy, r * 0.70f, p);
        }
        p.setStyle(style);
    }

    /* ── Shared pieces ───────────────────────────────────────────────── */

    /** The pointing hand shared by all five gesture icons. */
    private static void hand(Canvas c, float cx, float cy, float r, Paint p) {
        P.rewind();
        P.moveTo(cx - r * 0.30f, cy - r * 0.34f);
        P.lineTo(cx - r * 0.30f, cy + r * 0.18f);
        P.lineTo(cx - r * 0.54f, cy + r * 0.10f);
        P.rQuadTo(-r * 0.22f, r * 0.16f, -r * 0.02f, r * 0.38f);
        P.lineTo(cx - r * 0.18f, cy + r * 0.80f);
        P.lineTo(cx + r * 0.40f, cy + r * 0.80f);
        P.lineTo(cx + r * 0.46f, cy + r * 0.16f);
        P.rQuadTo(0f, -r * 0.20f, -r * 0.20f, -r * 0.18f);
        P.lineTo(cx - r * 0.06f, cy - r * 0.02f);
        P.lineTo(cx - r * 0.06f, cy - r * 0.34f);
        P.rQuadTo(0f, -r * 0.22f, -r * 0.12f, -r * 0.22f);
        P.rQuadTo(-r * 0.12f, 0f, -r * 0.12f, r * 0.22f);
        P.close();
        c.drawPath(P, p);
    }

    /** One or two ripple arcs above a tapping finger. */
    private static void arcsAbove(Canvas c, float cx, float cy, float r, Paint p, int count) {
        for (int i = 0; i < count; i++) {
            float rad = r * (0.34f + i * 0.28f);
            R.set(cx - rad, cy - rad, cx + rad, cy + rad);
            c.drawArc(R, 160f, 120f, false, p);
        }
    }

    /** A four-pointed sparkle. */
    private static void star(Canvas c, float cx, float cy, float r, Paint p) {
        P.rewind();
        P.moveTo(cx, cy - r);
        P.quadTo(cx + r * 0.16f, cy - r * 0.16f, cx + r, cy);
        P.quadTo(cx + r * 0.16f, cy + r * 0.16f, cx, cy + r);
        P.quadTo(cx - r * 0.16f, cy + r * 0.16f, cx - r, cy);
        P.quadTo(cx - r * 0.16f, cy - r * 0.16f, cx, cy - r);
        P.close();
        c.drawPath(P, p);
    }

    /** The diagonal bar that turns an icon into its "off" twin. */
    private static void slash(Canvas c, float cx, float cy, float r, Paint p) {
        c.drawLine(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f, p);
    }
}
