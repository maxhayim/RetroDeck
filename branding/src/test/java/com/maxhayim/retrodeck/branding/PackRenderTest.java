package com.maxhayim.retrodeck.branding;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import com.maxhayim.retrodeck.core.StereoView;

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

/**
 * Previews a branding pack on a computer: imports it through the same checks and importer as the
 * head unit, then draws each of its profiles on its skin into build/branding-renders/pack-*.png.
 * Only runs when given a pack: ./gradlew :branding:testDebugUnitTest -Ppack=path/to/pack.zip
 * (optionally -Prender.size=1920x1080).
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
        File dir = new File("build/branding-renders");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        int i = 0;
        for (VehicleProfile p : result.added) {
            StereoView v = new StereoView(ctx);
            v.setOverlay(new BrandingRenderer(store, p));
            Previews.apply(v, p);
            v.setState(Previews.state());
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, w, h);
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(bm));
            File out = new File(dir, "pack-" + (i++) + "-" + p.skin + ".png");
            try (FileOutputStream o = new FileOutputStream(out)) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, o);
            }
            System.out.println("Rendered " + out.getAbsolutePath());
        }
    }
}
