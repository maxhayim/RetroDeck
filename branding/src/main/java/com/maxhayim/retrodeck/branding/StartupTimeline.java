package com.maxhayim.retrodeck.branding;

/**
 * The start-up sequence as a function of time, so the animation is deterministic and testable:
 * black, manufacturer logo lights up, model badge appears, optional power-on, optional LCD start-up,
 * then a fade into the stereo. Steps that are switched off are left out and the rest share their time.
 */
public final class StartupTimeline {

    /** One moment of the sequence. Each value runs 0..1. */
    public static final class Frame {
        public float logo, badge, power, lcd, exit;
        /** Lamp flicker for the incandescent preset, around 1. */
        public float flicker = 1f;
    }

    private static final int BLACK = 0, LOGO = 1, BADGE = 2, POWER = 3, LCD = 4, EXIT = 5;
    private static final float[] WEIGHTS = {0.08f, 0.30f, 0.17f, 0.15f, 0.20f, 0.10f};

    private final StartupConfig config;
    private final float[] start = new float[6], end = new float[6];

    public StartupTimeline(StartupConfig config) {
        this.config = config.copy().sanitize();
        float total = 0;
        for (int i = 0; i < 6; i++) total += weight(i);
        float t = 0;
        for (int i = 0; i < 6; i++) {
            start[i] = t / total;
            // The badge starts while the logo is still brightening.
            t += weight(i);
            end[i] = t / total;
        }
        if (weight(BADGE) > 0) start[BADGE] = Math.max(start[LOGO], start[BADGE] - 0.06f);
    }

    private float weight(int step) {
        if (step == POWER && !config.powerOn) return 0;
        if (step == LCD && !config.lcdBoot) return 0;
        return WEIGHTS[step];
    }

    public int durationMs() {
        return config.effectiveMs();
    }

    public StartupConfig config() {
        return config;
    }

    /** The frame at elapsedMs after the sequence started. */
    public Frame at(long elapsedMs) {
        Frame f = new Frame();
        int total = durationMs();
        if (total <= 0) {
            f.exit = 1;
            return f;
        }
        if (StartupConfig.INSTANT.equals(config.preset)) {
            // No animation: the finished screen, then straight to the stereo.
            f.logo = f.badge = 1;
            f.exit = elapsedMs >= total ? 1 : 0;
            return f;
        }
        float t = Math.max(0f, Math.min(1f, elapsedMs / (float) total));
        f.logo = shape(progress(LOGO, t));
        f.badge = ease(progress(BADGE, t));
        f.power = weight(POWER) > 0 ? progress(POWER, t) : 0;
        f.lcd = weight(LCD) > 0 ? progress(LCD, t) : 0;
        f.exit = ease(progress(EXIT, t));
        if (StartupConfig.GLOW.equals(config.preset) && f.logo > 0 && f.logo < 1) {
            // A bulb warming up flickers a little before it settles.
            f.flicker = 1f - 0.18f * (1 - f.logo) * (float) Math.abs(Math.sin(elapsedMs / 37.0));
        }
        return f;
    }

    public boolean finished(long elapsedMs) {
        return elapsedMs >= durationMs();
    }

    private float progress(int step, float t) {
        if (end[step] <= start[step]) return t >= end[step] ? 1 : 0;
        return Math.max(0f, Math.min(1f, (t - start[step]) / (end[step] - start[step])));
    }

    /** How the logo comes up for each preset. */
    private float shape(float p) {
        switch (config.preset) {
            case StartupConfig.GLOW:
                // Filaments start slowly and then catch.
                return p * p * (3 - 2 * p) * (0.4f + 0.6f * p) / (0.4f + 0.6f);
            case StartupConfig.GREEN_LCD:
                // Old LCD drivers step through contrast levels.
                return Math.round(p * 6) / 6f;
            case StartupConfig.SCAN:
            case StartupConfig.CRT:
                return p;
            default:
                return ease(p);
        }
    }

    private static float ease(float p) {
        return p * p * (3 - 2 * p);
    }
}
