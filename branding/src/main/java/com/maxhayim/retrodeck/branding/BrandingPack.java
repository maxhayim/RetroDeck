package com.maxhayim.retrodeck.branding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Branding packs: a ZIP holding manifest.json and the logo images, for sharing vehicle profiles
 * offline. Packs are data only. Nothing in one is ever run; only manifest.json and images in assets/
 * are accepted, and every image is decoded and re-encoded as PNG before it's used.
 *
 * Layout:
 * <pre>
 * manifest.json        {"format": "retrodeck-branding-pack", "formatVersion": 1, "profiles": [...]}
 * assets/NAME.png      logos (png, jpg, jpeg, webp or svg)
 * assets/NAME.gif      visualizer animations
 * </pre>
 * Each profile is the ProfileJson object with "assets": {"manufacturer": "assets/...", "model": ...,
 * "animation": ...}. Packs with an animation are format 2, so older versions say they need an update
 * instead of refusing the GIF as an unknown file.
 */
public final class BrandingPack {

    public static final String FORMAT = "retrodeck-branding-pack";
    /** Packs with a newer major version are refused; unknown fields in this version are ignored. */
    public static final int FORMAT_VERSION = 2;
    public static final String MIME = "application/zip";

    public static final long MAX_PACK_BYTES = 24L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;
    public static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    public static final int MAX_MANIFEST_BYTES = 256 * 1024;
    public static final int MAX_ENTRIES = 40;
    public static final int MAX_PROFILES = 16;

    /** A pack that failed validation. The message is shown to the user. */
    public static final class InvalidPackException extends Exception {
        public InvalidPackException(String message) {
            super(message);
        }
    }

    /** One profile from a pack, with its logo bytes by role (null when absent). */
    public static final class Entry {
        public final VehicleProfile profile;
        public final byte[][] logos = new byte[2][];
        public final String[] formats = new String[2];
        /** The visualizer animation's GIF bytes, or null. */
        public byte[] animation;

        Entry(VehicleProfile profile) {
            this.profile = profile;
        }
    }

    /** A validated pack. */
    public static final class Pack {
        public String name = "";
        public final List<Entry> entries = new ArrayList<>();
        public final List<String> compatibleSkins = new ArrayList<>();
    }

    private BrandingPack() {}

    // ---- reading ----

