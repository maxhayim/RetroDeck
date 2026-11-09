package com.maxhayim.retrodeck.branding;

import android.content.Context;

import com.maxhayim.retrodeck.core.StereoState;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Previews a branding pack on a computer: imports it through the same checks and importer as the
 * head unit, then draws each of its profiles the way an exported pack's screenshots are drawn (stereo
 * by day and night, full-screen visualizer by day and night, startup) into
 * build/branding-renders/pack-INDEX-SKIN-NAME.png.
 * Only runs when given a pack: ./gradlew :branding:testDebugUnitTest -Ppack=path/to/pack.zip
 * (optionally -Prender.size=1920x1080, -Ppack.title=..., -Ppack.artist=...).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PackRenderTest {

    @Test
    public void renderPack() throws Exception {
        String path = System.getProperty("pack");
        Assume.assumeTrue(path != null);
        String size = System.getProperty("render.size", "1920x1080");
        int w = Integer.parseInt(size.split("x")[0]), h = Integer.parseInt(size.split("x")[1]);
        Context ctx = RuntimeEnvironment.getApplication();
        ProfileStore store = ProfileStore.get(ctx);

        PackIO.ImportResult result;
        try (InputStream in = new FileInputStream(path)) {
            result = PackIO.importPack(ctx, store, in);
        }
        StereoState song = new StereoState();
        song.title = System.getProperty("pack.title");
        song.artist = System.getProperty("pack.artist", "");

        File dir = new File("build/branding-renders");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        int i = 0;
        for (VehicleProfile p : result.added) {
            Map<String, byte[]> shots = PackScreenshots.draw(ctx, store, p, song, w, h);
            for (Map.Entry<String, byte[]> e : shots.entrySet()) {
                File out = new File(dir, "pack-" + i + "-" + p.skin + "-" + e.getKey() + ".png");
                try (FileOutputStream o = new FileOutputStream(out)) {
                    o.write(e.getValue());
                }
                System.out.println("Rendered " + out.getAbsolutePath());
            }
            i++;
        }
    }
}
