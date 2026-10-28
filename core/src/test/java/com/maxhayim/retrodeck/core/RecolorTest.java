package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecolorTest {

    private static final int RED = 0xFFD02020;

    private static int r(int c) { return (c >> 16) & 0xFF; }
    private static int g(int c) { return (c >> 8) & 0xFF; }

    @Test
    public void blackPlasticTakesADarkShadeOfTheColour() {
        int out = Recolor.pixel(0xFF141414, RED);
        assertTrue(r(out) > g(out) * 3);
        assertTrue(r(out) < 0x80);
    }

    @Test
    public void shadingIsKept() {
        assertTrue(r(Recolor.pixel(0xFF202020, RED)) < r(Recolor.pixel(0xFF404040, RED)));
    }

    @Test
    public void whitePrintStaysWhite() {
        assertEquals(0xFFFFFFFF, Recolor.pixel(0xFFFFFFFF, RED));
    }

    @Test
    public void aColouredDisplayKeepsItsColour() {
        assertEquals(0xFF7AA03A, Recolor.pixel(0xFF7AA03A, RED));
    }

    @Test
    public void alphaIsKept() {
        assertEquals(0x80, Recolor.pixel(0x80808080, RED) >>> 24);
    }
}