    /** Reads and validates a pack. Throws with a user-readable message when anything is wrong. */
    public static Pack read(InputStream raw) throws IOException, InvalidPackException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new Limited(raw, MAX_PACK_BYTES))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (++entries > MAX_ENTRIES * 4) throw new InvalidPackException("The pack has too many files.");
                if (e.isDirectory() || isJunk(name)) continue;
                String problem = pathProblem(name);
                if (problem != null) throw new InvalidPackException(problem);
                if (files.containsKey(name)) throw new InvalidPackException("The pack lists " + name + " twice.");
                if (files.size() >= MAX_ENTRIES) throw new InvalidPackException("The pack has too many files.");
                int cap = name.equals("manifest.json") ? MAX_MANIFEST_BYTES : MAX_ENTRY_BYTES;
                // Sizes in the ZIP header can lie; count what actually comes out.
                byte[] data = readCapped(zip, cap, name);
                total += data.length;
                if (total > MAX_TOTAL_BYTES) throw new InvalidPackException("The pack is too large when unpacked.");
                files.put(name, data);
            }
        } catch (Limited.TooLargeException ex) {
            throw new InvalidPackException("The pack file is larger than " + (MAX_PACK_BYTES >> 20) + " MB.");
        } catch (java.util.zip.ZipException ex) {
            // Android 14+ refuses ".." and absolute entry names itself, before pathProblem sees them.
            String m = ex.getMessage();
            if (m != null && m.contains("Invalid zip entry path")) {
                throw new InvalidPackException("The pack contains an unsafe file path.");
            }
            throw new InvalidPackException("This isn't a valid ZIP file.");
        }

        byte[] manifestBytes = files.get("manifest.json");
        if (manifestBytes == null) throw new InvalidPackException("The pack has no manifest.json.");
        JSONObject m;
        try {
            m = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
        } catch (JSONException ex) {
            throw new InvalidPackException("manifest.json isn't valid JSON.");
        }
        if (!FORMAT.equals(m.optString("format"))) throw new InvalidPackException("This isn't a RetroDeck branding pack.");
        int version = m.optInt("formatVersion", -1);
        if (version < 1) throw new InvalidPackException("The pack has no format version.");
        if (version > FORMAT_VERSION) {
            throw new InvalidPackException("This pack needs a newer RetroDeck (pack format " + version + ").");
        }

        Pack pack = new Pack();
        pack.name = VehicleProfile.cleanName(m.optString("name", ""));
        JSONArray skins = m.optJSONArray("compatibleSkins");
        for (int i = 0; skins != null && i < skins.length() && i < 32; i++) {
            String s = skins.optString(i, "");
            if (s.matches("[a-z0-9_-]{1,32}")) pack.compatibleSkins.add(s);
        }
        JSONArray profiles = m.optJSONArray("profiles");
        if (profiles == null || profiles.length() == 0) throw new InvalidPackException("The pack has no vehicle profiles.");
        if (profiles.length() > MAX_PROFILES) throw new InvalidPackException("The pack has more than " + MAX_PROFILES + " profiles.");

        for (int i = 0; i < profiles.length(); i++) {
            JSONObject po = profiles.optJSONObject(i);
            if (po == null) throw new InvalidPackException("Profile " + (i + 1) + " isn't valid.");
            Entry entry = new Entry(ProfileJson.read(po));
            JSONObject assets = po.optJSONObject("assets");
            for (int r = 0; r < 2; r++) {
                entry.profile.logoFile[r] = null;
                String path = assets == null ? null : assets.optString(VehicleProfile.ROLES[r], null);
                if (path == null || path.isEmpty()) continue;
                byte[] data = files.get(path);
                if (data == null) throw new InvalidPackException("The pack is missing " + path + ".");
                entry.formats[r] = checkImage(path, data);
                entry.logos[r] = data;
            }
            entry.profile.animationFile = null;
            String anim = assets == null ? null : assets.optString("animation", null);
            if (anim != null && !anim.isEmpty()) {
                byte[] data = files.get(anim);
                if (data == null) throw new InvalidPackException("The pack is missing " + anim + ".");
                if (!anim.endsWith(".gif")) throw new InvalidPackException(anim + " isn't a GIF.");
                try {
                    com.maxhayim.retrodeck.core.Gif.parse(data);
                } catch (com.maxhayim.retrodeck.core.Gif.FormatException ex) {
                    throw new InvalidPackException(anim + ": " + ex.getMessage());
                }
                entry.animation = data;
            }
            pack.entries.add(entry);
        }
        return pack;
    }

    /** Validates one image and returns its format. */
    static String checkImage(String path, byte[] data) throws InvalidPackException {
        String format = ImageFormat.sniff(data);
        if (format == null) throw new InvalidPackException(path + " isn't a PNG, JPEG, WebP or SVG image.");
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.US);
        boolean matches = ext.equals(format) || (ext.equals("jpg") && format.equals(ImageFormat.JPEG));
        if (!matches) throw new InvalidPackException(path + " is named ." + ext + " but contains " + format + ".");
        if (ImageFormat.SVG.equals(format)) {
            String problem = ImageFormat.svgProblem(new String(data, StandardCharsets.UTF_8));
            if (problem != null) throw new InvalidPackException(path + ": " + problem + ".");
            return format;
        }
        int[] d = ImageFormat.dimensions(data, data.length);
        if (d == null) throw new InvalidPackException(path + " is damaged.");
        if (d[0] > ImageFormat.MAX_DIMENSION || d[1] > ImageFormat.MAX_DIMENSION) {
            throw new InvalidPackException(path + " is " + d[0] + "x" + d[1] + "; the limit is "
                    + ImageFormat.MAX_DIMENSION + " pixels a side.");
        }
        return format;
    }

    /** Why a ZIP entry name isn't allowed, or null when it's fine. */
    static String pathProblem(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains("..")
                || name.contains(":") || name.contains("//")) {
            return "The pack contains an unsafe file path: " + printable(name);
        }
        if (name.equals("manifest.json")) return null;
        if (name.matches("assets/[A-Za-z0-9._-]{1,64}\\.(png|jpg|jpeg|webp|svg|gif)")) return null;
        return "The pack contains a file RetroDeck doesn't accept: " + printable(name);
    }

    /** Files added by macOS and Windows when zipping; skipped silently. */
    private static boolean isJunk(String name) {
        return name.startsWith("__MACOSX/") || name.endsWith(".DS_Store") || name.endsWith("Thumbs.db");
    }

    private static String printable(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && i < 80; i++) {
            char c = s.charAt(i);
            b.append(Character.isISOControl(c) ? '?' : c);
        }
        return b.toString();
    }

    private static byte[] readCapped(InputStream in, int cap, String name) throws IOException, InvalidPackException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > cap) throw new InvalidPackException(name + " is larger than " + (cap >> 10) + " KB.");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Stops reading after max bytes, so a huge file can't fill memory or storage. */
    static final class Limited extends java.io.FilterInputStream {
        static final class TooLargeException extends IOException {}

        private final long max;
        private long count;

        Limited(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0 && ++count > max) throw new TooLargeException();
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0 && (count += n) > max) throw new TooLargeException();
            return n;
        }
    }

    // ---- writing ----

    /** Writes a pack. logos.get(profile)[role] is the PNG bytes of that logo or null. */
    public static void write(OutputStream out, String name, List<VehicleProfile> profiles,
                             Map<VehicleProfile, byte[][]> logos, String createdWith) throws IOException {
        write(out, name, profiles, logos, new java.util.HashMap<>(), createdWith);
    }

    /** Writes a pack; animations.get(profile) is the GIF bytes of its animation, if it has one. */
    public static void write(OutputStream out, String name, List<VehicleProfile> profiles, Map<VehicleProfile, byte[][]> logos,
                             Map<VehicleProfile, byte[]> animations, String createdWith) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            JSONObject m = new JSONObject();
            JSONArray list = new JSONArray();
            Map<String, byte[]> assets = new LinkedHashMap<>();
            Set<String> skins = new HashSet<>();
            try {
                m.put("format", FORMAT);
                boolean animated = false;
                for (VehicleProfile p : profiles) animated |= animations.get(p) != null;
                m.put("formatVersion", animated ? FORMAT_VERSION : 1);
                m.put("name", name);
                m.put("createdWith", createdWith);
                int i = 0;
                for (VehicleProfile p : profiles) {
                    JSONObject po = ProfileJson.write(p);
                    // Logo file names are local to this device; the pack refers to its own paths.
                    JSONObject logosJson = po.optJSONObject("logos");
                    JSONObject a = new JSONObject();
                    byte[][] bytes = logos.get(p);
                    for (int r = 0; r < 2; r++) {
                        if (logosJson != null && logosJson.optJSONObject(VehicleProfile.ROLES[r]) != null) {
                            logosJson.optJSONObject(VehicleProfile.ROLES[r]).remove("file");
                        }
                        if (bytes == null || bytes[r] == null) continue;
                        String path = "assets/p" + i + "-" + VehicleProfile.ROLES[r] + ".png";
                        assets.put(path, bytes[r]);
                        a.put(VehicleProfile.ROLES[r], path);
                    }
                    JSONObject anim = po.optJSONObject("animation");
                    if (anim != null) anim.remove("file");
                    if (animations.get(p) != null) {
                        String path = "assets/p" + i + "-animation.gif";
                        assets.put(path, animations.get(p));
                        a.put("animation", path);
                    }
                    po.put("assets", a);
                    list.put(po);
                    skins.add(p.skin);
                    i++;
                }
                m.put("profiles", list);
                JSONArray sk = new JSONArray();
                for (String s : skins) sk.put(s);
                m.put("compatibleSkins", sk);
            } catch (JSONException e) {
                throw new IOException(e);
            }
            zip.putNextEntry(new ZipEntry("manifest.json"));
            try {
                zip.write(m.toString(2).getBytes(StandardCharsets.UTF_8));
            } catch (JSONException e) {
                throw new IOException(e);
            }
            zip.closeEntry();
            for (Map.Entry<String, byte[]> e : assets.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
    }
}
