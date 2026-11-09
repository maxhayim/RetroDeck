package com.maxhayim.retrodeck.branding;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import com.maxhayim.retrodeck.core.KeyLights;
import com.maxhayim.retrodeck.core.Levels;
import com.maxhayim.retrodeck.core.StereoState;
import com.maxhayim.retrodeck.core.StereoView;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The screenshots a shared pack carries, so whoever gets it sees it before importing: the stereo by
 * day and by night, the full-screen visualizer by day and by night, and the startup screen. Drawn with
 * the same views as the head unit. Call on the main thread.
 */
public final class PackScreenshots {

    /** Screenshot names, in the order they are shown. "day" is the main one. */
    public static final String[] NAMES = {"day", "night", "visualizer-day", "visualizer-night", "startup"};
    /** Used instead of visualizer-day and visualizer-night when the two look the same. */
    public static final String VISUALIZER = "visualizer";

    /** Simulated bars as they stand 1.6 s into a song. */
    private static final Levels FROZEN = new Levels() {
        private final Levels.Simulated sim = new Levels.Simulated();

        @Override
        public void read(float[] out, long nowMs, boolean playing) {
            sim.read(out, 1600, true);
        }
    };

    private PackScreenshots() {}

    /**
     * Every screenshot of p at w x h, as PNG files. song gives the title and artist shown playing; null
     * or blank uses the preview song. Each is compressed as soon as it is drawn, to keep memory low.
     */
    public static Map<String, byte[]> draw(Context c, ProfileStore store, VehicleProfile p, StereoState song, int w, int h) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        StereoState st = Previews.state();
        if (song != null && song.title != null && !song.title.trim().isEmpty() && !"NO MEDIA".equals(song.title)) {
            st.title = song.title;
            st.artist = song.artist == null ? "" : song.artist;
        }
        for (String name : NAMES) {
            if (name.equals("startup")) continue;
            StereoView v = new StereoView(c);
            v.setOverlay(new BrandingRenderer(store, p));
            Previews.apply(v, p);
            v.setKeyLighting(name.endsWith("night") ? KeyLights.ON : KeyLights.OFF);
            v.setFullscreen(name.startsWith("visualizer"));
            v.setState(st);
            // The same bars in every picture, so day and night differ only by the lights.
            v.setLevels(FROZEN);
            // A title too long for the display shows its end, where a song's original-script name usually is.
            v.setScrollToEnd(true);
            out.put(name, draw(v, w, h, 0));
        }
        // Most displays look the same full screen by day and night: then there's one picture, not two copies.
        if (java.util.Arrays.equals(out.get("visualizer-day"), out.get("visualizer-night"))) {
            byte[] one = out.remove("visualizer-day");
            out.remove("visualizer-night");
            Map<String, byte[]> ordered = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> e : out.entrySet()) {
                ordered.put(e.getKey(), e.getValue());
                if (e.getKey().equals("night")) ordered.put(VISUALIZER, one);
            }
            out = ordered;
        }
        StartupView s = new StartupView(c, store);
        s.showStill(p);
        out.put("startup", draw(s, w, h, 0xFF000000));
        return out;
    }

    private static byte[] draw(View v, int w, int h, int background) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        if (background != 0) canvas.drawColor(background);
        v.draw(canvas);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, b);
        bm.recycle();
        return b.toByteArray();
    }
}
