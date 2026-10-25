package com.maxhayim.retrodeck.core;

/** Where the visualizer gets its 16 band levels (0..1). */
public interface Levels {

    int BANDS = 16;

    /** Fills out with the current levels. */
    void read(float[] out, long nowMs, boolean playing);

    /**
     * Moving levels that follow no audio. Used when the app has no permission to read the output mix,
     * and in previews.
     */
    final class Simulated implements Levels {
        @Override
        public void read(float[] out, long now, boolean playing) {
            double t = now / 1000.0;
            for (int i = 0; i < out.length; i++) {
                if (!playing) {
                    out[i] = Math.max(0f, out[i] * 0.85f);
                    continue;
                }
                double base = 0.75 - i * 0.03;
                double v = base * (0.55 + 0.25 * Math.sin(t * (2.1 + i * 0.37) + i)
                        + 0.2 * Math.sin(t * (5.3 + i * 0.11) + i * 1.7));
                v *= 0.85 + 0.15 * Math.sin(t * 8.0);
                out[i] = (float) Math.max(0.03, Math.min(1.0, v));
            }
        }
    }
}
