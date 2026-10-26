package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class KeyLightsTest {

    private static Calendar at(int hour) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        return c;
    }

    @Test
    public void autoLightsTheKeysAtNightOnly() {
        assertTrue(KeyLights.lit(KeyLights.AUTO, at(22)));
        assertTrue(KeyLights.lit(KeyLights.AUTO, at(19)));
        assertTrue(KeyLights.lit(KeyLights.AUTO, at(3)));
        assertFalse(KeyLights.lit(KeyLights.AUTO, at(7)));
        assertFalse(KeyLights.lit(KeyLights.AUTO, at(13)));
    }

    @Test
    public void onAndOffIgnoreTheClock() {
        assertTrue(KeyLights.lit(KeyLights.ON, at(13)));
        assertFalse(KeyLights.lit(KeyLights.OFF, at(22)));
    }

    @Test
    public void unknownModesBehaveLikeAuto() {
        assertFalse(KeyLights.exists("dusk"));
        assertTrue(KeyLights.lit("dusk", at(23)));
    }
}
