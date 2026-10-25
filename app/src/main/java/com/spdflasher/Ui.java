package com.spdflasher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small view factory so the screens read like a Material 3 dark theme without extra libraries. */
final class Ui {
    private Ui() {}

    // Material 3 dark colour roles: surface / container / primary, kept theme-dependent.
    // BG       surface
    // BG_MID   surfaceContainerLow
    // CARD     surfaceContainerHigh
    // CARD_DARK surfaceContainerLowest (inputs, rows, panels)
    // CHIP_TOP secondaryContainer (selected chips, active pills)
    // CHIP_BOT secondaryContainer low tone (kept for compat; equals CHIP_TOP)
    static int BG = 0xFF0B141A;
    static int CARD = 0xFF101E26;
    static int CARD_DARK = 0xFF0C171E;
    static int CHIP_SEL = 0xFF10374B;
    static final int OUTLINE = 0xFF43566A;
    static final int TEXT = 0xFFE9F0F7;
    static final int MUTED = 0xFF9CAFC5;
    static int ACCENT = 0xFF64D8F0;
    static int ON_ACCENT = 0xFF00344A;
    static int BLUE_BANNER = 0xFF14283A;
    static final int RED_BANNER = 0xFF3A101E;
    static final int RED_TEXT = 0xFFFFB2C1;
    static final int GREEN = 0xFF7DDBA8;
    static final int GREEN_BG = 0xFF10362A;
    static final int RED = 0xFFE6557A;
    static final int RED_BG = 0xFF3A101E;
    static final int AMBER = 0xFFF5C563;
    static final int AMBER_BG = 0xFF382A15;
    static int ICON_BG = 0xFF10374B;

    // theme-dependent extras (see applyTheme)
    static int ACCENT2 = 0xFF9E8CFF, BG_MID = 0xFF0E1920, HERO = 0xFF122733, CHIP_TOP = 0xFF10374B, CHIP_BOT = 0xFF10374B;

    static final String[] THEME_NAMES = {"Neon", "Synthwave", "Toxic", "Inferno", "Frost"};
    //                          accent      accent2     bgTop       bgMid       hero        chipTop     chipBot     card        cardDark    onAccent
    private static final int[][] THEMES = {
            {0xFF64D8F0, 0xFF9E8CFF, 0xFF0B141A, 0xFF0E1920, 0xFF122733, 0xFF10374B, 0xFF0D2C3D, 0xFF101E26, 0xFF0C171E, 0xFF00344A},
            {0xFFF273E3, 0xFFB98FF8, 0xFF130C19, 0xFF171122, 0xFF251537, 0xFF3B1E42, 0xFF2C1634, 0xFF1B1425, 0xFF110C18, 0xFF41103B},
            {0xFF7ADC96, 0xFFC3E86B, 0xFF0B1210, 0xFF0E1713, 0xFF122A1D, 0xFF14382A, 0xFF0F2C21, 0xFF122018, 0xFF0C1611, 0xFF00391E},
            {0xFFFF8A5A, 0xFFF4C063, 0xFF130C0B, 0xFF171010, 0xFF2B130C, 0xFF442011, 0xFF33170D, 0xFF1E1614, 0xFF130D0C, 0xFF4A1704},
            {0xFF9FD2F0, 0xFF8CB8E8, 0xFF0C1218, 0xFF0F161E, 0xFF15293B, 0xFF1C3B52, 0xFF162E42, 0xFF14202B, 0xFF0E1820, 0xFF0D344D},
    };

