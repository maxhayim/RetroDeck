package com.maxhayim.retrodeck.core;

import android.graphics.Bitmap;
import android.graphics.RectF;

/**
 * Paints a radio's body in another colour while keeping its shading: dark plastic becomes a dark
 * shade of the colour, metal a bright one, and light print and highlights stay light. Used on the
 * rendered faceplate pictures, which have their own colours baked in. Parts that already have a
 * colour of their own, such as a display or a lamp, keep it: only the greys and blacks change.
 */
public final class Recolor {

    /** How much brighter than the picture the colour is laid on, so black plastic still shows it. */
    static final float GAIN = 3f;

    private Recolor() {}

    /** One pixel, 0xAARRGGBB. */
    static int pixel(int argb, int color) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
        float t = l * GAIN;
        float cr = ((color >> 16) & 0xFF) / 255f, cg = ((color >> 8) & 0xFF) / 255f, cb = (color & 0xFF) / 255f;
        float or, og, ob;
        if (t <= 1f) {
            or = cr * t;
            og = cg * t;
            ob = cb * t;
        } else {
            // Above the colour itself, highlights run up to white.
            float k = Math.min(1f, (t - 1f) / (GAIN - 1f));
            or = cr + (1f - cr) * k;
            og = cg + (1f - cg) * k;
            ob = cb + (1f - cb) * k;
        }
        // Grey takes the colour fully; anything clearly coloured is left as it is.
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float sat = max < 24 ? 0f : (max - min) / (float) max;
        float k = 1f - Math.max(0f, Math.min(1f, (sat - 0.08f) / 0.12f));
        if (k <= 0f) return argb;
        int nr = Math.round((r / 255f + (or - r / 255f) * k) * 255);
        int ng = Math.round((g / 255f + (og - g / 255f) * k) * 255);
        int nb = Math.round((b / 255f + (ob - b / 255f) * k) * 255);
        return (argb & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
    }

    /** Recolours bm inside body, all but the display window; bm must be mutable. */
    public static void apply(Bitmap bm, int color, RectF body, RectF window) {
        int x0 = Math.max(0, Math.round(body.left)), x1 = Math.min(bm.getWidth(), Math.round(body.right));
        int y0 = Math.max(0, Math.round(body.top)), y1 = Math.min(bm.getHeight(), Math.round(body.bottom));
        if (x1 <= x0 || y1 <= y0) return;
        int[] row = new int[x1 - x0];
        for (int y = y0; y < y1; y++) {
            bm.getPixels(row, 0, row.length, x0, y, row.length, 1);
            boolean inWindowRow = y >= window.top && y < window.bottom;
            for (int i = 0; i < row.length; i++) {
                int x = x0 + i;
                if (inWindowRow && x >= window.left && x < window.right) continue;
                row[i] = pixel(row[i], color);
            }
            bm.setPixels(row, 0, row.length, x0, y, row.length, 1);
        }
    }
}
