package com.spdflasher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** The app logo (a microchip with a lightning bolt), drawn in code so it scales cleanly anywhere. */
final class Logo extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bolt = new Path();
    private final RectF r = new RectF();

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width(), h = getBounds().height();
        float u = Math.min(w, h) / 100f;
        c.save();
        c.translate(getBounds().left + (w - 100 * u) / 2f, getBounds().top + (h - 100 * u) / 2f);
        c.scale(u, u);

        // tile
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 100, 100, 0xFF3C6EDC, 0xFF0B1330, Shader.TileMode.CLAMP));
        r.set(0, 0, 100, 100);
        c.drawRoundRect(r, 22, 22, p);
        p.setShader(null);

        // pins
        p.setColor(0xFFEBF2FF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setStrokeCap(Paint.Cap.ROUND);
        float[] pos = {38f, 50f, 62f};
        for (float v : pos) {
            c.drawLine(v, 17f, v, 29f, p);
            c.drawLine(v, 71f, v, 83f, p);
            c.drawLine(17f, v, 29f, v, p);
            c.drawLine(71f, v, 83f, v, p);
        }

        // chip body
        p.setStrokeWidth(5f);
        r.set(29f, 29f, 71f, 71f);
        c.drawRoundRect(r, 7f, 7f, p);

        // bolt
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF35C66B);
        bolt.reset();
        bolt.moveTo(56f, 33f);
        bolt.lineTo(39f, 54f);
        bolt.lineTo(49f, 54f);
        bolt.lineTo(44f, 68f);
        bolt.lineTo(62f, 46f);
        bolt.lineTo(53f, 46f);
        bolt.close();
        c.drawPath(bolt, p);
        c.restore();
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
