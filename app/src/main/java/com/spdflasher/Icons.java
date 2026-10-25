package com.spdflasher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Flat, Android-style vector icons drawn with primitives on a 24dp grid (no emojis, no images). */
final class Icons extends Drawable {
    static final int INFO = 0, REFRESH = 1, SETTINGS = 2, PHONE = 3, CHIP = 4, FOLDER = 5,
            TERMINAL = 6, PLUG = 7, WARNING = 8, KEY = 9, SAVE = 10, CHECK = 11, BOLT = 12,
            CLIPBOARD = 13, BOX = 14, SIGNAL = 15, TRASH = 16, LOCK = 17, UNLOCK = 18,
            SHIELD = 19, ERASER = 20, GRID = 21, WRENCH = 22, POWER = 23, DOWNLOAD = 24,
            DOC = 25, A = 26, B = 27, CLOSE = 28;

    private final int kind, color;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icons(int kind, int color) {
        this.kind = kind;
        this.color = color;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(2f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        float dx = getBounds().width() / 24f, dy = getBounds().height() / 24f;
        if (dx == 0 || dy == 0) return;
        int save = c.save();
        c.translate(getBounds().left, getBounds().top);
        c.scale(dx, dy);
        drawIcon(c, kind);
        c.restoreToCount(save);
    }

    private void drawIcon(Canvas c, int k) {
        switch (k) {
            case INFO: {
                circle(c, 12, 12, 8, stroke);
                c.drawLine(12, 11, 12, 16.5f, stroke);
                c.drawPoint(12, 8, stroke);
                break;
            }
            case REFRESH: {
                arc(c, 4, 4, 20, 20, 180, 180, stroke);
                strokePath(c, new float[]{17.6f, 12.9f, 20, 15.7f, 22.4f, 12.9f}, stroke);
                arc(c, 4, 4, 20, 20, 0, 180, stroke);
                strokePath(c, new float[]{1.9f, 11, 4, 8.2f, 6.1f, 11}, stroke);
                break;
            }
            case SETTINGS: {
                tune(c, 4, 20, 8, 7);
                tune(c, 4, 20, 16, 12);
                tune(c, 4, 20, 11, 17);
                break;
            }
            case PHONE: {
                c.drawRoundRect(7, 3, 17, 21, 2, 2, stroke);
                c.drawPoint(12, 17.4f, stroke);
                break;
            }
            case CHIP: {
                c.drawRoundRect(7, 7, 17, 17, 2, 2, stroke);
                c.drawRoundRect(10.5f, 10.5f, 13.5f, 13.5f, 1, 1, fill);
                pins(c, stroke);
                break;
            }
            case FOLDER: {
                float[] f = {3, 20, 3, 4, 10, 4, 12, 6.5f, 21, 6.5f, 21, 20, 3, 20};
                strokePath(c, f, stroke);
                break;
            }
            case TERMINAL: {
                c.drawRoundRect(3, 4, 21, 20, 2, 2, stroke);
                float[] chev = {7, 9, 10, 12, 7, 15};
                strokePath(c, chev, stroke);
                line(c, 12.5f, 15.5f, 16.5f, 15.5f, stroke);
                break;
            }
            case PLUG: {
                line(c, 9, 3, 9, 7, stroke);
                line(c, 15, 3, 15, 7, stroke);
                float[] body = {9, 7, 15, 7, 15, 11, 12, 13.5f, 9, 11, 9, 7};
                strokePath(c, body, stroke);
                line(c, 12, 13.5f, 12, 21, stroke);
                break;
            }
            case WARNING: {
                float[] tri = {12, 3.5f, 21.5f, 20, 2.5f, 20, 12, 3.5f};
                strokePath(c, tri, stroke);
                line(c, 12, 10, 12, 14.5f, stroke);
                c.drawPoint(12, 17.3f, stroke);
                break;
            }
            case KEY: {
                circle(c, 15.5f, 8, 3.5f, stroke);
                stroke.setStrokeWidth(2.4f);
                line(c, 13, 10.8f, 4, 19.5f, stroke);
                line(c, 8, 16, 10.6f, 18.6f, stroke);
                line(c, 11.6f, 13.4f, 14.2f, 16, stroke);
                break;
            }
            case SAVE: {
                strokePath(c, new float[]{4.5f, 20, 4.5f, 4.5f, 14.5f, 4.5f, 19.5f, 9.5f,
                        19.5f, 20, 4.5f, 20}, stroke);
                float[] h = {8.5f, 4.5f, 8.5f, 9.5f, 15.5f, 9.5f, 15.5f, 4.5f};
                strokePath(c, h, stroke);
                circle(c, 12, 15.5f, 2.5f, stroke);
                break;
            }
            case CHECK: {
                circle(c, 12, 12, 8.5f, stroke);
                stroke.setStrokeWidth(2.4f);
                float[] ch = {7.8f, 12.3f, 10.7f, 15.2f, 16.2f, 9.2f};
                strokePath(c, ch, stroke);
                break;
            }
            case BOLT: {
                float[] b = {12.8f, 3, 6.5f, 13.5f, 11, 13.5f, 9.5f, 21, 16.5f, 10.5f, 11.8f, 10.5f, 14, 3};
                strokePath(c, b, stroke);
                break;
            }
            case CLIPBOARD: {
                c.drawRoundRect(5.5f, 4.5f, 18.5f, 20.5f, 2, 2, stroke);
                c.drawRoundRect(9, 3, 15, 6.5f, 1.5f, 1.5f, stroke);
                line(c, 9, 11, 15, 11, stroke);
                line(c, 9, 14.5f, 15, 14.5f, stroke);
                line(c, 9, 18, 12.5f, 18, stroke);
                break;
            }
            case BOX: {
                c.drawRect(4, 8.5f, 20, 20, stroke);
                c.drawRect(3, 4, 21, 8.5f, stroke);
                line(c, 10, 12, 14, 12, stroke);
                break;
            }
            case SIGNAL: {
                float[] base = {3.5f, 20.5f, 21, 20.5f};
                strokePath(c, base, stroke);
                c.drawRoundRect(5.5f, 14, 8.2f, 20.5f, 1, 1, fill);
                c.drawRoundRect(10.2f, 10.5f, 12.9f, 20.5f, 1, 1, fill);
                c.drawRoundRect(14.9f, 7, 17.6f, 20.5f, 1, 1, fill);
                break;
            }
            case TRASH: {
                strokePath(c, new float[]{5, 6.5f, 19, 6.5f}, stroke);
                float[] lid = {9, 3.5f, 15, 3.5f, 16, 6.5f, 8, 6.5f, 9, 3.5f};
                strokePath(c, lid, stroke);
                float[] can = {6.3f, 6.8f, 17.7f, 20, 6.3f, 20, 6.3f, 6.8f};
                strokePath(c, can, stroke);
                line(c, 10, 10, 10, 17, stroke);
                line(c, 14, 10, 14, 17, stroke);
                break;
            }
            case LOCK: {
                c.drawRoundRect(5.5f, 11, 18.5f, 20, 2, 2, stroke);
                arc(c, 8, 3.5f, 16, 13, 180, 180, stroke);
                strokes(c, stroke, 8, 8.2f, 8, 11, 16, 8.2f, 16, 11);
                circle(c, 12, 14.8f, 1.4f, fill);
                break;
            }
            case UNLOCK: {
                c.drawRoundRect(5.5f, 11, 18.5f, 20, 2, 2, stroke);
                arc(c, 8, 3.5f, 16, 13, 180, 135, stroke);
                strokes(c, stroke, 8, 8.2f, 8, 11);
                circle(c, 12, 14.8f, 1.4f, fill);
                break;
            }
            case SHIELD: {
                float[] s = {12, 3, 19.5f, 6, 12, 21, 4.5f, 6, 12, 3};
                strokePath(c, s, stroke);
                break;
            }
            case ERASER: {
                float[] e = {8, 20, 4, 16, 13, 7, 17, 11, 8, 20};
                strokePath(c, e, stroke);
                strokePath(c, new float[]{13, 7, 15.5f, 4.5f, 19.5f, 8.5f, 17, 11}, stroke);
                line(c, 9, 20, 19, 20, stroke);
                break;
            }
            case GRID: {
                strokePath(c, new float[]{4, 10.5f, 4, 4, 10.5f, 4, 10.5f, 10.5f, 4,
                        10.5f, 4, 10.5f}, stroke);
                strokePath(c, new float[]{13.5f, 10.5f, 13.5f, 4, 20, 4, 20, 10.5f, 13.5f,
                        10.5f, 13.5f, 10.5f}, stroke);
                strokePath(c, new float[]{4, 20, 4, 13.5f, 10.5f, 13.5f, 10.5f, 20, 4, 20,
                        4, 20}, stroke);
                strokePath(c, new float[]{13.5f, 20, 13.5f, 13.5f, 20, 13.5f, 20, 20, 13.5f,
                        20, 13.5f, 20}, stroke);
                break;
            }
            case WRENCH: {
                // restart arrow: open-top circle plus a chevron near the gap (reboot into recovery)
                arc(c, 4.5f, 4.5f, 19.5f, 19.5f, 290, 300, stroke);
                strokePath(c, new float[]{5.7f, 4.5f, 9.5f, 4.4f, 8.7f, 8.1f}, stroke);
                break;
            }
            case POWER: {
                arc(c, 4.5f, 8, 19.5f, 23, 115, 310, stroke);
                line(c, 12, 2.5f, 12, 12, stroke);
                break;
            }
            case DOWNLOAD: {
                float[] arr = {12, 3.5f, 12, 14.5f, 7.5f, 10, 12, 14.5f, 16.5f, 10, 12, 14.5f};
                strokePath(c, arr, stroke);
                line(c, 5, 20, 19, 20, stroke);
                break;
            }
            case DOC: {
                strokePath(c, new float[]{7, 21, 7, 3, 14, 3, 17, 6, 17, 21, 7, 21}, stroke);
                strokePath(c, new float[]{14, 3, 14, 6, 17, 6}, stroke);
                line(c, 10, 12, 14, 12, stroke);
                line(c, 10, 16, 14, 16, stroke);
                break;
            }
            case A:
                glyph(c, "A");
                break;
            case B:
                glyph(c, "B");
                break;
            case CLOSE:
                line(c, 6, 6, 18, 18, stroke);
                line(c, 18, 6, 6, 18, stroke);
                break;
        }
    }

    private void tune(Canvas c, float x1, float x2, float knob, float y) {
        c.drawLine(x1, y, x2, y, stroke);
        c.drawCircle(knob, y, 2, fill);
    }

    private void glyph(Canvas c, String g) {
        Paint t = new Paint(fill);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(16f);
        c.drawText(g, 12, 17.5f, t);
    }

    private static void pins(Canvas c, Paint s) {
        s.setStrokeWidth(2.2f);
        c.drawLine(9, 7, 9, 4, s);
        c.drawLine(12, 7, 12, 4, s);
        c.drawLine(15, 7, 15, 4, s);
        c.drawLine(9, 19.9f, 9, 17, s);
        c.drawLine(12, 19.9f, 12, 17, s);
        c.drawLine(15, 19.9f, 15, 17, s);
        c.drawLine(9, 4.2f, 9, 2.3f, s);
        c.drawLine(12, 4.2f, 12, 2.3f, s);
        c.drawLine(15, 4.2f, 15, 2.3f, s);
    }

    private static void strokes(Canvas c, Paint s, float... pts) {
        for (int i = 0; i + 3 < pts.length; i += 4)
            c.drawLine(pts[i], pts[i + 1], pts[i + 2], pts[i + 3], s);
    }

    private static void circle(Canvas c, float x, float y, float r, Paint p) {
        c.drawCircle(x, y, r, p);
    }

    private static void line(Canvas c, float x1, float y1, float x2, float y2, Paint p) {
        c.drawLine(x1, y1, x2, y2, p);
    }

    private static void arc(Canvas c, float l, float t, float r, float b, float start, float sweep, Paint p) {
        c.drawArc(l, t, r, b, start, sweep, false, p);
    }

    private static void strokePath(Canvas c, float[] pts, Paint p) {
        c.save();
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(pts[0], pts[1]);
        for (int i = 2; i + 1 < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
        if (pts[0] == pts[pts.length - 2] && pts[1] == pts[pts.length - 1]) path.close();
        c.drawPath(path, p);
        c.restore();
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
