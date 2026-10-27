package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MelodyTest {

    @Test
    public void noteNames() {
        assertEquals(440, Melody.frequency("A4"), 0.01);
        assertEquals(523.25, Melody.frequency("C5"), 0.01);
        assertEquals(830.61, Melody.frequency("G#5"), 0.01);
        assertEquals(Melody.frequency("G#5"), Melody.frequency("Ab5"), 0.01);
        assertEquals(0, Melody.frequency("-"), 0);
        assertEquals(0, Melody.frequency("X9"), 0);
        assertEquals(0, Melody.frequency("C"), 0);
    }

    @Test
    public void everyMelodyIsAShortChime() {
        assertTrue(Melody.all().size() >= 4);
        for (Melody m : Melody.all()) {
            assertNotNull(Melody.get(m.id));
            long ms = m.lengthMs();
            assertTrue(m.id + " " + ms, ms >= 3000 && ms <= 9000);
            short[] pcm = m.render();
            assertTrue(pcm.length > Melody.RATE * ms / 1000);
            int peak = 0;
            for (short s : pcm) peak = Math.max(peak, Math.abs(s));
            // Loud enough to hear, never clipped.
            assertTrue(m.id + " peak " + peak, peak > 20000 && peak < Short.MAX_VALUE);
            // Rings out to silence.
            assertTrue(Math.abs(pcm[pcm.length - 1]) < 200);
        }
    }
}
