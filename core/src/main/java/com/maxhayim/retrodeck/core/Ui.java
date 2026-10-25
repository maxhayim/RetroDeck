package com.maxhayim.retrodeck.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Shared look for RetroDeck's settings screens: dark panels, rows big enough to hit while parked
 * (56 dp), and text that stays readable on a 1024x600 screen in sunlight. Built in code so every
 * module looks the same without sharing layout files.
 */
public final class Ui {

    public static final int BG = 0xFF0A0B0E;
    public static final int PANEL = 0xFF121419;
    public static final int ROW = 0xFF181B21;
    public static final int ROW_DOWN = 0xFF262A33;
    public static final int LINE = 0xFF262A31;
    public static final int TEXT = 0xFFF2F4F8;
    public static final int SUB = 0xFF9AA3B2;
    public static final int ACCENT = 0xFFFFA81E;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** Full screen with the system bars hidden, as a head unit app should be. */
    public static void immersive(Activity a) {
        View d = a.getWindow().getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        d.setSystemUiVisibility(flags);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** A screen title with a back button. */
    public static LinearLayout header(final Activity a, String title, String subtitle) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a, 8), dp(a, 6), dp(a, 16), dp(a, 6));
        bar.setBackgroundColor(PANEL);
        TextView back = text(a, "‹", 34, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setBackground(pressable(a, 0x00000000, ROW_DOWN, 24));
        back.setOnClickListener(v -> a.onBackPressed());
        bar.addView(back, new LinearLayout.LayoutParams(dp(a, 52), dp(a, 52)));
        LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(a, 8), 0, 0, 0);
        titles.addView(text(a, title, 21, TEXT, true));
        if (subtitle != null) titles.addView(text(a, subtitle, 13, SUB, false));
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return bar;
    }

    /** A settings row: title, current value underneath. Click opens the setting. */
    public static final class Row extends LinearLayout {
        public final TextView title, value;

        public Row(Context c, String titleText, String valueText) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(c, 60));
            setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8));
            setBackground(pressable(c, ROW, ROW_DOWN, 0));
            setFocusable(true);
            setClickable(true);
            title = text(c, titleText, 17, TEXT, false);
            value = text(c, valueText == null ? "" : valueText, 13, SUB, false);
            value.setSingleLine(true);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(title);
            addView(value);
            value.setVisibility(valueText == null ? GONE : VISIBLE);
        }

        public Row value(String s) {
            value.setText(s);
            value.setVisibility(s == null ? GONE : VISIBLE);
            return this;
        }
    }

    public static Row row(Context c, String title, String value, View.OnClickListener l) {
        Row r = new Row(c, title, value);
        r.setOnClickListener(l);
        return r;
    }

    /** A small grey heading between groups of rows. */
    public static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.US), 12, ACCENT, true);
        t.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 6));
        if (Build.VERSION.SDK_INT >= 21) t.setLetterSpacing(0.12f);
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    /** A rounded button. Primary ones are filled with the accent colour. */
    public static TextView button(Context c, String s, boolean primary, View.OnClickListener l) {
        TextView b = text(c, s, 15, primary ? 0xFF111111 : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 48));
        b.setMinWidth(dp(c, 96));
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        b.setBackground(primary ? pressable(c, ACCENT, 0xFFFFC560, 10) : pressable(c, 0xFF262A33, 0xFF343945, 10));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** A toggle chip; selected ones are outlined in the accent colour. */
    public static TextView chip(Context c, String s, boolean selected, View.OnClickListener l) {
        TextView b = text(c, s, 14, selected ? ACCENT : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 44));
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 22));
        g.setColor(selected ? 0x26FFA81E : 0xFF20242B);
        g.setStroke(dp(c, selected ? 2 : 1), selected ? ACCENT : LINE);
        b.setBackground(g);
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** A labelled slider with its value shown at the end. */
    public static final class Slider extends LinearLayout {
        public interface Format {
            String format(int progress);
        }

        public final SeekBar bar;
        private final TextView value;
        private final Format format;

        public Slider(Context c, String label, int max, int progress, Format format, final SeekBar.OnSeekBarChangeListener l) {
            super(c);
            this.format = format;
            setOrientation(VERTICAL);
            setPadding(dp(c, 18), dp(c, 6), dp(c, 18), dp(c, 2));
            LinearLayout top = new LinearLayout(c);
            top.setOrientation(HORIZONTAL);
            top.addView(text(c, label, 15, TEXT, false), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            value = text(c, format.format(progress), 14, SUB, false);
            top.addView(value);
            addView(top);
            bar = new SeekBar(c);
            bar.setMax(max);
            bar.setProgress(progress);
            bar.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
            if (Build.VERSION.SDK_INT >= 21) {
                bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
                bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
            }
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    value.setText(Slider.this.format.format(p));
                    if (l != null) l.onProgressChanged(s, p, fromUser);
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                    if (l != null) l.onStartTrackingTouch(s);
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                    if (l != null) l.onStopTrackingTouch(s);
                }
            });
            addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(c, 44)));
        }

        public void set(int progress) {
            bar.setProgress(progress);
            value.setText(format.format(progress));
        }
    }

    /** java.util.function.IntConsumer needs Android 7; this works everywhere. */
    public interface IntListener {
        void accept(int value);
    }

    /** A simple change listener for sliders. */
    public static SeekBar.OnSeekBarChangeListener onChange(final IntListener f) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) f.accept(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        };
    }

    /** A row with a title and an on/off switch. */
    public static LinearLayout toggle(Context c, String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, 56));
        row.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        row.addView(text(c, label, 15, TEXT, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch s = new Switch(c);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        row.addView(s);
        row.setOnClickListener(v -> s.toggle());
        return row;
    }

    public static ScrollView scroll(Context c, View content) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.addView(content);
        return s;
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static Drawable pressable(Context c, int normal, int pressed, int radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(c, pressed, radiusDp));
        s.addState(new int[]{android.R.attr.state_focused}, rounded(c, pressed, radiusDp));
        s.addState(new int[]{}, rounded(c, normal, radiusDp));
        return s;
    }

    private static Drawable rounded(Context c, int color, int radiusDp) {
        if (radiusDp == 0) return new ColorDrawable(color);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** A dark dialog builder that matches the screens. */
    public static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c, int l, int t, int r, int b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }
}
