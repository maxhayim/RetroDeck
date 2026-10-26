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
        for (Skin skin : Skins.all()) for (String keys : new String[]{KeyLights.ON, KeyLights.OFF}) {
            StereoView v = new StereoView(RuntimeEnvironment.getApplication());
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
            try (FileOutputStream out = new FileOutputStream(new File(dir, skin.id + "-" + size + (KeyLights.ON.equals(keys) ? "" : "-day") + ".png"))) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
    }
}
