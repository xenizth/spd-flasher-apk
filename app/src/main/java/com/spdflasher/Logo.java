package com.spdflasher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * The app logo: a memory chip with a lightning bolt, drawn flat in code so it scales anywhere.
 * Chip outline with a filled flash bolt in the middle - no gradients, no bugdroid.
 */
final class Logo extends Drawable {
    /** Default accent colour (the Neon theme accent). */
    static final int ACCENT = 0xFF64D8F0;

    private final int color;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    Logo() { this(ACCENT); }

    Logo(int color) {
        this.color = color;
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(4f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width(), h = getBounds().height();
        float u = Math.min(w, h) / 108f;
        c.save();
        c.translate(getBounds().left + (w - 108 * u) / 2f, getBounds().top + (h - 108 * u) / 2f);
        c.scale(u, u);

        // pins: three stubs on each side of the chip body
        c.drawLine(43.5f, 28.5f, 43.5f, 35f, stroke);
        c.drawLine(54, 28.5f, 54, 35f, stroke);
        c.drawLine(64.5f, 28.5f, 64.5f, 35f, stroke);
        c.drawLine(43.5f, 73f, 43.5f, 79.5f, stroke);
        c.drawLine(54, 73f, 54, 79.5f, stroke);
        c.drawLine(64.5f, 73f, 64.5f, 79.5f, stroke);
        c.drawLine(28.5f, 43.5f, 35f, 43.5f, stroke);
        c.drawLine(28.5f, 54, 35f, 54, stroke);
        c.drawLine(28.5f, 64.5f, 35f, 64.5f, stroke);
        c.drawLine(73f, 43.5f, 79.5f, 43.5f, stroke);
        c.drawLine(73f, 54, 79.5f, 54, stroke);
        c.drawLine(73f, 64.5f, 79.5f, 64.5f, stroke);

        // chip body: rounded square, stroked
        c.drawRoundRect(37f, 37f, 71f, 71f, 8f, 8f, stroke);

        // filled flash bolt in the middle (material flash path, scaled onto the 108 grid)
        c.drawPath(bolt(), fill);
        c.restore();
    }

    private android.graphics.Path bolt() {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(55, 44);
        p.lineTo(45, 56);
        p.lineTo(52, 56);
        p.lineTo(51, 64);
        p.lineTo(61, 52);
        p.lineTo(54, 52);
        p.close();
        return p;
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
