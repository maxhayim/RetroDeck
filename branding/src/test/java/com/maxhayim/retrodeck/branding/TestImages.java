package com.maxhayim.retrodeck.branding;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Image files built byte by byte (the JVM test classpath has no ImageIO), for header and pack tests. */
final class TestImages {

    private TestImages() {}

    /** A valid, fully transparent RGBA PNG. */
    static byte[] png(int w, int h) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(ihdr);
            d.writeInt(w);
            d.writeInt(h);
            d.write(new byte[]{8, 6, 0, 0, 0});
            chunk(out, "IHDR", ihdr.toByteArray());
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DeflaterOutputStream z = new DeflaterOutputStream(raw)) {
                byte[] row = new byte[1 + w * 4];
                for (int y = 0; y < h; y++) z.write(row);
            }
            chunk(out, "IDAT", raw.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(data.length);
        byte[] t = type.getBytes("US-ASCII");
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }

    /** JPEG markers up to the frame header: SOI, APP0 (JFIF), a comment, then SOF0 with the size. */
    static byte[] jpeg(int w, int h) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int[] head = {0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
                0xFF, 0xFE, 0x00, 0x06, 'h', 'e', 'l', 'o',
                0xFF, 0xC0, 0x00, 0x11, 0x08, (h >> 8) & 0xFF, h & 0xFF, (w >> 8) & 0xFF, w & 0xFF, 3,
                1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1, 0xFF, 0xD9};
        for (int b : head) o.write(b);
        return o.toByteArray();
    }

    /** A lossy WebP header (RIFF + VP8 frame tag); enough for header parsing. */
    static byte[] webpLossy(int w, int h) {
        byte[] b = new byte[40];
        put(b, 0, "RIFF");
        put(b, 8, "WEBP");
        put(b, 12, "VP8 ");
        b[23] = (byte) 0x9D;
        b[24] = 0x01;
        b[25] = 0x2A;
        b[26] = (byte) (w & 0xFF);
        b[27] = (byte) ((w >> 8) & 0x3F);
        b[28] = (byte) (h & 0xFF);
        b[29] = (byte) ((h >> 8) & 0x3F);
        return b;
    }

    static byte[] webpLossless(int w, int h) {
        byte[] b = new byte[40];
        put(b, 0, "RIFF");
        put(b, 8, "WEBP");
        put(b, 12, "VP8L");
        b[20] = 0x2F;
        int bits = (w - 1) | ((h - 1) << 14);
        b[21] = (byte) bits;
        b[22] = (byte) (bits >> 8);
        b[23] = (byte) (bits >> 16);
        b[24] = (byte) (bits >> 24);
        return b;
    }

    static byte[] webpExtended(int w, int h) {
        byte[] b = new byte[40];
        put(b, 0, "RIFF");
        put(b, 8, "WEBP");
        put(b, 12, "VP8X");
        int cw = w - 1, ch = h - 1;
        b[24] = (byte) cw;
        b[25] = (byte) (cw >> 8);
        b[26] = (byte) (cw >> 16);
        b[27] = (byte) ch;
        b[28] = (byte) (ch >> 8);
        b[29] = (byte) (ch >> 16);
        return b;
    }

    private static void put(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i);
    }

}
