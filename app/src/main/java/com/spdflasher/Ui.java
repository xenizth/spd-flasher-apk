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

    static int BG = 0xFF1B1D24;
    static int CARD = 0xFF2C303B;
    static int CARD_DARK = 0xFF23262E;
    static int CHIP_SEL = 0xFF3E4559;
    static final int OUTLINE = 0xFF8E919A;
    static final int TEXT = 0xFFE3E2E9;
    static final int MUTED = 0xFFC4C6D0;
    static int ACCENT = 0xFFAAC4FF;
    static int ON_ACCENT = 0xFF0E2659;
    static int BLUE_BANNER = 0xFF2A3A5E;
    static final int RED_BANNER = 0xFF522B30;
    static final int RED_TEXT = 0xFFFFB4AB;
    static final int GREEN = 0xFF35C66B;
    static final int GREEN_BG = 0xFF24493A;
    static final int RED = 0xFFEF4444;
    static final int RED_BG = 0xFF5A2D31;
    static final int AMBER = 0xFFE8A33D;
    static final int AMBER_BG = 0xFF4A3A24;
    static int ICON_BG = 0xFF3E4559;

    // theme-dependent extras (see applyTheme)
    static int ACCENT2 = 0xFF7C9CFF, BG_MID = 0xFF151A2B, HERO = 0xFF3F5FB8, CHIP_TOP = 0xFF4C5E94, CHIP_BOT = 0xFF35406A;

    static final String[] THEME_NAMES = {"Midnight", "AMOLED", "Violet", "Emerald", "Sunset"};
    //                          accent      accent2     bgTop       bgMid       hero        chipTop     chipBot     card        cardDark    onAccent
    private static final int[][] THEMES = {
            {0xFFAAC4FF, 0xFF7C9CFF, 0xFF0D1017, 0xFF151A2B, 0xFF3F5FB8, 0xFF4C5E94, 0xFF35406A, 0xFF2C303B, 0xFF23262E, 0xFF0E2659},
            {0xFF7CF5C8, 0xFF3DDCA0, 0xFF000000, 0xFF080A10, 0xFF1F7A63, 0xFF1F5E4E, 0xFF133C32, 0xFF14171C, 0xFF0D0F13, 0xFF00382B},
            {0xFFD0B8FF, 0xFFB08CFF, 0xFF100B1A, 0xFF1C1430, 0xFF6B45C8, 0xFF5A3F94, 0xFF3C2A66, 0xFF2B2438, 0xFF211B2D, 0xFF2D1560},
            {0xFF8EE6A8, 0xFF5CD68A, 0xFF0B1410, 0xFF10201A, 0xFF2E8B57, 0xFF2F6E4D, 0xFF1F4A34, 0xFF222E28, 0xFF1A241F, 0xFF0A3A1E},
            {0xFFFFB68A, 0xFFFF9466, 0xFF160F0D, 0xFF241613, 0xFFC4572B, 0xFF9A5A3C, 0xFF6A3A28, 0xFF332522, 0xFF281C1A, 0xFF5A1D00},
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

    /** Soft diagonal gradient with a hairline border: the look of cards and tiles. */
    static GradientDrawable panel(Context c, int top, int bottom, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), 0x22FFFFFF);
        return g;
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
        on.setStroke(dp(c, 1), (ACCENT & 0x00FFFFFF) | 0x88000000);
        t.setBackground(ripple(selected ? on : outline(c, 0x00000000, 0x66FFFFFF, 14)));
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
        t.setBackground(ripple(pg));
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

    /** Tile / card background: soft gradient tinted with an accent colour and a hairline accent border. */
    static GradientDrawable tinted(Context c, int accent, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{mix(mix(CARD, 0xFFFFFFFF, 0.06f), accent, 0.16f), mix(CARD_DARK, 0xFF000000, 0.15f)});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), (accent & 0x00FFFFFF) | 0x44000000);
        return g;
    }

    /** Small uppercase section label with a coloured dot. */
    static TextView section(Context c, String label, int accent) {
        TextView t = text(c, "●  " + label.toUpperCase(java.util.Locale.US), 12, accent, true);
        t.setLetterSpacing(0.12f);
        return t;
    }
}
