package com.spdflasher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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

    static int BG = 0xFF05060A;
    static int CARD = 0xFF0E1322;
    static int CARD_DARK = 0xFF090C15;
    static int CHIP_SEL = 0xFF0F2A44;
    static final int OUTLINE = 0xFF2E3A52;
    static final int TEXT = 0xFFE9F0FF;
    static final int MUTED = 0xFF93A4C3;
    static int ACCENT = 0xFF00E5FF;
    static int ON_ACCENT = 0xFF03141C;
    static int BLUE_BANNER = 0xFF101A38;
    static final int RED_BANNER = 0xFF3A101E;
    static final int RED_TEXT = 0xFFFF9DB0;
    static final int GREEN = 0xFF00FF9C;
    static final int GREEN_BG = 0xFF0E3A26;
    static final int RED = 0xFFFF3B5C;
    static final int RED_BG = 0xFF3A101E;
    static final int AMBER = 0xFFFFC857;
    static final int AMBER_BG = 0xFF3A2A08;
    static int ICON_BG = 0xFF0F2A44;

    // theme-dependent extras (see applyTheme)
    static int ACCENT2 = 0xFF7A5CFF, BG_MID = 0xFF0A0E1A, HERO = 0xFF14204A, CHIP_TOP = 0xFF0F2A44, CHIP_BOT = 0xFF0A1A30;

    static final String[] THEME_NAMES = {"Neon", "Synthwave", "Toxic", "Inferno", "Frost"};
    //                          accent      accent2     bgTop       bgMid       hero        chipTop     chipBot     card        cardDark    onAccent
    private static final int[][] THEMES = {
            {0xFF00E5FF, 0xFF7A5CFF, 0xFF05060A, 0xFF0A0E1A, 0xFF14204A, 0xFF0F2A44, 0xFF0A1A30, 0xFF0E1322, 0xFF090C15, 0xFF03141C},
            {0xFFFF4FD8, 0xFF9D5CFF, 0xFF0A0510, 0xFF150A24, 0xFF3A1060, 0xFF3A1454, 0xFF240D38, 0xFF180F26, 0xFF100A1A, 0xFF2A0530},
            {0xFF39FF88, 0xFFB8FF3D, 0xFF050A07, 0xFF0A1510, 0xFF0E3A24, 0xFF0F3A28, 0xFF0A2418, 0xFF0B1410, 0xFF070E0A, 0xFF03160B},
            {0xFFFF5A3C, 0xFFFFB03A, 0xFF0A0505, 0xFF160A08, 0xFF4A1608, 0xFF3E1A10, 0xFF280F08, 0xFF170E0C, 0xFF0F0908, 0xFF2E0A00},
            {0xFFB8E6FF, 0xFF7CC8FF, 0xFF070C12, 0xFF0C1420, 0xFF16324F, 0xFF1A3A55, 0xFF0F2438, 0xFF0D1520, 0xFF080E16, 0xFF0A1B2E},
    };

    /** Switches every theme colour. Call before the views are built (the activity recreates itself on change). */
    static void applyTheme(int i) {
        int[] t = THEMES[Math.max(0, Math.min(THEMES.length - 1, i))];
        ACCENT = t[0]; ACCENT2 = t[1]; BG = t[2]; BG_MID = t[3]; HERO = t[4]; CHIP_TOP = t[5]; CHIP_BOT = t[6];
        CARD = t[7]; CARD_DARK = t[8]; ON_ACCENT = t[9];
        CHIP_SEL = t[5]; ICON_BG = t[5];
        BLUE_BANNER = mix(t[2], t[4], 0.35f);
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

    /** Neon panel: diagonal gradient + accent hairline + a soft outer glow ring. */
    static Drawable panel(Context c, int top, int bottom, float radiusDp) {
        float r = dp(c, radiusDp);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        g.setCornerRadius(r);
        g.setStroke(dp(c, 1), (ACCENT & 0x00FFFFFF) | 0x30000000);
        GradientDrawable halo = new GradientDrawable();
        halo.setCornerRadius(r + dp(c, 2));
        halo.setStroke(dp(c, 3), (ACCENT & 0x00FFFFFF) | 0x26000000);
        LayerDrawable l = new LayerDrawable(new Drawable[]{halo, g});
        int inset = dp(c, 2);
        l.setLayerInset(1, inset, inset, inset, inset);
        return l;
    }

    static GradientDrawable outline(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = round(c, fill, radiusDp);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** Wraps a background so taps show a ripple. */
    static Drawable ripple(Drawable bg) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null);
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

    static LinearLayout card(Context c, int color) {
        LinearLayout l = vbox(c);
        l.setBackground(panel(c, color, mix(color, 0xFF14161C, 0.35f), 26));
        int p = dp(c, 16);
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
        t.setBackground(panel(c, bg, mix(bg, 0xFF000000, 0.3f), sizeDp * 0.3f));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    static TextView badge(Context c, String label, int fg, int bg) {
        TextView t = text(c, label, 12, fg, true);
        t.setLetterSpacing(0.05f);
        t.setSingleLine(true);
        t.setBackground(round(c, bg, 12));
        t.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        return t;
    }

    static View chip(Context c, String label, boolean selected, View.OnClickListener l) {
        TextView t = text(c, label, 15, selected ? TEXT : MUTED, selected);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{CHIP_TOP, CHIP_BOT});
        on.setCornerRadius(dp(c, 14));
        on.setStroke(dp(c, 1), (ACCENT & 0x00FFFFFF) | 0xB4000000);
        t.setBackground(ripple(selected ? on : outline(c, 0x00000000, (ACCENT & 0x00FFFFFF) | 0x55000000, 14)));
        t.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
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
        TextView t = text(c, label, 16, ON_ACCENT, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACCENT, ACCENT2});
        pg.setCornerRadius(dp(c, 26));
        GradientDrawable halo = new GradientDrawable();
        halo.setCornerRadius(dp(c, 28));
        halo.setStroke(dp(c, 4), (ACCENT & 0x00FFFFFF) | 0x59000000);
        LayerDrawable lg = new LayerDrawable(new Drawable[]{halo, pg});
        int inset = dp(c, 2);
        lg.setLayerInset(1, inset, inset, inset, inset);
        t.setBackground(ripple(lg));
        t.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    static TextView outlineButton(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 16, ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(outline(c, 0x00000000, OUTLINE, 26)));
        t.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    /** Colored info/warning strip with a leading glyph. */
    static LinearLayout banner(Context c, String glyph, CharSequence msg, int bg, int fg) {
        LinearLayout l = hbox(c);
        l.setBackground(round(c, bg, 20));
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        TextView g = text(c, glyph, 20, fg, true);
        l.addView(g, wrapLp(c, 0, 0, 14, 0));
        TextView m = text(c, msg, 14, fg, false);
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
    static final int MAX_CONTENT_DP = 760;

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
        float avail = Math.min(wdp, MAX_CONTENT_DP) - 32;
        return avail >= 700 ? 4 : avail >= 520 ? 3 : 2;
    }

    /** Tile / card background: soft gradient tinted with an accent colour and a neon glow rim. */
    static Drawable tinted(Context c, int accent, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{mix(mix(CARD, 0xFFFFFFFF, 0.06f), accent, 0.16f), mix(CARD_DARK, 0xFF000000, 0.15f)});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), (accent & 0x00FFFFFF) | 0x66000000);
        GradientDrawable halo = new GradientDrawable();
        halo.setCornerRadius(dp(c, radiusDp) + dp(c, 2));
        halo.setStroke(dp(c, 3), (accent & 0x00FFFFFF) | 0x33000000);
        LayerDrawable l = new LayerDrawable(new Drawable[]{halo, g});
        int inset = dp(c, 2);
        l.setLayerInset(1, inset, inset, inset, inset);
        return l;
    }

    /** Small uppercase section label with a coloured dot. */
    static TextView section(Context c, String label, int accent) {
        TextView t = text(c, "●  " + label.toUpperCase(java.util.Locale.US), 12, accent, true);
        t.setLetterSpacing(0.12f);
        return t;
    }
}
