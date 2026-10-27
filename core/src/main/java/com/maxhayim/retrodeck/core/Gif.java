package com.maxhayim.retrodeck.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An animated GIF, decoded one frame at a time so a long animation never needs every frame in
 * memory. Plain Java: Android's own GIF support is either missing on old head units or can't be
 * slowed down and sped up with the music.
 *
 * <p>Reading is strict about size and lenient about damage: a file that is too big is refused, and a
 * file cut short plays the frames it has.
 */
public final class Gif {

    /** Largest screen accepted, a side. */
    public static final int MAX_SIDE = 800;
    public static final int MAX_FRAMES = 500;
    public static final int MAX_BYTES = 8 * 1024 * 1024;

    /** A GIF RetroDeck can't play. The message is shown to the user. */
    public static final class FormatException extends Exception {
        public FormatException(String message) {
            super(message);
        }
    }

    private static final class Frame {
        int x, y, w, h;
        boolean interlace, transparent;
        int transIndex, dispose, delayMs;
        int[] colors;
        int minCodeSize;
        int data;   // offset of the first data sub-block
    }

    public final int width, height;
    private final byte[] bytes;
    private final List<Frame> frames = new ArrayList<>();
    private final int[] global;

    private final int[] canvas;
    private int[] saved;
    private int current = -1;

    // LZW tables, reused for every frame.
    private final short[] prefix = new short[4096];
    private final byte[] suffix = new byte[4096];
    private final byte[] stack = new byte[4097];
    private byte[] indices;

    private Gif(byte[] bytes, int width, int height, int[] global) {
        this.bytes = bytes;
        this.width = width;
        this.height = height;
        this.global = global;
        this.canvas = new int[width * height];
    }

