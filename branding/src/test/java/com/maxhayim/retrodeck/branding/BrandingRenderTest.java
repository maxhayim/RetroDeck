package com.maxhayim.retrodeck.branding;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import com.maxhayim.retrodeck.core.Illumination;
import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.StereoView;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

/**
 * Draws each skin branded with the made-up demo vehicle in docs/demo, through the real importer and
 * renderer, and writes PNGs to build/branding-renders. Only runs with -Prender.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BrandingRenderTest {

    @Test
    public void renderBrandedSkins() throws Exception {
        Assume.assumeTrue(System.getProperty("render") != null);
        String size = System.getProperty("render.size", "1024x600");
        int w = Integer.parseInt(size.split("x")[0]), h = Integer.parseInt(size.split("x")[1]);
        File demo = new File(System.getProperty("demo.dir"));
        Context ctx = RuntimeEnvironment.getApplication();

        ProfileStore store = ProfileStore.get(ctx);
        VehicleProfile p = store.newProfile();
        p.manufacturer = "Halcyon";
        p.model = "Mesa GT";
        p.year = "1989";
        String[] files = {"halcyon-logo.svg", "mesa-gt-badge.png"};
        for (int role = 0; role < 2; role++) {
            File dest = store.newLogoFile(p.id, role);
            LogoImporter.Result r = LogoImporter.fromBytes(ctx, Files.readAllBytes(new File(demo, files[role]).toPath()), dest);
            p.logoFile[role] = dest.getName();
            p.logoColor[role] = r.color;
        }
        p.illumination = Illumination.AUTO;
        store.add(p, true);

        File dir = new File("build/branding-renders");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        for (Skin skin : Skins.all()) {
            p.skin = skin.id;
            StereoView v = new StereoView(ctx);
            v.setOverlay(new BrandingRenderer(store, p));
            Previews.apply(v, p);
            v.setState(Previews.state());
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, w, h);
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(bm));
            try (FileOutputStream out = new FileOutputStream(new File(dir, skin.id + "-" + size + ".png"))) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
    }
}
