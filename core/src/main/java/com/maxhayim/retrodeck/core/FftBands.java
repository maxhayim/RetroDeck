package com.maxhayim.retrodeck.core;

/**
 * Turns one capture of the music's waveform into the visualizer's band levels: 16 bands spaced
 * evenly in pitch from the bass to the treble, as a graphic equaliser's are, each 0..1.
 *
 * It works from Android's waveform rather than its ready-made FFT because some devices capture at
 * a fraction of the rate they report and pad the gaps with silent samples: their FFT then shows the
 * bass again high in the treble. Here the padding is found and dropped, and the bands are spread
 * over whatever range the capture really holds.
 */
public final class FftBands {

    /** The lowest and highest band edges, in Hz, when the capture reaches that high. */
    static final float LOW = 50f, HIGH = 14000f;
    /** Band levels in dB of full scale: RANGE below full scale is silence, full scale is a full bar. */
    static final float RANGE = 34f;
    /** An 8-bit sample of silence. */
    private static final int SILENT = 128;

    private FftBands() {}

    /** wave is Visualizer.getWaveForm's output, unsigned 8-bit; rateHz is the rate the capture reports. */
    public static void read(byte[] wave, int rateHz, float[] out) {
        int step = padding(wave);
        int n = Integer.highestOneBit(wave.length / step);
        if (n < 16 || rateHz <= 0) {
            java.util.Arrays.fill(out, 0f);
            return;
        }
        int offset = offset(wave, step);
        float rate = rateHz / (float) step;
        // Hann-windowed samples, -1..1.
        double[] re = new double[n], im = new double[n];
        for (int i = 0; i < n; i++) {
            double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
            re[i] = w * ((wave[offset + i * step] & 0xFF) - SILENT) / 128.0;
        }
        fft(re, im);
        float binHz = rate / n;
        float high = Math.min(HIGH, rate * 0.45f);
        for (int b = 0; b < out.length; b++) {
            float f0 = edge(b, out.length, high), f1 = edge(b + 1, out.length, high);
            int k0 = Math.max(1, (int) Math.floor(f0 / binHz));
            int k1 = Math.min(n / 2 - 1, Math.max(k0, (int) Math.ceil(f1 / binHz) - 1));
            double peak = 0;
            for (int k = k0; k <= k1; k++) peak = Math.max(peak, Math.hypot(re[k], im[k]));
            // A full-scale sine through the Hann window peaks at n / 4.
            double amp = peak / (n / 4.0);
            float db = amp <= 0 ? -RANGE : (float) (20 * Math.log10(amp));
            out[b] = Math.max(0f, Math.min(1f, (db + RANGE) / RANGE));
        }
    }

    /** 1, or the 2, 4 or 8 a device pads its capture by, with silent samples between the real ones. */
    static int padding(byte[] wave) {
        for (int step = 8; step >= 2; step /= 2) {
            int[] heard = new int[step];
            for (int i = 0; i < wave.length; i++) if ((wave[i] & 0xFF) != SILENT) heard[i % step]++;
            int lanes = 0;
            for (int h : heard) if (h > 0) lanes++;
            // Padded: exactly one sample in each step carries sound, and it carries plenty of it.
            if (lanes == 1) {
                for (int h : heard) if (h > wave.length / step / 4) return step;
            }
        }
        return 1;
    }

    /** Which sample in each step carries the sound. */
    private static int offset(byte[] wave, int step) {
        for (int i = 0; i < wave.length; i++) if ((wave[i] & 0xFF) != SILENT) return i % step;
        return 0;
    }

    /** Band i's lower edge in Hz; band count's edge is high. */
    static float edge(int i, int count, float high) {
        return (float) (LOW * Math.pow(high / LOW, i / (double) count));
    }

    /** In place, radix 2; re.length is a power of two. */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double a = -2 * Math.PI / len, wr = Math.cos(a), wi = Math.sin(a);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int p = i + k, q = p + len / 2;
                    double tr = re[q] * cr - im[q] * ci, ti = re[q] * ci + im[q] * cr;
                    re[q] = re[p] - tr; im[q] = im[p] - ti;
                    re[p] += tr; im[p] += ti;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }
}