    /** Reads a GIF's structure. Throws when it isn't a GIF or is larger than RetroDeck plays. */
    public static Gif parse(byte[] b) throws FormatException {
        if (b == null || b.length < 13 || b[0] != 'G' || b[1] != 'I' || b[2] != 'F') {
            throw new FormatException("This isn't a GIF file.");
        }
        if (b.length > MAX_BYTES) throw new FormatException("The GIF is larger than " + (MAX_BYTES >> 20) + " MB.");
        int w = u16(b, 6), h = u16(b, 8);
        if (w == 0 || h == 0) throw new FormatException("The GIF has no size.");
        if (w > MAX_SIDE || h > MAX_SIDE) {
            throw new FormatException("The GIF is " + w + "×" + h + "; the limit is " + MAX_SIDE + " pixels a side.");
        }
        int packed = b[10] & 0xFF;
        int pos = 13;
        int[] global = null;
        if ((packed & 0x80) != 0) {
            int n = 2 << (packed & 7);
            if (pos + n * 3 > b.length) throw new FormatException("The GIF is damaged.");
            global = colors(b, pos, n);
            pos += n * 3;
        }
        Gif gif = new Gif(b, w, h, global);

        // Graphic control for the next image.
        int dispose = 0, delay = 0, trans = -1;
        try {
            while (pos < b.length) {
                int block = b[pos++] & 0xFF;
                if (block == 0x3B) break;
                if (block == 0x21) {
                    int label = b[pos++] & 0xFF;
                    if (label == 0xF9 && (b[pos] & 0xFF) >= 4) {
                        int p = b[pos + 1] & 0xFF;
                        dispose = (p >> 2) & 7;
                        delay = u16(b, pos + 2) * 10;
                        trans = (p & 1) != 0 ? b[pos + 4] & 0xFF : -1;
                    }
                    pos = skipBlocks(b, pos);
                } else if (block == 0x2C) {
                    if (gif.frames.size() >= MAX_FRAMES) {
                        throw new FormatException("The GIF has more than " + MAX_FRAMES + " frames.");
                    }
                    Frame f = new Frame();
                    f.x = u16(b, pos);
                    f.y = u16(b, pos + 2);
                    f.w = u16(b, pos + 4);
                    f.h = u16(b, pos + 6);
                    int p = b[pos + 8] & 0xFF;
                    pos += 9;
                    f.interlace = (p & 0x40) != 0;
                    if ((p & 0x80) != 0) {
                        int n = 2 << (p & 7);
                        f.colors = colors(b, pos, n);
                        pos += n * 3;
                    } else {
                        f.colors = global;
                    }
                    f.minCodeSize = b[pos++] & 0xFF;
                    f.data = pos;
                    pos = skipBlocks(b, pos);
                    f.transparent = trans >= 0;
                    f.transIndex = trans;
                    f.dispose = dispose;
                    // Browsers play very short delays at 10 frames a second; so do we.
                    f.delayMs = delay < 20 ? 100 : delay;
                    dispose = 0;
                    delay = 0;
                    trans = -1;
                    if (f.colors != null && f.w > 0 && f.h > 0 && f.minCodeSize >= 2 && f.minCodeSize <= 11) {
                        gif.frames.add(f);
                    }
                } else {
                    break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // Cut short: keep the frames read so far.
        }
        if (gif.frames.isEmpty()) throw new FormatException("The GIF has no pictures in it.");
        return gif;
    }

    public int frameCount() {
        return frames.size();
    }

    public int delay(int frame) {
        return frames.get(frame).delayMs;
    }

    /** Total length of one loop, in milliseconds. */
    public int duration() {
        int d = 0;
        for (Frame f : frames) d += f.delayMs;
        return d;
    }

    public int current() {
        return current;
    }

    /**
     * Moves to the next frame (or back to the first) and returns the picture, ARGB, width × height.
     * The array is reused: read it before the next call.
     */
    public int[] next() {
        int n = current + 1 >= frames.size() ? 0 : current + 1;
        if (n == 0) {
            Arrays.fill(canvas, 0);
            saved = null;
        } else {
            Frame prev = frames.get(current);
            if (prev.dispose == 2) {
                clear(prev);
            } else if (prev.dispose == 3 && saved != null) {
                System.arraycopy(saved, 0, canvas, 0, canvas.length);
            }
        }
        Frame f = frames.get(n);
        if (f.dispose == 3) {
            if (saved == null) saved = new int[canvas.length];
            System.arraycopy(canvas, 0, saved, 0, canvas.length);
        }
        draw(f);
        current = n;
        return canvas;
    }

    private void clear(Frame f) {
        for (int y = Math.max(0, f.y); y < Math.min(height, f.y + f.h); y++) {
            int row = y * width;
            for (int x = Math.max(0, f.x); x < Math.min(width, f.x + f.w); x++) canvas[row + x] = 0;
        }
    }

    private void draw(Frame f) {
        int count = f.w * f.h;
        if (indices == null || indices.length < count) indices = new byte[count];
        int got = decode(f, indices, count);
        int[] col = f.colors;
        int pass = 1, step = 8, line = 0;
        for (int i = 0; i < f.h; i++) {
            int row = i;
            if (f.interlace) {
                if (line >= f.h) {
                    pass++;
                    switch (pass) {
                        case 2: line = 4; break;
                        case 3: line = 2; step = 4; break;
                        default: line = 1; step = 2; break;
                    }
                }
                row = line;
                line += step;
            }
            int y = f.y + row;
            if (y < 0 || y >= height) continue;
            int src = i * f.w;
            if (src >= got) break;
            int dst = y * width;
            for (int x = 0; x < f.w && src + x < got; x++) {
                int px = f.x + x;
                if (px < 0 || px >= width) continue;
                int idx = indices[src + x] & 0xFF;
                if (f.transparent && idx == f.transIndex) continue;
                if (idx < col.length) canvas[dst + px] = col[idx];
            }
        }
    }

    /** LZW-decodes a frame's pixels into out; returns how many came out (fewer if the data is short). */
    private int decode(Frame f, byte[] out, int count) {
        int clear = 1 << f.minCodeSize, eoi = clear + 1;
        int codeSize = f.minCodeSize + 1, mask = (1 << codeSize) - 1, avail = clear + 2;
        for (int i = 0; i < clear; i++) {
            prefix[i] = 0;
            suffix[i] = (byte) i;
        }
        int pos = f.data, blockLeft = 0;
        int datum = 0, bits = 0, old = -1, first = 0, top = 0, n = 0;
        while (n < count) {
            if (top == 0) {
                if (bits < codeSize) {
                    if (blockLeft == 0) {
                        if (pos >= bytes.length) break;
                        blockLeft = bytes[pos++] & 0xFF;
                        if (blockLeft == 0) break;
                    }
                    if (pos >= bytes.length) break;
                    datum |= (bytes[pos++] & 0xFF) << bits;
                    bits += 8;
                    blockLeft--;
                    continue;
                }
                int code = datum & mask;
                datum >>>= codeSize;
                bits -= codeSize;
                if (code == clear) {
                    codeSize = f.minCodeSize + 1;
                    mask = (1 << codeSize) - 1;
                    avail = clear + 2;
                    old = -1;
                    continue;
                }
                if (code == eoi || code > avail) break;
                if (old == -1) {
                    stack[top++] = suffix[code];
                    old = code;
                    first = code;
                    continue;
                }
                int in = code;
                if (code == avail) {
                    stack[top++] = (byte) first;
                    code = old;
                }
                while (code > clear && top < stack.length - 1) {
                    stack[top++] = suffix[code];
                    code = prefix[code];
                }
                first = suffix[code] & 0xFF;
                stack[top++] = (byte) first;
                if (avail < 4096) {
                    prefix[avail] = (short) old;
                    suffix[avail] = (byte) first;
                    avail++;
                    if ((avail & mask) == 0 && avail < 4096) {
                        codeSize++;
                        mask += avail;
                    }
                }
                old = in;
            }
            out[n++] = stack[--top];
        }
        return n;
    }

    private static int skipBlocks(byte[] b, int pos) {
        while (true) {
            int len = b[pos++] & 0xFF;
            if (len == 0) return pos;
            pos += len;
        }
    }

    private static int[] colors(byte[] b, int pos, int n) {
        int[] c = new int[256];
        for (int i = 0; i < n; i++) {
            int o = pos + i * 3;
            c[i] = 0xFF000000 | (b[o] & 0xFF) << 16 | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF);
        }
        return c;
    }

    private static int u16(byte[] b, int pos) {
        return (b[pos] & 0xFF) | (b[pos + 1] & 0xFF) << 8;
    }
}
