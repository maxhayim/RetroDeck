package com.maxhayim.retrodeck.branding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ColorTest {

    @Test
    public void neutralMatrixLeavesPixelsAlone() {
        float[] m = ColorMath.matrix(0, 0xFFFF0000, 0);
        assertEquals(0x80336699, ColorMath.apply(m, 0x80336699));
        assertTrue(ColorMath.isIdentity(0, 0));
    }

    @Test
    public void fullTintTurnsWhiteIntoTheTint() {
        float[] m = ColorMath.matrix(0, 0xFF00FF00, 1);
        assertEquals(0xFF00FF00, ColorMath.apply(m, 0xFFFFFFFF));
    }

    @Test
    public void tintKeepsTransparency() {
        float[] m = ColorMath.matrix(0.5f, 0xFFFF0000, 1);
        assertEquals(0, ColorMath.apply(m, 0x00FFFFFF) >>> 24);
    }

    @Test
    public void brightnessShifts() {
        assertEquals(0xFFFFFFFF, ColorMath.apply(ColorMath.matrix(1, 0, 0), 0xFF000000));
        assertEquals(0xFF000000, ColorMath.apply(ColorMath.matrix(-1, 0, 0), 0xFFFFFFFF));
    }

    @Test
    public void extractsTheLogoColourIgnoringChromeAndTransparency() {
        int[] px = new int[100 * 100];
        Arrays.fill(px, 0x00000000);
        for (int i = 0; i < 3000; i++) px[i] = 0xFFC8CCD0; // chrome
        for (int i = 3000; i < 4500; i++) px[i] = 0xFFD01818; // red badge
        for (int i = 4500; i < 5000; i++) px[i] = 0xFF101010; // black outline
        int c = ColorExtractor.dominant(px);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        assertEquals(255, r);
        assertTrue("got " + Integer.toHexString(c), g < 60 && b < 60);
    }

    @Test
    public void greyLogoHasNoColour() {
        int[] px = new int[400];
        Arrays.fill(px, 0xFF8A8A8A);
        assertEquals(0, ColorExtractor.dominant(px));
        assertEquals(0, ColorExtractor.dominant(new int[0]));
    }

    @Test
    public void strayPixelsDontDecide() {
        int[] px = new int[10000];
        Arrays.fill(px, 0xFFFFFFFF);
        px[5] = 0xFF0000FF;
        assertEquals(0, ColorExtractor.dominant(px));
    }

    @Test
    public void brightensDarkColours() {
        assertEquals(0xFFFF8000, ColorExtractor.brighten(0x40, 0x20, 0));
    }
}
