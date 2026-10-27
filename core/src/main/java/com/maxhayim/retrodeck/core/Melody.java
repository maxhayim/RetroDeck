package com.maxhayim.retrodeck.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Station-platform chimes, like the short melodies Japanese stations play as a train leaves. These
 * are RetroDeck's own tunes, not copies of real station melodies, played on a synthesized bell.
 * A tune is written as notes ("E5", "G#5", "-" for a rest), each optionally ":beats".
 */
public final class Melody {

    public static final int RATE = 22050;

    public final String id, name;
    final int bpm;
    final String notes;

    private Melody(String id, String name, int bpm, String notes) {
        this.id = id;
        this.name = name;
        this.bpm = bpm;
        this.notes = notes;
    }

    private static final Map<String, Melody> ALL = new LinkedHashMap<>();

    static {
        add(new Melody("departure", "Departure", 150, "C5 E5 G5 C6 B5 G5 E5 G5 A5 F5 D5 F5 G5:2 C6:4"));
        add(new Melody("morning", "Morning Line", 120, "E5 G5 A5:2 G5 E5 D5:2 E5 G5 C6:2 B5 A5 G5:4"));
        add(new Melody("harbour", "Harbour", 132, "G5:2 C6 B5:2 G5 A5:2 F5 E5:3 D5:2 G5 C6:3"));
        add(new Melody("last_train", "Last Train", 112, "C6 A5 F5:2 G5 E5 C5:2 D5 F5 E5:2 C5:4"));
    }

    private static void add(Melody m) {
        ALL.put(m.id, m);
    }

    public static List<Melody> all() {
        return new ArrayList<>(ALL.values());
    }

    /** A built-in melody, or null. */
    public static Melody get(String id) {
        return id == null ? null : ALL.get(id);
    }

    /** A note name such as "G#5" as a frequency in Hz; 0 for a rest or anything unreadable. */
    static double frequency(String note) {
        if (note == null || note.isEmpty() || note.equals("-")) return 0;
        int i = "C D EF G A B".indexOf(Character.toUpperCase(note.charAt(0)));
        if (i < 0) return 0;
        int p = 1;
        if (p < note.length() && note.charAt(p) == '#') { i++; p++; } else if (p < note.length() && note.charAt(p) == 'b') { i--; p++; }
        try {
            int octave = Integer.parseInt(note.substring(p));
            int midi = (octave + 1) * 12 + i;
            return 440 * Math.pow(2, (midi - 69) / 12.0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 16-bit mono samples at {@link #RATE}, with the last bell left to ring out. */
    public short[] render() {
        double beat = 60.0 / bpm;
        List<double[]> hits = new ArrayList<>(); // {start s, frequency}
        double t = 0;
        for (String token : notes.trim().split("\\s+")) {
            String[] nb = token.split(":");
            double beats = 1;
            if (nb.length > 1) {
                try {
                    beats = Double.parseDouble(nb[1]);
                } catch (NumberFormatException ignored) {
                    // One beat.
                }
            }
            double f = frequency(nb[0]);
            if (f > 0) hits.add(new double[]{t, f});
            t += beats * beat;
        }
        double ring = 1.6;
        int n = (int) ((t + ring) * RATE);
        double[] mix = new double[n];
        // A bell-like tone: a few inharmonic partials, the higher ones dying away faster.
        double[] ratio = {1, 2.0, 3.01, 4.2}, level = {1, 0.45, 0.2, 0.12}, decay = {2.2, 3.5, 5, 7};
        for (double[] h : hits) {
            int start = (int) (h[0] * RATE);
            int len = Math.min(n - start, (int) (ring * RATE));
            for (int k = 0; k < len; k++) {
                double s = k / (double) RATE;
                double v = 0;
                for (int p = 0; p < ratio.length; p++) {
                    v += level[p] * Math.exp(-decay[p] * s) * Math.sin(2 * Math.PI * h[1] * ratio[p] * s);
                }
                // A soft strike rather than a click.
                v *= Math.min(1, s / 0.004);
                mix[start + k] += v;
            }
        }
        double peak = 0;
        for (double v : mix) peak = Math.max(peak, Math.abs(v));
        double gain = peak > 0 ? 0.7 / peak : 0;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) Math.round(mix[i] * gain * Short.MAX_VALUE);
        return out;
    }

    /** How long the tune runs, without the ring-out, in milliseconds. */
    public long lengthMs() {
        double beats = 0;
        for (String token : notes.trim().split("\\s+")) {
            String[] nb = token.split(":");
            beats += nb.length > 1 ? Double.parseDouble(nb[1]) : 1;
        }
        return Math.round(beats * 60000 / bpm);
    }
}
