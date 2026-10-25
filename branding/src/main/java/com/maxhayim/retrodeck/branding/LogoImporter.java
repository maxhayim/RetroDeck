package com.maxhayim.retrodeck.branding;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import com.caverock.androidsvg.RenderOptions;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Turns a picked image into an optimised logo: decoded at a reduced size (never the full resolution of
 * a big photo), turned upright from its EXIF data, stripped of empty transparent margins, and saved as
 * PNG in the profile's folder. Large files are streamed to a temporary file first rather than read into
 * memory. Runs off the main thread.
 */
public final class LogoImporter {

    /** Largest file accepted from the device. */
    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    /** SVG text above this is refused; logos are small. */
    public static final int MAX_SVG_BYTES = 2 * 1024 * 1024;

    public static final class Result {
        public int width, height;
        public String format;
        /** Colour found by ColorExtractor, or 0. */
        public int color;
    }

    public static final class ImportException extends Exception {
        public ImportException(String message) {
            super(message);
        }
    }

    private LogoImporter() {}

    /**
     * The longest side kept for a logo: 1024 px, or 512 px on low-memory head units. Logos are drawn at
     * most about a third of the screen wide, so this is plenty for 1024x600 and 1920x1080.
     */
    public static int maxDimension(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        boolean low = am != null && (am.isLowRamDevice() || am.getMemoryClass() <= 64);
        return low ? 512 : 1024;
    }

