package com.maxhayim.retrodeck.branding;

/** The welcome sequence shown when RetroDeck opens. */
public final class StartupConfig {

    public static final String GLOW = "glow";
    public static final String GREEN_LCD = "green_lcd";
    public static final String SCAN = "scan";
    public static final String CRT = "crt";
    public static final String FADE = "fade";
    public static final String INSTANT = "instant";

    public static final String[] PRESETS = {GLOW, GREEN_LCD, SCAN, CRT, FADE, INSTANT};

    public static final int MIN_MS = 1000, MAX_MS = 6000, DEFAULT_MS = 3000;
    /** The minimal preset shows the finished screen this long, with no animation. */
    public static final int INSTANT_MS = 600;

    public boolean enabled = true;
    public String preset = GLOW;
    public int durationMs = DEFAULT_MS;
    /** Include the retro power-on step. */
    public boolean powerOn = true;
    /** Include the simulated LCD start-up text. */
    public boolean lcdBoot = true;

    public StartupConfig copy() {
        StartupConfig c = new StartupConfig();
        c.enabled = enabled;
        c.preset = preset;
        c.durationMs = durationMs;
        c.powerOn = powerOn;
        c.lcdBoot = lcdBoot;
        return c;
    }

    public StartupConfig sanitize() {
        if (!exists(preset)) preset = GLOW;
        durationMs = Math.max(MIN_MS, Math.min(MAX_MS, durationMs));
        return this;
    }

    /** How long the sequence actually runs; 0 when it is off. */
    public int effectiveMs() {
        if (!enabled) return 0;
        return INSTANT.equals(preset) ? INSTANT_MS : durationMs;
    }

    public static boolean exists(String p) {
        for (String s : PRESETS) if (s.equals(p)) return true;
        return false;
    }

    public static String label(String p) {
        switch (p) {
            case GLOW: return "Classic incandescent glow";
            case GREEN_LCD: return "1980s green LCD";
            case SCAN: return "1990s digital scan";
            case CRT: return "CRT-style power-on";
            case FADE: return "Modern fade";
            case INSTANT: return "Minimal instant startup";
            default: return p;
        }
    }
}
