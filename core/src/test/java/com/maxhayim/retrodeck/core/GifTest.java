package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Every frame of the test GIFs, compared with what Pillow decodes from the same files
 * (core/src/test/resources/gif; the .txt files are Pillow's frames, or their CRC32 for the big one).
 * One change from Pillow: where a frame is disposed "to background", browsers clear it, and so does
 * RetroDeck, so interlaced.txt has those pixels clear in its last frame where Pillow paints them black.
 */
public class GifTest {

    @Test
    public void transparentFramesThatClearBehindThem() throws Exception {
        Gif g = Gif.parse(bytes("moving.gif"));
        assertEquals(12, g.width);
        assertEquals(9, g.height);
        assertEquals(4, g.frameCount());
        assertEquals(50, g.delay(0));
        // A delay under 20 ms plays at 10 frames a second, as in browsers.
        assertEquals(100, g.delay(3));
        assertFrames(g, "moving.txt");
    }

    @Test
    public void interlacedAndPartialFramesWithEveryDisposal() throws Exception {
        assertFrames(Gif.parse(bytes("interlaced.gif")), "interlaced.txt");
    }

    @Test
    public void fullCodeTable() throws Exception {
        Gif g = Gif.parse(bytes("noise.gif"));
        String[] want = text("noise.txt").trim().split("\n");
        assertEquals(want.length, g.frameCount());
        for (String w : want) {
            int[] px = g.next();
            ByteBuffer b = ByteBuffer.allocate(px.length * 4);
            for (int p : px) b.putInt(p);
            CRC32 crc = new CRC32();
            crc.update(b.array());
            assertEquals(w, String.format("%08x", crc.getValue()));
        }
    }

    @Test
    public void loopsBackToTheFirstFrame() throws Exception {
        Gif g = Gif.parse(bytes("moving.gif"));
        int[] first = g.next().clone();
        for (int i = 1; i < g.frameCount(); i++) g.next();
        int[] again = g.next();
        assertEquals(0, g.current());
        assertArrayEquals(first, again);
    }

    @Test
    public void cutShortPlaysWhatIsThere() throws Exception {
        byte[] b = bytes("moving.gif");
        Gif g = Gif.parse(Arrays.copyOf(b, b.length * 2 / 3));
        assertTrue(g.frameCount() >= 1);
        g.next();
    }

    @Test
    public void refusesWhatIsNotAPlayableGif() {
        refuse("not a gif".getBytes(StandardCharsets.US_ASCII), "isn't a GIF");
        byte[] huge = header(801, 10);
        refuse(huge, "limit is 800");
        refuse(header(10, 10), "no pictures");
        refuse(new byte[Gif.MAX_BYTES + 1], "isn't a GIF");
    }

    private static byte[] header(int w, int h) {
        return new byte[]{'G', 'I', 'F', '8', '9', 'a', (byte) w, (byte) (w >> 8), (byte) h, (byte) (h >> 8), 0, 0, 0, 0x3B};
    }

    private static void refuse(byte[] b, String why) {
        try {
            Gif.parse(b);
            fail("accepted: " + why);
        } catch (Gif.FormatException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(why));
        }
    }

    private static void assertFrames(Gif g, String expected) throws IOException {
        String[] lines = text(expected).trim().split("\n");
        assertEquals(lines.length, g.frameCount());
        for (int f = 0; f < lines.length; f++) {
            String[] want = lines[f].trim().split(" ");
            int[] px = g.next();
            assertEquals(want.length, px.length);
            for (int i = 0; i < px.length; i++) {
                int w = (int) Long.parseLong(want[i], 16);
                // Pillow keeps a colour under clear pixels; only "clear" matters.
                int got = (w >>> 24) == 0 ? px[i] >>> 24 : px[i];
                int exp = (w >>> 24) == 0 ? 0 : w;
                if (got != exp) {
                    fail("frame " + f + " pixel " + (i % g.width) + "," + (i / g.width) + ": want "
                            + Integer.toHexString(exp) + " got " + Integer.toHexString(px[i]));
                }
            }
        }
    }

    private static byte[] bytes(String name) throws IOException {
        try (InputStream in = GifTest.class.getResourceAsStream("/gif/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String text(String name) throws IOException {
        return new String(bytes(name), StandardCharsets.US_ASCII);
    }
}
