package com.maxhayim.retrodeck;

import android.media.audiofx.Visualizer;
import android.os.SystemClock;

import com.maxhayim.retrodeck.core.FftBands;
import com.maxhayim.retrodeck.core.Levels;

/**
 * The visualizer's levels from the music that's playing, read from RetroDeck's own player with
 * Android's Visualizer. Without permission to read it, or on a device whose Visualizer gives
 * nothing, the bars move on their own as before.
 */
final class LiveLevels implements Levels {

    /** How long a new player may stay silent before this device's Visualizer is taken to give nothing. */
    private static final long SILENT_MS = 2500;

    private final Levels fallback = new Levels.Simulated();
    private final float[] live = new float[BANDS];
    private Visualizer visualizer;
    private byte[] wave;
    private int session, rateHz;
    private long attachedAt, heardAt;

    /** The audio session followed, or 0. */
    int session() {
        return session;
    }

    /** Follows this player's audio session, or none for 0. False if it can't (no permission, no Visualizer). */
    boolean attach(int audioSession) {
        release();
        session = audioSession;
        if (audioSession == 0) return false;
        try {
            Visualizer v = new Visualizer(audioSession);
            int[] range = Visualizer.getCaptureSizeRange();
            v.setCaptureSize(Math.max(range[0], Math.min(1024, range[1])));
            v.setEnabled(true);
            visualizer = v;
            wave = new byte[v.getCaptureSize()];
            rateHz = v.getSamplingRate() / 1000;
            attachedAt = SystemClock.uptimeMillis();
            heardAt = 0;
            return true;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            release();
            session = audioSession;
            return false;
        }
    }

    void release() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (RuntimeException ignored) {
                // Already gone with its player.
            }
            visualizer = null;
        }
        session = 0;
    }

    @Override
    public void read(float[] out, long now, boolean playing) {
        if (visualizer == null || (heardAt == 0 && SystemClock.uptimeMillis() - attachedAt > SILENT_MS)) {
            fallback.read(out, now, playing);
            return;
        }
        boolean got = false;
        if (playing) {
            try {
                got = visualizer.getWaveForm(wave) == Visualizer.SUCCESS;
            } catch (IllegalStateException ignored) {
                // Its player was released; the next attach replaces it.
            }
        }
        if (got) {
            FftBands.read(wave, rateHz, live);
            for (float v : live) {
                if (v > 0.02f) {
                    heardAt = SystemClock.uptimeMillis();
                    break;
                }
            }
        } else {
            java.util.Arrays.fill(live, 0f);
        }
        // Bars jump up with the music and fall back smoothly, as a meter's needle does.
        for (int i = 0; i < out.length; i++) out[i] = live[i] >= out[i] ? live[i] : Math.max(live[i], out[i] * 0.82f);
    }
}
