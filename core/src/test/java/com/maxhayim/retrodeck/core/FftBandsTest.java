package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FftBandsTest {

    private static final int RATE = 44100, N = 1024;

    /** A waveform capture, unsigned 8-bit, of a sine at this frequency and level (0..1). */
    private static byte[] tone(float hz, double level, int rate) {
        byte[] wave = new byte[N];
        for (int i = 0; i < N; i++) wave[i] = (byte) Math.round(128 + 127 * level * Math.sin(2 * Math.PI * hz * i / rate));
        return wave;
    }

    /** As a device that captures at a quarter of the rate it reports: three silent samples after each real one. */
    private static byte[] padded(float hz, int reported) {
        byte[] real = tone(hz, 0.8, reported / 4);
        byte[] wave = new byte[N];
        java.util.Arrays.fill(wave, (byte) 128);
        for (int i = 0; i < N / 4; i++) wave[i * 4] = real[i];
        return wave;
    }

    private static int loudest(float[] bands) {
        int best = 0;
        for (int i = 1; i < bands.length; i++) if (bands[i] > bands[best]) best = i;
        return best;
    }

    @Test
    public void silenceIsZero() {
        float[] out = new float[Levels.BANDS];
        byte[] wave = new byte[N];
        java.util.Arrays.fill(wave, (byte) 128);
        FftBands.read(wave, RATE, out);
        for (float v : out) assertEquals(0f, v, 0f);
    }

    @Test
    public void bassLightsTheLeftBandsAndTrebleTheRight() {
        float[] out = new float[Levels.BANDS];
        FftBands.read(tone(80, 0.8, RATE), RATE, out);
        assertTrue(loudest(out) <= 1);
        FftBands.read(tone(10000, 0.8, RATE), RATE, out);
        assertTrue(loudest(out) >= 14);
    }

    @Test
    public void aLoudToneIsNearFullScaleAndAQuietOneIsLow() {
        float[] out = new float[Levels.BANDS];
        FftBands.read(tone(1000, 0.9, RATE), RATE, out);
        assertTrue(out[loudest(out)] > 0.9f);
        FftBands.read(tone(1000, 0.05, RATE), RATE, out);
        assertTrue(out[loudest(out)] < 0.3f);
    }

    @Test
    public void paddedCapturesShowBassOnlyInTheBass() {
        // Such a device's own FFT shows the bass again at a quarter of the rate, in the top band.
        byte[] wave = padded(100, 48000);
        assertEquals(4, FftBands.padding(wave));
        float[] out = new float[Levels.BANDS];
        FftBands.read(wave, 48000, out);
        assertTrue(loudest(out) <= 2);
        assertTrue(out[15] < 0.2f);
    }

    @Test
    public void paddedCapturesSpreadTheBandsOverWhatTheyHold() {
        // Captured at 12 kHz, the top band ends below 6 kHz, so a 5 kHz tone still reaches it.
        float[] out = new float[Levels.BANDS];
        FftBands.read(padded(5000, 48000), 48000, out);
        assertTrue(loudest(out) >= 14);
    }

    @Test
    public void aRealCaptureIsNotTakenForPadding() {
        assertEquals(1, FftBands.padding(tone(440, 0.5, RATE)));
    }

    @Test
    public void edgesRiseEvenlyInPitch() {
        assertEquals(FftBands.LOW, FftBands.edge(0, 16, FftBands.HIGH), 0.01f);
        assertEquals(FftBands.HIGH, FftBands.edge(16, 16, FftBands.HIGH), 1f);
        float r1 = FftBands.edge(1, 16, 9000) / FftBands.edge(0, 16, 9000), r2 = FftBands.edge(9, 16, 9000) / FftBands.edge(8, 16, 9000);
        assertEquals(r1, r2, 0.001f);
    }
}
