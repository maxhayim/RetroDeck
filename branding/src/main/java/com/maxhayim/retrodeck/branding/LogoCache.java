package com.maxhayim.retrodeck.branding;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.LruCache;

import java.io.File;

/**
 * Logos with their crop, rotation and colour edits baked in, kept in memory so drawing a frame is just
 * one bitmap blit per logo. Sized to an eighth of the app's memory, so low-memory head units keep
 * fewer versions instead of running out.
 */
public final class LogoCache {

    private static LogoCache instance;
    private final LruCache<String, Bitmap> cache;

    public static synchronized LogoCache get() {
        if (instance == null) instance = new LogoCache();
        return instance;
    }

    private LogoCache() {
        int kb = (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(Math.max(4096, kb)) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
    }

    /** The edited logo, or null when the file is missing or can't be decoded. */
    public Bitmap get(File file, LogoAdjust a) {
        if (file == null || !file.exists()) return null;
        String key = file.getPath() + "@" + file.lastModified() + "#" + a.signature();
        Bitmap bm = cache.get(key);
        if (bm != null && !bm.isRecycled()) return bm;
        bm = render(file, a);
        if (bm != null) cache.put(key, bm);
        return bm;
    }

    /** A logo's soft shadow, made from its edited version. */
    public static final class Shadow {
        public final Bitmap alpha;
        /** Where the shadow's top left sits relative to the logo's, in logo pixels. */
        public final int dx, dy;

        Shadow(Bitmap alpha, int dx, int dy) {
            this.alpha = alpha;
            this.dx = dx;
            this.dy = dy;
        }
    }

    private final LruCache<String, Shadow> shadows = new LruCache<>(8);

    /** The blurred outline of the edited logo, so a badge can stand off the faceplate; null if unavailable. */
    public Shadow shadow(File file, LogoAdjust a) {
        Bitmap bm = get(file, a);
        if (bm == null) return null;
        String key = file.getPath() + "@" + file.lastModified() + "#" + a.signature();
        Shadow s = shadows.get(key);
        if (s != null && !s.alpha.isRecycled()) return s;
        try {
            Paint blur = new Paint();
            blur.setMaskFilter(new BlurMaskFilter(Math.max(2, Math.min(bm.getWidth(), bm.getHeight()) * 0.05f),
                    BlurMaskFilter.Blur.NORMAL));
            int[] offset = new int[2];
            Bitmap alpha = bm.extractAlpha(blur, offset);
            s = new Shadow(alpha, offset[0], offset[1]);
            shadows.put(key, s);
            return s;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** The optimised original, without edits; used by the logo editor's crop view. */
    public Bitmap original(File file) {
        return get(file, new LogoAdjust());
    }

    public void clear() {
        cache.evictAll();
        shadows.evictAll();
    }

    private static Bitmap render(File file, LogoAdjust a) {
        Bitmap src;
        try {
            src = BitmapFactory.decodeFile(file.getPath());
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (src == null) return null;
        LogoAdjust s = a.copy().sanitize();
        boolean edited = s.isCropped() || Math.abs(s.rotation) > 0.05f || !ColorMath.isIdentity(s.brightness, s.tintStrength);
        if (!edited) return src;
        try {
            Rect crop = new Rect(Math.round(s.cropLeft * src.getWidth()), Math.round(s.cropTop * src.getHeight()),
                    Math.round(s.cropRight * src.getWidth()), Math.round(s.cropBottom * src.getHeight()));
            if (crop.width() < 1 || crop.height() < 1) crop.set(0, 0, src.getWidth(), src.getHeight());
            float[] size = PlacementMath.rotatedSize(crop.width(), crop.height(), s.rotation);
            int w = Math.max(1, Math.round(size[0])), h = Math.max(1, Math.round(size[1]));
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            if (!ColorMath.isIdentity(s.brightness, s.tintStrength)) {
                p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(ColorMath.matrix(s.brightness, s.tint, s.tintStrength))));
            }
            Matrix m = new Matrix();
            m.postTranslate(-crop.left - crop.width() / 2f, -crop.top - crop.height() / 2f);
            m.postRotate(s.rotation);
            m.postTranslate(w / 2f, h / 2f);
            c.save();
            c.concat(m);
            c.clipRect(crop);
            c.drawBitmap(src, 0, 0, p);
            c.restore();
            src.recycle();
            return out;
        } catch (OutOfMemoryError e) {
            return src;
        }
    }
}
