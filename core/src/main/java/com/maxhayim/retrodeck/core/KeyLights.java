package com.maxhayim.retrodeck.core;

import java.util.Calendar;

/**
 * Whether the key legends are backlit. In a car they light with the headlights, so by day the keys
 * show only their printed legends; with no light sensor, Auto goes by the clock.
 */
public final class KeyLights {

    public static final String AUTO = "auto", ON = "on", OFF = "off";
    public static final String[] ALL = {AUTO, ON, OFF};

    /** Auto lights the keys from this hour until MORNING. */
    public static final int EVENING = 19, MORNING = 7;

    private KeyLights() {}

    public static boolean exists(String mode) {
        for (String m : ALL) if (m.equals(mode)) return true;
        return false;
    }

    public static boolean lit(String mode, Calendar now) {
        if (ON.equals(mode)) return true;
        if (OFF.equals(mode)) return false;
        int h = now.get(Calendar.HOUR_OF_DAY);
        return h >= EVENING || h < MORNING;
    }

    public static String label(String mode) {
        if (ON.equals(mode)) return "Always on";
        if (OFF.equals(mode)) return "Off";
        return "Auto";
    }

    public static String describe(String mode) {
        if (ON.equals(mode)) return "Legends always backlit";
        if (OFF.equals(mode)) return "Printed legends only, never backlit";
        return "Backlit from " + EVENING + ":00 to " + MORNING + ":00, printed by day";
    }
}
