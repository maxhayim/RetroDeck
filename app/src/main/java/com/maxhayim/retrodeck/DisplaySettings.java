package com.maxhayim.retrodeck;

import android.content.Context;

/** Settings → Display: when the full-screen visualizer comes on by itself. */
final class DisplaySettings {

    private static final String PREFS = "display", FULLSCREEN_AFTER = "fullscreenAfter";

    /** Seconds without a touch or key, while music plays; 0 is never. */
    static final int[] CHOICES = {0, 30, 60, 120, 300, 600};

    private DisplaySettings() {}

    static int fullscreenAfter(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(FULLSCREEN_AFTER, 0);
    }

    static void setFullscreenAfter(Context c, int seconds) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(FULLSCREEN_AFTER, seconds).apply();
    }

    static String label(int seconds) {
        if (seconds <= 0) return "Off: only when you ask for it";
        if (seconds < 60) return "After " + seconds + " seconds";
        return seconds == 60 ? "After 1 minute" : "After " + seconds / 60 + " minutes";
    }
}
