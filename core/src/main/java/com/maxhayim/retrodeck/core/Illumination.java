package com.maxhayim.retrodeck.core;

/** Dashboard illumination colours, matched to the lighting of the car's own switches and gauges. */
public final class Illumination {

    public static final String AMBER = "amber";
    public static final String GREEN = "green";
    public static final String BLUE = "blue";
    public static final String RED = "red";
    public static final String WHITE = "white";
    public static final String CUSTOM = "custom";
    /** Taken from the imported logos. */
    public static final String AUTO = "auto";

    public static final String[] MODES = {AMBER, GREEN, BLUE, RED, WHITE, CUSTOM, AUTO};

    private Illumination() {}

    public static String label(String mode) {
        switch (mode) {
            case AMBER: return "Amber";
            case GREEN: return "Green";
            case BLUE: return "Blue";
            case RED: return "Red";
            case WHITE: return "White";
            case CUSTOM: return "Custom RGB";
            case AUTO: return "From logos";
            default: return mode;
        }
    }

    /** The preset colour for a mode, or 0 for custom and auto. */
    public static int preset(String mode) {
        switch (mode) {
            case AMBER: return 0xFFFFA81E;
            case GREEN: return 0xFF4CFF7A;
            case BLUE: return 0xFF3EA8FF;
            case RED: return 0xFFFF3B30;
            case WHITE: return 0xFFF2F4F8;
            default: return 0;
        }
    }

    /** The colour to light the stereo with. Falls back to amber when a custom or logo colour is missing. */
    public static int resolve(String mode, int custom, int fromLogos) {
        int c = preset(mode == null ? AMBER : mode);
        if (CUSTOM.equals(mode)) c = custom;
        else if (AUTO.equals(mode)) c = fromLogos;
        if ((c >>> 24) == 0) c = preset(AMBER);
        return 0xFF000000 | c;
    }

    /** The colour scaled towards black; 1 keeps it, 0 is black. */
    public static int dim(int color, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = Math.round(((color >> 16) & 0xFF) * f);
        int g = Math.round(((color >> 8) & 0xFF) * f);
        int b = Math.round((color & 0xFF) * f);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Between two colours: 0 is a, 1 is b. */
    public static int mix(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Perceived lightness, 0 for black to 1 for white. */
    public static float luminance(int color) {
        return (0.2126f * ((color >> 16) & 0xFF) + 0.7152f * ((color >> 8) & 0xFF) + 0.0722f * (color & 0xFF)) / 255f;
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /** Parses #RRGGBB or #AARRGGBB; returns fallback for anything else. */
    public static int parse(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (!s.startsWith("#") || (s.length() != 7 && s.length() != 9)) return fallback;
        try {
            long v = Long.parseLong(s.substring(1), 16);
            return s.length() == 7 ? (int) (0xFF000000L | v) : (int) v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
