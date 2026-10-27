package com.maxhayim.retrodeck.core;

/** Visualizer styles the LCD can show. */
public final class Visualizers {

    public static final String BARS = "bars";
    public static final String LADDER = "ladder";
    public static final String SCOPE = "scope";
    /** A GIF chosen in Vehicle Branding, moving with the music. */
    public static final String ANIMATION = "animation";
    public static final String OFF = "off";

    public static final String[] ALL = {BARS, LADDER, SCOPE, ANIMATION, OFF};

    private Visualizers() {}

    public static String label(String id) {
        switch (id) {
            case BARS: return "Spectrum bars";
            case LADDER: return "Level ladder";
            case SCOPE: return "Oscilloscope";
            case ANIMATION: return "Animation (GIF)";
            case OFF: return "Off";
            default: return id;
        }
    }

    public static boolean exists(String id) {
        for (String v : ALL) if (v.equals(id)) return true;
        return false;
    }

    public static String next(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(id)) return ALL[(i + 1) % ALL.length];
        return BARS;
    }
}
