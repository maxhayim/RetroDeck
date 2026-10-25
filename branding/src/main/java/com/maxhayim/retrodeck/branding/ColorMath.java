package com.maxhayim.retrodeck.branding;

/** Colour arithmetic for logos and illumination. */
public final class ColorMath {

    private ColorMath() {}

    /**
     * A 4x5 colour matrix (android.graphics.ColorMatrix layout) that blends each pixel towards the tint,
     * keeping its lightness, and then shifts brightness. Alpha is untouched, so transparent backgrounds
     * stay transparent.
     */
    public static float[] matrix(float brightness, int tint, float strength) {
        float s = Math.max(0f, Math.min(1f, strength));
        float tr = ((tint >> 16) & 0xFF) / 255f, tg = ((tint >> 8) & 0xFF) / 255f, tb = (tint & 0xFF) / 255f;
        float off = Math.max(-1f, Math.min(1f, brightness)) * 255f;
        float k = 1 - s;
        return new float[]{
                k + s * tr * 0.299f, s * tr * 0.587f, s * tr * 0.114f, 0, off,
                s * tg * 0.299f, k + s * tg * 0.587f, s * tg * 0.114f, 0, off,
                s * tb * 0.299f, s * tb * 0.587f, k + s * tb * 0.114f, 0, off,
                0, 0, 0, 1, 0};
    }

    public static boolean isIdentity(float brightness, float strength) {
        return Math.abs(brightness) < 0.001f && strength < 0.001f;
    }

    /** Applies a colour matrix to one ARGB pixel, the same way Android does. */
    public static int apply(float[] m, int argb) {
        float r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF, a = (argb >>> 24);
        int nr = clamp(m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4]);
        int ng = clamp(m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9]);
        int nb = clamp(m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14]);
        int na = clamp(m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19]);
        return (na << 24) | (nr << 16) | (ng << 8) | nb;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    /** Relative luminance-ish lightness of a colour, 0..1. */
    public static float lightness(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
    }
}
