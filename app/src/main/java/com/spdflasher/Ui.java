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

    static final int BG = 0xFF1B1D24;
    static final int CARD = 0xFF2C303B;
    static final int CARD_DARK = 0xFF23262E;
    static final int CHIP_SEL = 0xFF3E4559;
    static final int OUTLINE = 0xFF8E919A;
    static final int TEXT = 0xFFE3E2E9;
    static final int MUTED = 0xFFC4C6D0;
    static final int ACCENT = 0xFFAAC4FF;
    static final int ON_ACCENT = 0xFF0E2659;
    static final int BLUE_BANNER = 0xFF2A3A5E;
    static final int RED_BANNER = 0xFF522B30;
    static final int RED_TEXT = 0xFFFFB4AB;
    static final int GREEN = 0xFF35C66B;
    static final int GREEN_BG = 0xFF24493A;
    static final int RED = 0xFFEF4444;
    static final int RED_BG = 0xFF5A2D31;
    static final int AMBER = 0xFFE8A33D;
    static final int AMBER_BG = 0xFF4A3A24;
    static final int ICON_BG = 0xFF3E4559;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
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
        l.setBackground(round(c, color, 24));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    /** Rounded square holding one glyph, used as the icon of tiles and cards. */
    static TextView iconBox(Context c, String glyph, int bg, int fg, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(glyph);
        t.setTextSize(sizeDp * 0.42f);
        t.setTextColor(fg);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(c, bg, sizeDp * 0.3f));
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
        t.setBackground(ripple(selected ? round(c, CHIP_SEL, 12) : outline(c, 0x00000000, OUTLINE, 12)));
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
        t.setBackground(ripple(round(c, ACCENT, 26)));
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

    static int alpha(int color, float a) {
        return Color.argb(Math.round(a * 255), Color.red(color), Color.green(color), Color.blue(color));
    }
}
