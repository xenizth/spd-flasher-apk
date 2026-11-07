package com.spdflasher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * The app logo: a phone silhouette struck through by a flash bolt, drawn flat in code so it
 * scales anywhere. Reads at a glance as "flashing a phone" - no gradients, no bugdroid.
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
        stroke.setStrokeWidth(4.5f);
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

        // phone body: tall rounded rect
        c.drawRoundRect(34f, 13f, 74f, 95f, 13f, 13f, stroke);
        // home indicator, bottom of the screen
        c.drawLine(47f, 88f, 61f, 88f, stroke);

        // filled flash bolt, struck through the body (the "flashing" mark)
        c.drawPath(bolt(), fill);
        c.restore();
    }

    private android.graphics.Path bolt() {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(61, 25);
        p.lineTo(43, 58);
        p.lineTo(51.5f, 58);
        p.lineTo(48, 83);
        p.lineTo(68, 48);
        p.lineTo(58, 48);
        p.close();
        return p;
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
