package com.maxhayim.retrodeck.branding;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ImageFormatTest {

    @Test
    public void sniffsByContentNotName() {
        assertEquals(ImageFormat.PNG, ImageFormat.sniff(TestImages.png(4, 4)));
        assertEquals(ImageFormat.JPEG, ImageFormat.sniff(TestImages.jpeg(4, 4)));
        assertEquals(ImageFormat.WEBP, ImageFormat.sniff(TestImages.webpLossy(4, 4)));
        assertEquals(ImageFormat.SVG, ImageFormat.sniff(bytes("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>")));
        assertEquals(ImageFormat.SVG, ImageFormat.sniff(bytes("  <svg viewBox=\"0 0 10 10\"></svg>")));
        assertNull(ImageFormat.sniff(bytes("#!/bin/sh\nrm -rf /")));
        assertNull(ImageFormat.sniff(bytes("<html><body>hi</body></html>")));
        assertNull(ImageFormat.sniff(new byte[]{'P', 'K', 3, 4}));
    }

    @Test
    public void readsDimensionsFromHeaders() {
        assertArrayEquals(new int[]{321, 123}, ImageFormat.dimensions(TestImages.png(321, 123), TestImages.png(321, 123).length));
        byte[] j = TestImages.jpeg(640, 200);
        assertArrayEquals(new int[]{640, 200}, ImageFormat.dimensions(j, j.length));
        byte[] w = TestImages.webpLossy(800, 600);
        assertArrayEquals(new int[]{800, 600}, ImageFormat.dimensions(w, w.length));
        w = TestImages.webpLossless(1000, 37);
        assertArrayEquals(new int[]{1000, 37}, ImageFormat.dimensions(w, w.length));
        w = TestImages.webpExtended(5000, 4000);
        assertArrayEquals(new int[]{5000, 4000}, ImageFormat.dimensions(w, w.length));
    }

    @Test
    public void truncatedHeadersAreUnknown() {
        byte[] p = TestImages.png(10, 10);
        assertNull(ImageFormat.dimensions(p, 12));
        assertNull(ImageFormat.dimensions(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}, 4));
    }

    @Test
    public void svgSafety() {
        assertNull(ImageFormat.svgProblem("<svg viewBox='0 0 1 1'><path d='M0 0h1v1z' fill='#f00'/></svg>"));
        assertNotNull(ImageFormat.svgProblem("<!DOCTYPE svg [<!ENTITY a \"aaaa\">]><svg>&a;</svg>"));
        assertNotNull(ImageFormat.svgProblem("<svg><script>alert(1)</script></svg>"));
        assertNotNull(ImageFormat.svgProblem("<svg><image href=\"https://example.com/x.png\"/></svg>"));
        assertNotNull(ImageFormat.svgProblem("<svg><image xlink:href='file:///data/x'/></svg>"));
        assertNotNull(ImageFormat.svgProblem("<svg><rect style=\"fill:url( 'http://x/y')\"/></svg>"));
        assertNotNull(ImageFormat.svgProblem("<svg><foreignObject><div/></foreignObject></svg>"));
        // Internal references are fine.
        assertNull(ImageFormat.svgProblem("<svg><rect fill='url(#grad)'/><use href='#a'/></svg>"));
    }

    @Test
    public void sampleSizeNeverDecodesBelowTheTarget() {
        assertEquals(1, ImageFormat.sampleSize(1000, 500, 1024));
        assertEquals(1, ImageFormat.sampleSize(2000, 500, 1024));
        assertEquals(2, ImageFormat.sampleSize(2048, 500, 1024));
        assertEquals(4, ImageFormat.sampleSize(4096, 4096, 1024));
        assertEquals(8, ImageFormat.sampleSize(4096, 3000, 512));
        int s = ImageFormat.sampleSize(12000, 9000, 1024);
        assertEquals(8, s);
        assertEquals(true, 12000 / s >= 1024);
    }

    @Test
    public void fitOnlyShrinks() {
        assertArrayEquals(new int[]{300, 200}, ImageFormat.fit(300, 200, 1024));
        assertArrayEquals(new int[]{1024, 512}, ImageFormat.fit(2048, 1024, 1024));
        assertArrayEquals(new int[]{256, 512}, ImageFormat.fit(1000, 2000, 512));
    }

    @Test
    public void opaqueBoundsTrimMargins() {
        int w = 10, h = 6;
        int[] px = new int[w * h];
        px[2 * w + 3] = 0xFF000000;
        px[4 * w + 7] = 0x80FFFFFF;
        assertArrayEquals(new int[]{3, 2, 8, 5}, ImageFormat.opaqueBounds(px, w, h));
        assertNull(ImageFormat.opaqueBounds(new int[w * h], w, h));
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
