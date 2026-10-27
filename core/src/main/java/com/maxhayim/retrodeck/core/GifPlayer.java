package com.maxhayim.retrodeck.core;

import android.graphics.Bitmap;

/**
 * Plays a {@link Gif} on the display: moves through its frames as time passes, faster when the music
 * is loud, and hands over the current frame as a bitmap, in its own colours or lit in the display's.
 */
public final class GifPlayer {

    private final Gif gif;
    private final int[] out;
    private Bitmap bitmap;
    private int[] frame;
    private float into;
    private boolean dirty = true;
    private int tintedWith = 0;

    public GifPlayer(Gif gif) {
        this.gif = gif;
        this.out = new int[gif.width * gif.height];
        this.frame = gif.next();
    }

    public int width() { return gif.width; }

    public int height() { return gif.height; }

    /** Moves the animation on by ms of time at the given speed (1 is the GIF's own pace). */
    public void advance(long ms, float speed) {
        into += ms * speed;
        // A long pause (the screen was off) shouldn't make it race to catch up.
        for (int steps = 0; into >= gif.delay(gif.current()) && steps < 4; steps++) {
            into -= gif.delay(gif.current());
            frame = gif.next();
            dirty = true;
        }
        into = Math.min(into, gif.delay(gif.current()));
    }

    /**
     * The current frame. With a tint, each pixel is lit in that colour as brightly as it was bright,
     * like an animation on a one-colour display; 0 keeps the GIF's own colours. Null when there's no
     * memory for it.
     */
    public Bitmap bitmap(int tint) {
        if (bitmap == null) {
            try {
                bitmap = Bitmap.createBitmap(gif.width, gif.height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                return null;
            }
            dirty = true;
        }
        if (!dirty && tint == tintedWith) return bitmap;
        if (tint == 0) {
            bitmap.setPixels(frame, 0, gif.width, 0, 0, gif.width, gif.height);
        } else {
            int rgb = tint & 0xFFFFFF;
            for (int i = 0; i < frame.length; i++) {
                int p = frame[i];
                int lum = (((p >> 16) & 0xFF) * 77 + ((p >> 8) & 0xFF) * 151 + (p & 0xFF) * 28) >> 8;
                int a = ((p >>> 24) * lum) / 255;
                out[i] = a << 24 | rgb;
            }
            bitmap.setPixels(out, 0, gif.width, 0, 0, gif.width, gif.height);
        }
        dirty = false;
        tintedWith = tint;
        return bitmap;
    }

    public void release() {
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
    }
}
