package com.maxhayim.retrodeck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdaterTest {

    @Test
    public void comparesVersionsByNumber() {
        assertTrue(Updater.compare("0.17.0", "0.16.0") > 0);
        assertTrue(Updater.compare("0.10.0", "0.9.9") > 0);
        assertTrue(Updater.compare("1.0", "0.99.99") > 0);
        assertTrue(Updater.compare("0.16.0", "0.16.1") < 0);
        assertEquals(0, Updater.compare("0.16.0", "0.16"));
        assertEquals(0, Updater.compare("0.16.0", "0.16.0"));
    }

    @Test
    public void namesEveryMode() {
        assertEquals("Off", Updater.label(Updater.OFF));
        assertEquals("Notify only", Updater.label(Updater.NOTIFY));
        assertEquals("Automatic", Updater.label(Updater.AUTO));
        assertEquals(3, Updater.MODES.length);
    }
}
