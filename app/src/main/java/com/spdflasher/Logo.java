package com.spdflasher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The app logo: the Android robot head (bugdroid), drawn flat in code so it scales anywhere.
 * Green head, punched eyes, two antennas - no gradients. Heads sit on whatever surface is behind.
 */
final class Logo extends Drawable {
    /** Android brand green. */
    static final int GREEN = 0xFF3DDC84;

    private final int color;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    Logo() { this(GREEN); }

    Logo(int color) {
        this.color = color;
        fill.setColor(color);
        stroke.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(6f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width(), h = getBounds().height();
        float u = Math.min(w, h) / 100f;
        c.save();
        c.translate(getBounds().left + (w - 100 * u) / 2f, getBounds().top + (h - 100 * u) / 2f);
        c.scale(u, u);

        // head: dome + short slab, eyes punched out with even-odd fill (they show the surface behind)
        c.drawPath(head(), fill);

        // antennas
        c.drawLine(29f, 24f, 22f, 11.5f, stroke);
        c.drawLine(71f, 24f, 78f, 11.5f, stroke);
        c.restore();
    }

    /** Bugdroid head in a 0..100 grid: dome from x 8..92, top y 18, flat bottom at y 70. */
    private Path head() {
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(8, 60);
        p.arcTo(new RectF(8, 18, 92, 102), 180, 180); // dome: (8,60) -> (50,18) -> (92,60)
        p.lineTo(92, 64);
        p.quadTo(92, 70, 86, 70);
        p.lineTo(14, 70);
        p.quadTo(8, 70, 8, 64);
        p.close();
        p.addCircle(31, 45, 6.5f, Path.Direction.CW);
        p.addCircle(69, 45, 6.5f, Path.Direction.CW);
        return p;
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
