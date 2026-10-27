package com.maxhayim.retrodeck.branding;

import android.content.Context;
import android.net.Uri;

import com.maxhayim.retrodeck.core.Gif;
import com.maxhayim.retrodeck.core.GifPlayer;
import com.maxhayim.retrodeck.core.StereoView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.WeakHashMap;

/** Visualizer animations: GIFs brought in from a file or GIPHY, and handed to the stereo. */
public final class Animations {

    /** Which file each stereo view is playing, so it's only read again when it changes. */
    private static final Map<StereoView, String> playing = new WeakHashMap<>();

    private Animations() {}

    /** Gives a stereo view the profile's animation and how to show it. */
    public static void apply(StereoView v, ProfileStore store, VehicleProfile p) {
        File f = store.animation(p);
        String path = f == null ? null : f.getPath();
        String was = playing.get(v);
        if (path != null && path.equals(was)) {
            v.setAnimation(v.getGifPlayer(), p.animationTint, p.animationFull);
            return;
        }
        GifPlayer player = null;
        if (f != null) {
            try {
                player = new GifPlayer(Gif.parse(read(new FileInputStream(f))));
            } catch (IOException | Gif.FormatException | OutOfMemoryError e) {
                path = null;
            }
        }
        if (v.getGifPlayer() != null) v.getGifPlayer().release();
        playing.put(v, path);
        v.setAnimation(player, p.animationTint, p.animationFull);
    }

    /** A GIF that couldn't be used. The message is shown to the user. */
    public static final class ImportException extends Exception {
        ImportException(String message) {
            super(message);
        }
    }

    /** Checks a GIF from a file and copies it to dest. */
    static void fromUri(Context c, Uri uri, File dest) throws ImportException {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new ImportException("Couldn't open that file.");
            save(read(in), dest);
        } catch (IOException e) {
            throw new ImportException("Couldn't read that file.");
        }
    }

    /** Checks GIF bytes and writes them to dest. */
    static void save(byte[] data, File dest) throws ImportException {
        try {
            Gif.parse(data);
        } catch (Gif.FormatException e) {
            throw new ImportException(e.getMessage());
        }
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(data);
        } catch (IOException e) {
            throw new ImportException("Couldn't save the animation.");
        }
    }

    /** Reads at most Gif.MAX_BYTES (plus one, so a bigger file is noticed and refused). */
    static byte[] read(InputStream in) throws IOException {
        try (InputStream src = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = src.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > Gif.MAX_BYTES) break;
            }
            return out.toByteArray();
        }
    }
}
