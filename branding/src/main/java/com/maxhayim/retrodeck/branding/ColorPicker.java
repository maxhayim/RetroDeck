package com.maxhayim.retrodeck.branding;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;

import com.maxhayim.retrodeck.core.Illumination;
import com.maxhayim.retrodeck.core.Ui;

/** Red, green and blue sliders with a swatch; works with a remote or a touchscreen. */
final class ColorPicker extends LinearLayout {

    interface Listener {
        void onColor(int color);
    }

    private int color;
    private final View swatch;
    private final Ui.Slider r, g, b;
    private final Listener listener;

    ColorPicker(Context c, int initial, Listener l) {
        super(c);
        setOrientation(VERTICAL);
        color = initial | 0xFF000000;
        listener = l;
        swatch = new View(c);
        LayoutParams lp = Ui.margins(new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 28)), c, 18, 6, 18, 4);
        addView(swatch, lp);
        r = new Ui.Slider(c, "Red", 255, (color >> 16) & 0xFF, String::valueOf, Ui.onChange(v -> update()));
        g = new Ui.Slider(c, "Green", 255, (color >> 8) & 0xFF, String::valueOf, Ui.onChange(v -> update()));
        b = new Ui.Slider(c, "Blue", 255, color & 0xFF, String::valueOf, Ui.onChange(v -> update()));
        addView(r);
        addView(g);
        addView(b);
        paintSwatch();
    }

    void set(int c) {
        color = c | 0xFF000000;
        r.set((color >> 16) & 0xFF);
        g.set((color >> 8) & 0xFF);
        b.set(color & 0xFF);
        paintSwatch();
    }

    int color() {
        return color;
    }

    private void update() {
        color = 0xFF000000 | (r.bar.getProgress() << 16) | (g.bar.getProgress() << 8) | b.bar.getProgress();
        paintSwatch();
        if (listener != null) listener.onColor(color);
    }

    private void paintSwatch() {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(Ui.dp(getContext(), 8));
        d.setColor(color);
        swatch.setBackground(d);
        swatch.setContentDescription(Illumination.hex(color));
    }
}
