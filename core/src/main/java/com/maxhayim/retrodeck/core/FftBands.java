package com.maxhayim.retrodeck.core;

/**
 * Turns one frame of Android's visualizer FFT into the visualizer's band levels: 16 bands spaced
 * evenly in pitch from the bass to the treble, as a graphic equaliser's are, each 0..1.
 */
public final class FftBands {

    /** The lowest and highest band edges, in Hz. */
    static final float LOW = 50f, HIGH = 14000f;
    /** Levels in dB of the 8-bit FFT: below FLOOR is silence, FLOOR + RANGE is full scale. */
    static final float FLOOR = 8f, RANGE = 34f;

    private FftBands() {}

    /**
     * fft is Visualizer.getFft's output: the real parts of DC and the Nyquist bin first, then a
     * real and imaginary byte for each bin. rateHz is the capture's sampling rate.
     */
    public static void read(byte[] fft, int rateHz, float[] out) {
        int n = fft.length;
        int bins = n / 2;
        float binHz = rateHz / (float) n;
        for (int b = 0; b < out.length; b++) {
            float f0 = edge(b, out.length), f1 = edge(b + 1, out.length);
            int k0 = Math.max(1, (int) Math.floor(f0 / binHz));
            int k1 = Math.min(bins - 1, Math.max(k0, (int) Math.ceil(f1 / binHz) - 1));
            float peak = 0;
            for (int k = k0; k <= k1; k++) {
                float re = fft[2 * k], im = fft[2 * k + 1];
                peak = Math.max(peak, (float) Math.sqrt(re * re + im * im));
            }
            float db = peak <= 1f ? 0f : (float) (20 * Math.log10(peak));
            out[b] = Math.max(0f, Math.min(1f, (db - FLOOR) / RANGE));
        }
    }

    /** Band i's lower edge in Hz; band count's edge is the top. */
    static float edge(int i, int count) {
        return (float) (LOW * Math.pow(HIGH / LOW, i / (double) count));
    }
}
