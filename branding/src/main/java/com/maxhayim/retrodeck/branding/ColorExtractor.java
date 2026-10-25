package com.maxhayim.retrodeck.branding;

/**
 * Picks an illumination colour from a logo: the most common strongly coloured pixel group, brightened
 * so it reads as light on a dark dashboard. Grey, black, white and transparent pixels don't count, so
 * a red badge on a chrome background gives red.
 */
public final class ColorExtractor {

    private ColorExtractor() {}

    /** The colour, or 0 when the logo has no clear colour (an all-chrome or black and white logo). */
    public static int dominant(int[] argb) {
        if (argb == null || argb.length == 0) return 0;
        float[] weight = new float[4096];
        long[] sumR = new long[4096], sumG = new long[4096], sumB = new long[4096];
        int[] count = new int[4096];
        int counted = 0;
        for (int px : argb) {
            if ((px >>> 24) < 128) continue;
            int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, b = px & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max < 50) continue;
            float sat = (max - min) / (float) max;
            if (sat < 0.28f) continue;
            int bucket = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
            weight[bucket] += sat;
            sumR[bucket] += r;
            sumG[bucket] += g;
            sumB[bucket] += b;
            count[bucket]++;
            counted++;
        }
        if (counted == 0) return 0;
        // Merge each bucket with its neighbours so gradients and anti-aliased edges count together.
        int best = -1;
        float bestW = 0;
        for (int i = 0; i < 4096; i++) {
            if (count[i] == 0) continue;
            float w = 0;
            int r = i >> 8, g = (i >> 4) & 15, b = i & 15;
            for (int dr = -1; dr <= 1; dr++) for (int dg = -1; dg <= 1; dg++) for (int db = -1; db <= 1; db++) {
                int nr = r + dr, ng = g + dg, nb = b + db;
                if (nr < 0 || ng < 0 || nb < 0 || nr > 15 || ng > 15 || nb > 15) continue;
                w += weight[(nr << 8) | (ng << 4) | nb];
            }
            if (w > bestW) {
                bestW = w;
                best = i;
            }
        }
        // Too few coloured pixels: a stray highlight shouldn't decide the dashboard colour.
        if (count[best] * 50 < argb.length && counted * 20 < argb.length) return 0;
        int r = (int) (sumR[best] / count[best]), g = (int) (sumG[best] / count[best]), b = (int) (sumB[best] / count[best]);
        return brighten(r, g, b);
    }

    /** Scales the colour up so its brightest channel is full, like a lit lamp behind coloured glass. */
    static int brighten(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        if (max == 0) return 0;
        float f = 255f / max;
        return 0xFF000000 | (Math.min(255, Math.round(r * f)) << 16) | (Math.min(255, Math.round(g * f)) << 8)
                | Math.min(255, Math.round(b * f));
    }
}
