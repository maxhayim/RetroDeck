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
    /** The greeting's own text, any language; "{car}" is replaced by the vehicle's name. Blank: the default. */
    public String greeting = "";

    public static final String DEFAULT_GREETING = "HELLO  {car}";
    public static final int MAX_GREETING = 60;

    public StartupConfig copy() {
        StartupConfig c = new StartupConfig();
        c.enabled = enabled;
        c.preset = preset;
        c.durationMs = durationMs;
        c.powerOn = powerOn;
        c.lcdBoot = lcdBoot;
        c.greeting = greeting;
        return c;
    }

    public StartupConfig sanitize() {
        if (!exists(preset)) preset = GLOW;
        durationMs = Math.max(MIN_MS, Math.min(MAX_MS, durationMs));
        greeting = cleanGreeting(greeting);
        return this;
    }

    /** How long the sequence actually runs; 0 when it is off. */
    public int effectiveMs() {
        if (!enabled) return 0;
        return INSTANT.equals(preset) ? INSTANT_MS : durationMs;
    }

    /** No control characters or line breaks, at most MAX_GREETING long. */
    public static String cleanGreeting(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < MAX_GREETING; i++) {
            char c = s.charAt(i);
            if (!Character.isISOControl(c)) b.append(c);
        }
        return b.toString().trim();
    }

    /** The greeting as shown for a vehicle called car (blank when unnamed). */
    public String greetingFor(String car) {
        String g = greeting == null || greeting.trim().isEmpty() ? DEFAULT_GREETING : greeting;
        g = g.replace("{car}", car == null ? "" : car).trim();
        return g.isEmpty() ? "HELLO" : g;
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
