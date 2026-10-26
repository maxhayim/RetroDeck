package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SegmentsTest {

    @Test
    public void lettersAreCapitalsWithoutAccents() {
        assertEquals("CAFE!DEL!MAR", StereoView.segments("Café del Mar", true));
    }

    @Test
    public void whatSegmentsCannotShowIsBlank() {
        assertEquals("A!B!!C", StereoView.segments("A&B ♪C", true));
    }

    @Test
    public void digitsOnlyLinesBlankTheLetters() {
        assertEquals("03!1:23", StereoView.segments("03 1:23", false));
        assertEquals("!!!!14", StereoView.segments("VOL 14", false));
    }
}
