package com.maxhayim.retrodeck.branding;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exports profiles to branding packs and imports them back. Runs off the main thread. */
final class PackIO {

    static final class ImportResult {
        final List<VehicleProfile> added = new ArrayList<>();
        String packName;
    }

    private PackIO() {}

    static void export(ProfileStore store, List<VehicleProfile> profiles, OutputStream out, String createdWith) throws IOException {
        Map<VehicleProfile, byte[][]> logos = new HashMap<>();
        Map<VehicleProfile, byte[]> animations = new HashMap<>();
        for (VehicleProfile p : profiles) {
            File anim = store.animation(p);
            if (anim != null && anim.exists()) animations.put(p, read(anim));
            byte[][] b = new byte[2][];
            for (int r = 0; r < 2; r++) {
                File f = store.logo(p, r);
                if (f != null && f.exists()) b[r] = read(f);
            }
            logos.put(p, b);
        }
        String name = profiles.size() == 1 ? profiles.get(0).displayName() : profiles.size() + " vehicles";
        BrandingPack.write(out, name, profiles, logos, animations, createdWith);
    }

    /**
     * Imports every profile in a pack. Each logo goes through the same decoder as a picked image, so
     * what is stored is always a fresh PNG made by RetroDeck, never the file from the pack.
     */
    static ImportResult importPack(Context c, ProfileStore store, InputStream in)
            throws IOException, BrandingPack.InvalidPackException, LogoImporter.ImportException {
        BrandingPack.Pack pack = BrandingPack.read(in);
        // Decode everything first, so a bad logo leaves no half-imported profiles behind.
        List<VehicleProfile> ready = new ArrayList<>();
        List<File> written = new ArrayList<>();
        try {
            for (BrandingPack.Entry e : pack.entries) {
                VehicleProfile p = e.profile;
                p.id = store.newProfile().id;
                for (int r = 0; r < 2; r++) {
                    p.logoFile[r] = null;
                    p.logoColor[r] = 0;
                    if (e.logos[r] == null) continue;
                    File dest = store.newLogoFile(p.id, r);
                    LogoImporter.Result res = LogoImporter.fromBytes(c, e.logos[r], dest);
                    written.add(dest);
                    p.logoFile[r] = dest.getName();
                    p.logoColor[r] = res.color;
                }
                if (e.animation != null) {
                    // Already checked by BrandingPack.read; GIFs are kept as they are, frames and all.
                    File dest = store.newAnimationFile(p.id);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                        out.write(e.animation);
                    }
                    written.add(dest);
                    p.animationFile = dest.getName();
                }
                ready.add(p);
            }
        } catch (LogoImporter.ImportException | IOException ex) {
            for (File f : written) store.discard(f);
            throw ex;
        }
        ImportResult result = new ImportResult();
        result.packName = pack.name;
        for (int i = 0; i < ready.size(); i++) {
            store.add(ready.get(i), i == 0);
            result.added.add(ready.get(i));
        }
        return result;
    }

    private static byte[] read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
