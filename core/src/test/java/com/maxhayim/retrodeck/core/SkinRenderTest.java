package com.maxhayim.retrodeck.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Draws every skin with Android's real graphics and writes PNGs to build/skin-renders, so changes to
 * the faceplates can be looked at without a device. Set -Prender.size=1920x1080 for other sizes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SkinRenderTest {

    @Test
    public void renderSkins() throws Exception {
        // Only on request: the first run downloads Android's graphics runtime.
        Assume.assumeTrue(System.getProperty("render") != null);
        String size = System.getProperty("render.size", "1024x600");
        int w = Integer.parseInt(size.split("x")[0]), h = Integer.parseInt(size.split("x")[1]);
        File dir = new File("build/skin-renders");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        for (Skin skin : Skins.all()) {
            try (java.io.Writer out = new java.io.FileWriter(new File(dir, skin.id + ".json"))) {
                out.write(layout(skin));
            }
        }
        // -Prender.code draws the faceplates in code instead, as a device does when it can't load the art.
        boolean code = System.getProperty("render.code") != null;
        for (Skin skin : Skins.all()) for (String keys : new String[]{KeyLights.ON, KeyLights.OFF}) {
            StereoView v = new StereoView(RuntimeEnvironment.getApplication());
            String art = skin.art;
            if (code) skin.art = null;
            v.setSkin(skin);
            StereoState s = new StereoState();
            s.playing = true;
            s.title = "Night Drive";
            s.artist = "RetroDeck preview";
            s.track = 3;
            s.volume = 14;
            s.positionMs = 83_000;
            s.durationMs = 240_000;
            v.setState(s);
            v.setIllumination(Illumination.preset(Illumination.AMBER));
            v.setKeyLighting(keys);
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, w, h);
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(bm));
            skin.art = art;
            try (FileOutputStream out = new FileOutputStream(new File(dir, skin.id + "-" + size + (code ? "-code" : "") + (KeyLights.ON.equals(keys) ? "" : "-day") + ".png"))) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
    }

    /** Every box of a skin as JSON, for the 3D models in tools/blender, so they match the touch areas. */
    private static String layout(Skin s) {
        StringBuilder b = new StringBuilder("{\"id\":\"").append(s.id).append('"');
        b.append(",\"corner\":").append(s.corner).append(",\"lcdStyle\":").append(s.lcdStyle);
        b.append(",\"din\":").append(s.din);
        box(b, "body", s.body);
        box(b, "screen", s.screen);
        box(b, "knob", s.knob);
        box(b, "lcd", s.lcd);
        box(b, "lcdText", s.lcdText);
        box(b, "slot", s.slot);
        box(b, "changer", s.changer);
        b.append(",\"keys\":[");
        String sep = "";
        for (Skin.Control c : s.controls) { b.append(sep); key(b, c.id, c.label, c.box, c.round); sep = ","; }
        for (Skin.Control c : s.decor) { b.append(sep); key(b, null, c.label, c.box, c.round); sep = ","; }
        java.util.List<Box> presets = s.presetBoxes();
        for (int i = 0; i < presets.size(); i++) {
            b.append(sep);
            key(b, "preset" + (i + 1), s.presetLabels != null ? s.presetLabels[i] + "|" + (i + 1) : String.valueOf(i + 1), presets.get(i), false);
        }
        b.append("],\"trims\":[");
        sep = "";
        for (Skin.Control c : s.trims) { b.append(sep); key(b, null, c.label, c.box, true); sep = ","; }
        b.append("],\"zones\":{");
        sep = "";
        for (String z : Zones.ALL) {
            Box zb = s.zone(z);
            if (zb == null) continue;
            b.append(sep).append('"').append(z).append("\":").append(arr(zb));
            sep = ",";
        }
        return b.append("}}").toString();
    }

    private static void box(StringBuilder b, String name, Box x) {
        if (x != null) b.append(",\"").append(name).append("\":").append(arr(x));
    }

    private static void key(StringBuilder b, String id, String label, Box x, boolean round) {
        b.append("{\"id\":").append(id == null ? "null" : "\"" + id + "\"")
                .append(",\"label\":\"").append(label.replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
                .append(",\"box\":").append(arr(x)).append(",\"round\":").append(round).append('}');
    }

    private static String arr(Box x) {
        return "[" + x.left + "," + x.top + "," + x.right + "," + x.bottom + "]";
    }
}
