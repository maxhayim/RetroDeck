package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

public class SegmentTextTest {

    private static String show(String s) {
        StringBuilder b = new StringBuilder();
        for (SegmentText.Cell c : SegmentText.cells(s)) b.append(c);
        return b.toString();
    }

    @Test
    public void latinIsSegmentsInCapitalsWithoutAccents() {
        assertEquals("CAFE!DEL!MAR", show("Café del Mar"));
    }

    @Test
    public void japaneseIsDrawnAndTakesTwoCells() {
        List<SegmentText.Cell> cells = SegmentText.cells("Road 夜のドライブ");
        assertEquals("ROAD![夜][の][ド][ラ][イ][ブ]", show("Road 夜のドライブ"));
        // Precomposed ド and ブ stay one glyph each, not ト or フ plus a loose mark.
        assertEquals(5 + 6 * 2, SegmentText.width(cells));
    }

    @Test
    public void cyrillicKeepsItsLetters() {
        assertEquals("[К][И][Н][О]!-![Й]", show("Кино - й"));
    }

    @Test
    public void hebrewReadsRightToLeft() {
        // שלום is written ש-ל-ו-ם; seen left to right it is ם-ו-ל-ש, and the second word comes first.
        assertEquals("[ם][ל][ו][ע]![ם][ו][ל][ש]", show("שלום עולם"));
        assertEquals("ABC![ם][ו][ל][ש]", show("ABC שלום"));
    }

    @Test
    public void symbolsAreDrawnNotBlanked() {
        assertEquals("A![&]!B", show("A & B"));
    }

    @Test
    public void longTextRestsThenScrollsThenRestsAtTheEnd() {
        assertEquals(0, SegmentText.scroll(10, 20, 99_999, false));
        assertEquals(0, SegmentText.scroll(30, 20, 0, false));
        assertEquals(1, SegmentText.scroll(30, 20, SegmentText.HOLD_MS, false));
        assertEquals(10, SegmentText.scroll(30, 20, SegmentText.HOLD_MS + 9 * SegmentText.STEP_MS, false));
        assertEquals(10, SegmentText.scroll(30, 20, 0, true));
    }

    @Test
    public void aWideGlyphCutByTheEdgeIsBlank() {
        List<SegmentText.Cell> cells = SegmentText.cells("AB真");
        SegmentText.Cell[] w = SegmentText.window(cells, 0, 3);
        assertEquals('A', w[0].seg);
        assertNull(w[2]);
        w = SegmentText.window(cells, 2, 2);
        assertEquals("真", w[0].glyph);
    }
}