    /** Imports from a content or file URI. */
    public static Result fromUri(Context c, Uri uri, File dest) throws ImportException {
        File tmp = new File(c.getCacheDir(), "import-" + System.nanoTime());
        try {
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new ImportException("Couldn't open that file.");
                copyCapped(in, tmp);
            } catch (SecurityException e) {
                throw new ImportException("RetroDeck wasn't given access to that file.");
            } catch (IOException e) {
                throw new ImportException("Couldn't read that file.");
            }
            return fromFile(c, tmp, dest);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** Imports image bytes, for branding packs. */
    public static Result fromBytes(Context c, byte[] data, File dest) throws ImportException {
        File tmp = new File(c.getCacheDir(), "import-" + System.nanoTime());
        try {
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
            } catch (IOException e) {
                throw new ImportException("Couldn't unpack a logo.");
            }
            return fromFile(c, tmp, dest);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static Result fromFile(Context c, File src, File dest) throws ImportException {
        byte[] head = new byte[64 * 1024];
        int n;
        try (InputStream in = new FileInputStream(src)) {
            n = readFully(in, head);
        } catch (IOException e) {
            throw new ImportException("Couldn't read that file.");
        }
        String format = ImageFormat.sniff(head, n);
        if (format == null) throw new ImportException("That isn't a PNG, JPEG, WebP or SVG image.");
        int max = maxDimension(c);
        Bitmap bm;
        if (ImageFormat.SVG.equals(format)) {
            bm = renderSvg(src, max);
        } else {
            bm = decode(src, format, max);
        }
        try {
            bm = trim(bm);
            Result r = new Result();
            r.format = format;
            r.width = bm.getWidth();
            r.height = bm.getHeight();
            r.color = color(bm);
            save(bm, dest);
            return r;
        } finally {
            bm.recycle();
        }
    }

    private static Bitmap decode(File src, String format, int max) throws ImportException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(src.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new ImportException(unreadable(format));
        if ((long) o.outWidth * o.outHeight > 100_000_000L) throw new ImportException("That image is too large.");
        o.inJustDecodeBounds = false;
        o.inSampleSize = ImageFormat.sampleSize(o.outWidth, o.outHeight, max);
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm;
        try {
            bm = BitmapFactory.decodeFile(src.getPath(), o);
        } catch (OutOfMemoryError e) {
            throw new ImportException("Not enough memory to open that image. Try a smaller one.");
        }
        if (bm == null) throw new ImportException(unreadable(format));
        int rotate = ImageFormat.JPEG.equals(format) ? exifRotation(src) : 0;
        return scale(bm, max, rotate);
    }

    private static String unreadable(String format) {
        if (ImageFormat.WEBP.equals(format)) {
            // Animated and some extended WebP files only decode on newer Android versions.
            return "This device can't read this WebP image. Save it as PNG and import that instead.";
        }
        return "That image is damaged or in a form this device can't read.";
    }

    private static int exifRotation(File f) {
        try {
            int o = new ExifInterface(f.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }

    /** Scales so the longest side is at most max, and rotates, replacing the bitmap if needed. */
    private static Bitmap scale(Bitmap bm, int max, int rotate) {
        int[] size = ImageFormat.fit(bm.getWidth(), bm.getHeight(), max);
        if (size[0] == bm.getWidth() && size[1] == bm.getHeight() && rotate == 0) return bm;
        Matrix m = new Matrix();
        m.postScale(size[0] / (float) bm.getWidth(), size[1] / (float) bm.getHeight());
        if (rotate != 0) m.postRotate(rotate);
        Bitmap out = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        if (out != bm) bm.recycle();
        return out;
    }

    private static Bitmap renderSvg(File src, int max) throws ImportException {
        if (src.length() > MAX_SVG_BYTES) throw new ImportException("That SVG file is too large for a logo.");
        String text;
        try (InputStream in = new FileInputStream(src)) {
            byte[] b = new byte[(int) src.length()];
            text = new String(b, 0, readFully(in, b), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ImportException("Couldn't read that file.");
        }
        String problem = ImageFormat.svgProblem(text);
        if (problem != null) throw new ImportException(problem + ".");
        SVG svg;
        try {
            svg = SVG.getFromInputStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        } catch (SVGParseException e) {
            throw new ImportException("That SVG file couldn't be read.");
        }
        float w = svg.getDocumentWidth(), h = svg.getDocumentHeight();
        android.graphics.RectF vb = svg.getDocumentViewBox();
        if ((w <= 0 || h <= 0) && vb != null) {
            w = vb.width();
            h = vb.height();
        }
        if (w <= 0 || h <= 0) {
            w = 512;
            h = 512;
        }
        // Vector art: render at the full size we keep, whatever the document says.
        float f = max / Math.max(w, h);
        int bw = Math.max(1, Math.round(w * f)), bh = Math.max(1, Math.round(h * f));
        if (vb == null) svg.setDocumentViewBox(0, 0, w, h);
        Bitmap bm;
        try {
            bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            throw new ImportException("Not enough memory to open that image.");
        }
        svg.renderToCanvas(new Canvas(bm), new RenderOptions().viewPort(0, 0, bw, bh));
        return bm;
    }

    /** Drops fully transparent margins, so placement sizes the visible logo, not empty space. */
    private static Bitmap trim(Bitmap bm) throws ImportException {
        int w = bm.getWidth(), h = bm.getHeight();
        int[] px = new int[w * h];
        bm.getPixels(px, 0, w, 0, 0, w, h);
        int[] b = ImageFormat.opaqueBounds(px, w, h);
        if (b == null) throw new ImportException("That image is completely transparent.");
        if (b[0] == 0 && b[1] == 0 && b[2] == w && b[3] == h) return bm;
        Bitmap out = Bitmap.createBitmap(bm, b[0], b[1], b[2] - b[0], b[3] - b[1]);
        if (out != bm) bm.recycle();
        return out;
    }

    private static int color(Bitmap bm) {
        Bitmap small = Bitmap.createScaledBitmap(bm, Math.min(64, bm.getWidth()), Math.min(64, bm.getHeight()), true);
        int[] px = new int[small.getWidth() * small.getHeight()];
        small.getPixels(px, 0, small.getWidth(), 0, 0, small.getWidth(), small.getHeight());
        if (small != bm) small.recycle();
        return ColorExtractor.dominant(px);
    }

    private static void save(Bitmap bm, File dest) throws ImportException {
        File parent = dest.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        File tmp = new File(dest.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            if (!bm.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("compress failed");
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new ImportException("Couldn't save the logo. Is the device storage full?");
        }
        if (!tmp.renameTo(dest)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new ImportException("Couldn't save the logo.");
        }
    }

    private static void copyCapped(InputStream in, File to) throws IOException, ImportException {
        long total = 0;
        try (OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_FILE_BYTES) throw new ImportException("That file is larger than 25 MB.");
                out.write(buf, 0, n);
            }
        }
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0, n;
        while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
        return off;
    }
}
