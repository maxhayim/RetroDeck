package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IlluminationTest {

    @Test
    public void presetsResolveToTheirColour() {
        assertEquals(Illumination.preset(Illumination.GREEN), Illumination.resolve(Illumination.GREEN, 0, 0));
        assertEquals(Illumination.preset(Illumination.RED), Illumination.resolve(Illumination.RED, 0xFF123456, 0xFF654321));
    }

    @Test
    public void customAndLogoColours() {
        assertEquals(0xFF123456, Illumination.resolve(Illumination.CUSTOM, 0xFF123456, 0));
        assertEquals(0xFF654321, Illumination.resolve(Illumination.AUTO, 0, 0xFF654321));
    }

    @Test
    public void missingLogoColourFallsBackToAmber() {
        assertEquals(Illumination.preset(Illumination.AMBER), Illumination.resolve(Illumination.AUTO, 0, 0));
        assertEquals(Illumination.preset(Illumination.AMBER), Illumination.resolve(null, 0, 0));
    }

    @Test
    public void parseAndHex() {
        assertEquals(0xFFFFA81E, Illumination.parse("#FFA81E", 0));
        assertEquals(0x80FFA81E, Illumination.parse("#80FFA81E", 0));
        assertEquals(7, Illumination.parse("orange", 7));
        assertEquals(7, Illumination.parse("#GGGGGG", 7));
        assertEquals("#FFA81E", Illumination.hex(0xFFFFA81E));
    }

    @Test
    public void dim() {
        assertEquals(0xFF000000, Illumination.dim(0xFFFFFFFF, 0));
        assertEquals(0xFF808080, Illumination.dim(0xFFFFFFFF, 0.502f));
    }
}
