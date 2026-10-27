package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FftBandsTest {

    private static final int RATE = 44100, N = 1024;

    /** An FFT frame with one loud bin at this frequency and nothing else. */
    private static byte[] tone(float hz) {
        byte[] fft = new byte[N];
        int k = Math.round(hz / (RATE / (float) N));
        fft[2 * k] = 100;
        fft[2 * k + 1] = 60;
        return fft;
    }

    private static int loudest(float[] bands) {
        int best = 0;
        for (int i = 1; i < bands.length; i++) if (bands[i] > bands[best]) best = i;
        return best;
    }

    @Test
    public void silenceIsZero() {
        float[] out = new float[Levels.BANDS];
        FftBands.read(new byte[N], RATE, out);
        for (float v : out) assertEquals(0f, v, 0f);
    }

    @Test
    public void bassLightsTheLeftBandsAndTrebleTheRight() {
        float[] out = new float[Levels.BANDS];
        FftBands.read(tone(80), RATE, out);
        assertTrue(loudest(out) <= 1);
        FftBands.read(tone(10000), RATE, out);
        assertTrue(loudest(out) >= 14);
    }

    @Test
    public void aLoudToneIsNearFullScale() {
        float[] out = new float[Levels.BANDS];
        FftBands.read(tone(1000), RATE, out);
        assertTrue(out[loudest(out)] > 0.9f);
    }

    @Test
    public void everyBandSeesAtLeastOneBin() {
        // A band narrower than one bin still reads the bin it falls in, so no column is always dark.
        byte[] fft = new byte[N];
        for (int k = 1; k < N / 2; k++) fft[2 * k] = 50;
        float[] out = new float[Levels.BANDS];
        FftBands.read(fft, RATE, out);
        for (float v : out) assertTrue(v > 0.5f);
    }

    @Test
    public void edgesRiseEvenlyInPitch() {
        assertEquals(FftBands.LOW, FftBands.edge(0, 16), 0.01f);
        assertEquals(FftBands.HIGH, FftBands.edge(16, 16), 1f);
        float r1 = FftBands.edge(1, 16) / FftBands.edge(0, 16), r2 = FftBands.edge(9, 16) / FftBands.edge(8, 16);
        assertEquals(r1, r2, 0.001f);
    }
}