    /** Switches every theme colour. Call before the views are built (the activity recreates itself on change). */
    static void applyTheme(int i) {
        int[] t = THEMES[Math.max(0, Math.min(THEMES.length - 1, i))];
        ACCENT = t[0]; ACCENT2 = t[1]; BG = t[2]; BG_MID = t[3]; HERO = t[4]; CHIP_TOP = t[5]; CHIP_BOT = t[6];
        CARD = t[7]; CARD_DARK = t[8]; ON_ACCENT = t[9];
        CHIP_SEL = mix(t[5], 0xFFFFFFFF, 0.08f); ICON_BG = t[5];
        BLUE_BANNER = mix(t[2], t[4], 0.5f);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Flat tonal surface (no gradient), single color inside a large-radius container. */
    static Drawable panel(Context c, int color, float radiusDp) {
        return round(c, color, radiusDp);
    }

    static GradientDrawable outline(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = round(c, fill, radiusDp);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** Wraps a background so taps show a ripple. */
    static Drawable ripple(Drawable bg) {
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), bg, null);
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static LinearLayout.LayoutParams wrapLp(Context c, float l, float t, float r, float b) {
        return lp(c, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, l, t, r, b);
    }

    static LinearLayout.LayoutParams fullLp(Context c, float t, float b) {
        return lp(c, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, t, 0, b);
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Card container: flat surface on surfaceContainerHigh. */
    static LinearLayout card(Context c, int color) {
        LinearLayout l = vbox(c);
        l.setBackground(round(c, color, 16));
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        return l;
    }

    /** Rounded square holding one glyph, used as the icon of tiles and cards. */
    static TextView iconBox(Context c, String glyph, int bg, int fg, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(glyph);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDp * 0.42f);
        t.setTextColor(fg);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(c, bg, sizeDp * 0.32f));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    static TextView badge(Context c, String label, int fg, int bg) {
        TextView t = text(c, label, 11, fg, true);
        t.setLetterSpacing(0.05f);
        t.setSingleLine(true);
        t.setBackground(round(c, bg, 8));
        t.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        return t;
    }

    static View chip(Context c, String label, boolean selected, View.OnClickListener l) {
        TextView t = text(c, label, 13, selected ? TEXT : MUTED, selected);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        if (selected) t.setBackground(ripple(round(c, CHIP_TOP, 18)));
        else t.setBackground(ripple(outline(c, Color.TRANSPARENT, OUTLINE, 18)));
        t.setPadding(dp(c, 13), dp(c, 8), dp(c, 13), dp(c, 8));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    /** Chip with an optional leading vector icon (used by the file tabs). */
    static LinearLayout chip(Context c, String label, int icon, boolean selected, View.OnClickListener l) {
        LinearLayout w = hbox(c);
        w.setBackground(selected ? ripple(round(c, CHIP_TOP, 18))
                : ripple(outline(c, Color.TRANSPARENT, OUTLINE, 18)));
        w.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        w.setClickable(true);
        w.setOnClickListener(l);
        if (icon != 0) w.addView(icon(c, icon, 14, selected ? TEXT : MUTED), wrapLp(c, 0, 0, 6, 0));
        TextView t = text(c, label, 13, selected ? TEXT : MUTED, selected);
        TextView t = text(c, label, 15, selected ? TEXT : MUTED, selected);
        t.setSingleLine(true);
        w.addView(t);
        return w;
    }

    /** Horizontally scrolling row of chips. */
    static HorizontalScrollView chipRow(Context c) {
        HorizontalScrollView sv = new HorizontalScrollView(c);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = hbox(c);
        sv.addView(row);
        return sv;
    }

    static void addChip(HorizontalScrollView row, View chip) {
        ((LinearLayout) row.getChildAt(0)).addView(chip, wrapLp(row.getContext(), 0, 0, 8, 0));
    }

    static TextView primaryButton(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 14, ON_ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(round(c, ACCENT, 30)));
        t.setPadding(dp(c, 20), dp(c, 11), dp(c, 20), dp(c, 11));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    static TextView outlineButton(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 14, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(outline(c, Color.TRANSPARENT, OUTLINE, 30)));
        t.setPadding(dp(c, 20), dp(c, 11), dp(c, 20), dp(c, 11));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    /** Colored strip with a leading vector icon. */
    static LinearLayout banner(Context c, int kind, CharSequence msg, int bg, int fg) {
        LinearLayout l = hbox(c);
        l.setBackground(round(c, bg, 14));
        l.setPadding(dp(c, 13), dp(c, 10), dp(c, 13), dp(c, 10));
        if (kind != 0) l.addView(icon(c, kind, 17, fg), wrapLp(c, 0, 0, 11, 0));
        TextView m = text(c, msg, 13, fg, false);
        m.setLineSpacing(0, 1.2f);
        l.addView(m, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return l;
    }

    static TextView mono(Context c, CharSequence s, float sp, int color) {
        TextView t = text(c, s, sp, color, false);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    static TextView ellipsized(TextView t, int maxLines) {
        t.setMaxLines(maxLines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int alpha(int color, float a) {
        return Color.argb(Math.round(a * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Widest the content column gets, so tablets and big screens do not stretch everything edge to edge. */
    static final int MAX_CONTENT_DP = 640;

    /** Vertical box that never grows wider than {@link #MAX_CONTENT_DP}. */
    static class Capped extends LinearLayout {
        private final int maxPx;
        Capped(Context c) {
            super(c);
            setOrientation(VERTICAL);
            maxPx = dp(c, MAX_CONTENT_DP);
        }
        @Override
        protected void onMeasure(int w, int h) {
            int size = MeasureSpec.getSize(w);
            if (size > maxPx) w = MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.EXACTLY);
            super.onMeasure(w, h);
        }
    }

    /** Horizontal variant of {@link Capped}. */
    static class CappedRow extends LinearLayout {
        private final int maxPx;
        CappedRow(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            maxPx = dp(c, MAX_CONTENT_DP);
        }
        @Override
        protected void onMeasure(int w, int h) {
            int size = MeasureSpec.getSize(w);
            if (size > maxPx) w = MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.EXACTLY);
            super.onMeasure(w, h);
        }
    }

    /** Tile columns for the current window: 2 on phones, 3 on wide screens, 4 on very wide ones. */
    static int tileColumns(Context c) {
        float wdp = c.getResources().getConfiguration().screenWidthDp;
        float avail = Math.min(wdp, MAX_CONTENT_DP) - 24;
        return avail >= 620 ? 4 : avail >= 460 ? 3 : 2;
    }

    /** Tile / card background: flat container lightly tinted with the accent tile color. */
    static Drawable tinted(Context c, int accent, float radiusDp) {
        return round(c, mix(CARD, accent, 0.18f), radiusDp);
    }

    /** A tinted flat vector icon from {@link Icons} in its own view. */
    static android.view.View iconBox(Context c, int kind, int bg, int fg, int sizeDp) {
        android.widget.FrameLayout box = new android.widget.FrameLayout(c);
        box.setBackground(round(c, bg, sizeDp * 0.32f));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        box.addView(icon(c, kind, sizeDp * 0.52f, fg), new android.widget.FrameLayout.LayoutParams(
                dp(c, sizeDp), dp(c, sizeDp), Gravity.CENTER));
        return box;
    }

    /** Standalone tinted icon view; icons are pre-scaled, so the view must not scale them. */
    static android.widget.ImageView icon(Context c, int kind, float sizeDp, int color) {
        int px = dp(c, sizeDp);
        android.widget.ImageView v = new android.widget.ImageView(c);
        Icons d = new Icons(kind, color);
        d.setBounds(0, 0, px, px);
        v.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        v.setImageDrawable(d);
        return v;
    }

    /** Small uppercase section label. */
    static TextView section(Context c, String label, int accent) {
        TextView t = text(c, label.toUpperCase(java.util.Locale.US), 11, accent, true);
        t.setLetterSpacing(0.12f);
        return t;
    }
}
