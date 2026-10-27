package com.maxhayim.retrodeck.branding;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BrandingPackTest {

    // ---- helpers ----

    private static byte[] zip(Object... nameThenBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < nameThenBytes.length; i += 2) {
                z.putNextEntry(new ZipEntry((String) nameThenBytes[i]));
                z.write((byte[]) nameThenBytes[i + 1]);
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] manifest(JSONObject... profiles) throws Exception {
        JSONArray a = new JSONArray();
        for (JSONObject p : profiles) a.put(p);
        return new JSONObject().put("format", BrandingPack.FORMAT).put("formatVersion", 1).put("name", "Test")
                .put("profiles", a).toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JSONObject profile(String manufacturerAsset, String modelAsset) throws Exception {
        JSONObject p = ProfileJson.write(ProfileJsonTest.sample());
        JSONObject assets = new JSONObject();
        if (manufacturerAsset != null) assets.put("manufacturer", manufacturerAsset);
        if (modelAsset != null) assets.put("model", modelAsset);
        p.put("assets", assets);
        return p;
    }

    private static BrandingPack.Pack read(byte[] zip) throws Exception {
        return BrandingPack.read(new ByteArrayInputStream(zip));
    }

    private static void rejects(byte[] zip, String expect) throws Exception {
        try {
            read(zip);
            fail("accepted a pack that should fail: " + expect);
        } catch (BrandingPack.InvalidPackException e) {
            assertTrue("message was: " + e.getMessage(), e.getMessage().toLowerCase().contains(expect.toLowerCase()));
        }
    }

    // ---- accepted ----

    @Test
    public void exportThenImportRoundTrips() throws Exception {
        VehicleProfile p = ProfileJsonTest.sample();
        byte[] logo = TestImages.png(300, 120);
        byte[] badge = TestImages.png(90, 40);
        Map<VehicleProfile, byte[][]> logos = new HashMap<>();
        logos.put(p, new byte[][]{logo, badge});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BrandingPack.write(out, "My Jeep", Collections.singletonList(p), logos, "RetroDeck test");

        BrandingPack.Pack pack = read(out.toByteArray());
        assertEquals("My Jeep", pack.name);
        assertEquals(1, pack.entries.size());
        BrandingPack.Entry e = pack.entries.get(0);
        assertEquals("Jeep", e.profile.manufacturer);
        assertEquals("Wrangler YJ", e.profile.model);
        assertEquals("1989", e.profile.year);
        assertEquals(StartupConfig.CRT, e.profile.startup.preset);
        assertArrayEquals(logo, e.logos[0]);
        assertArrayEquals(badge, e.logos[1]);
        assertEquals(ImageFormat.PNG, e.formats[0]);
        // Device file names never travel in a pack.
        assertNull(e.profile.logoFile[0]);
        assertTrue(pack.compatibleSkins.contains(p.skin));
    }

    static byte[] gif() throws Exception {
        try (java.io.InputStream in = BrandingPackTest.class.getResourceAsStream("/gif/moving.gif")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    @Test
    public void animationTravelsInAFormat2Pack() throws Exception {
        VehicleProfile p = ProfileJsonTest.sample();
        p.animationFile = "animation-123.gif";
        p.animationTint = false;
        p.animationFull = true;
        byte[] gif = gif();
        Map<VehicleProfile, byte[]> anims = new HashMap<>();
        anims.put(p, gif);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BrandingPack.write(out, "Animated", Collections.singletonList(p), new HashMap<>(), anims, "test");

        BrandingPack.Pack pack = read(out.toByteArray());
        BrandingPack.Entry e = pack.entries.get(0);
        assertArrayEquals(gif, e.animation);
        // The device's file name stays behind; the settings travel.
        assertNull(e.profile.animationFile);
        assertEquals(false, e.profile.animationTint);
        assertEquals(true, e.profile.animationFull);
        assertEquals(2, manifestOf(out.toByteArray()).getInt("formatVersion"));
    }

    @Test
    public void packsWithoutAnimationStayFormat1() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BrandingPack.write(out, "Plain", Collections.singletonList(ProfileJsonTest.sample()), new HashMap<>(), "test");
        // So RetroDeck versions before animations still open them.
        assertEquals(1, manifestOf(out.toByteArray()).getInt("formatVersion"));
    }

    @Test
    public void rejectsAnimationsThatAreNotGifs() throws Exception {
        JSONObject p = profile(null, null);
        p.getJSONObject("assets").put("animation", "assets/a.gif");
        rejects(zip("manifest.json", manifest(p), "assets/a.gif", TestImages.png(10, 10)), "isn't a GIF");
        p.getJSONObject("assets").put("animation", "assets/a.png");
        rejects(zip("manifest.json", manifest(p), "assets/a.png", gif()), "isn't a GIF");
        p.getJSONObject("assets").put("animation", "assets/missing.gif");
        rejects(zip("manifest.json", manifest(p)), "missing");
    }

    private static JSONObject manifestOf(byte[] pack) throws Exception {
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new ByteArrayInputStream(pack))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (!e.getName().equals("manifest.json")) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = z.read(buf)) > 0) out.write(buf, 0, n);
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        throw new AssertionError("no manifest");
    }

    @Test
    public void exportWithoutLogos() throws Exception {
        VehicleProfile a = ProfileJsonTest.sample(), b = new VehicleProfile("b");
        b.manufacturer = "Toyota";
        b.model = "Supra";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BrandingPack.write(out, "Two", Arrays.asList(a, b), new HashMap<>(), "test");
        BrandingPack.Pack pack = read(out.toByteArray());
        assertEquals(2, pack.entries.size());
        assertEquals("Supra", pack.entries.get(1).profile.model);
        assertNull(pack.entries.get(1).logos[0]);
    }

    @Test
    public void acceptsEveryImageFormat() throws Exception {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 4'><rect width='10' height='4'/></svg>"
                .getBytes(StandardCharsets.UTF_8);
        BrandingPack.Pack pack = read(zip("manifest.json", manifest(profile("assets/a.jpg", "assets/b.svg")),
                "assets/a.jpg", TestImages.jpeg(64, 32), "assets/b.svg", svg));
        assertEquals(ImageFormat.JPEG, pack.entries.get(0).formats[0]);
        assertEquals(ImageFormat.SVG, pack.entries.get(0).formats[1]);
        pack = read(zip("manifest.json", manifest(profile("assets/a.webp", null)), "assets/a.webp", TestImages.webpLossless(50, 50)));
        assertEquals(ImageFormat.WEBP, pack.entries.get(0).formats[0]);
    }

    @Test
    public void ignoresMacAndWindowsJunk() throws Exception {
        BrandingPack.Pack pack = read(zip("manifest.json", manifest(profile(null, null)),
                "__MACOSX/._manifest.json", new byte[]{1, 2}, ".DS_Store", new byte[]{0}, "assets/", new byte[0]));
        assertEquals(1, pack.entries.size());
    }

    @Test
    public void ignoresUnknownManifestFields() throws Exception {
        JSONObject p = profile(null, null).put("futureThing", 42);
        byte[] m = new JSONObject(new String(manifest(p), StandardCharsets.UTF_8)).put("also", "new").toString().getBytes(StandardCharsets.UTF_8);
        assertEquals(1, read(zip("manifest.json", m)).entries.size());
    }

    // ---- refused ----

    @Test
    public void refusesPathTraversal() throws Exception {
        for (String evil : new String[]{"../evil.png", "assets/../../x.png", "/abs/x.png", "assets\\x.png", "C:/x.png", "assets//x.png"}) {
            rejects(zip("manifest.json", manifest(profile(null, null)), evil, TestImages.png(2, 2)), "unsafe file path");
        }
    }

    @Test
    public void refusesScriptsAndOtherFiles() throws Exception {
        for (String name : new String[]{"install.sh", "assets/run.js", "classes.dex", "assets/x.apk", "assets/logo.png.exe", "readme.txt"}) {
            rejects(zip("manifest.json", manifest(profile(null, null)), name, new byte[]{1}), "doesn't accept");
        }
    }

    @Test
    public void refusesMismatchedOrFakeImages() throws Exception {
        rejects(zip("manifest.json", manifest(profile("assets/a.png", null)), "assets/a.png", TestImages.jpeg(10, 10)), "contains jpeg");
        rejects(zip("manifest.json", manifest(profile("assets/a.png", null)), "assets/a.png",
                "#!/bin/sh\necho hi".getBytes(StandardCharsets.UTF_8)), "isn't a png");
        rejects(zip("manifest.json", manifest(profile("assets/a.png", null)), "assets/a.png",
                Arrays.copyOf(TestImages.png(10, 10), 14)), "damaged");
    }

    @Test
    public void refusesOversizedImages() throws Exception {
        rejects(zip("manifest.json", manifest(profile("assets/a.webp", null)), "assets/a.webp", TestImages.webpExtended(5000, 100)),
                "the limit is 4096");
    }

    @Test
    public void refusesUnsafeSvg() throws Exception {
        byte[] bomb = ("<?xml version='1.0'?><!DOCTYPE svg [<!ENTITY a 'aaaaaaaaaa'><!ENTITY b '&a;&a;&a;&a;'>]>"
                + "<svg xmlns='http://www.w3.org/2000/svg'>&b;</svg>").getBytes(StandardCharsets.UTF_8);
        rejects(zip("manifest.json", manifest(profile("assets/a.svg", null)), "assets/a.svg", bomb), "doctype");
        byte[] script = "<svg xmlns='http://www.w3.org/2000/svg'><script>x()</script></svg>".getBytes(StandardCharsets.UTF_8);
        rejects(zip("manifest.json", manifest(profile("assets/a.svg", null)), "assets/a.svg", script), "scripts");
    }

    @Test
    public void refusesZipBombs() throws Exception {
        // 9 MB of zeros compresses to a few KB; the reader counts what comes out, not the header.
        byte[] zeros = new byte[9 * 1024 * 1024];
        rejects(zip("manifest.json", manifest(profile("assets/a.png", null)), "assets/a.png", zeros), "larger than");
        byte[] bigManifest = new byte[BrandingPack.MAX_MANIFEST_BYTES + 1];
        Arrays.fill(bigManifest, (byte) ' ');
        rejects(zip("manifest.json", bigManifest), "larger than");
    }

    @Test
    public void refusesTooManyFiles() throws Exception {
        Object[] entries = new Object[2 + 2 * BrandingPack.MAX_ENTRIES];
        entries[0] = "manifest.json";
        entries[1] = manifest(profile(null, null));
        for (int i = 0; i < BrandingPack.MAX_ENTRIES; i++) {
            entries[2 + 2 * i] = "assets/f" + i + ".png";
            entries[3 + 2 * i] = TestImages.png(1, 1);
        }
        rejects(zip(entries), "too many files");
    }

    @Test
    public void refusesBadManifests() throws Exception {
        rejects(zip("assets/a.png", TestImages.png(2, 2)), "no manifest");
        rejects(zip("manifest.json", "{not json".getBytes(StandardCharsets.UTF_8)), "valid json");
        rejects(zip("manifest.json", new JSONObject().put("format", "other").put("formatVersion", 1).toString().getBytes(StandardCharsets.UTF_8)),
                "isn't a retrodeck");
        rejects(zip("manifest.json", new JSONObject().put("format", BrandingPack.FORMAT).put("formatVersion", BrandingPack.FORMAT_VERSION + 1)
                .put("profiles", new JSONArray().put(new JSONObject())).toString().getBytes(StandardCharsets.UTF_8)), "newer retrodeck");
        rejects(zip("manifest.json", new JSONObject().put("format", BrandingPack.FORMAT).put("formatVersion", 1)
                .put("profiles", new JSONArray()).toString().getBytes(StandardCharsets.UTF_8)), "no vehicle profiles");
        rejects(zip("manifest.json", manifest(profile("assets/missing.png", null))), "missing");
    }

    @Test
    public void refusesDuplicateEntries() throws Exception {
        // ZipOutputStream refuses duplicates, so build two manifests by hand-crafting a second entry name case.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("manifest.json"));
            z.write(manifest(profile(null, null)));
            z.closeEntry();
        }
        byte[] one = out.toByteArray();
        // A ZIP stream reader sees entries in order; concatenating the local entry twice repeats the name.
        int cd = indexOf(one, new byte[]{'P', 'K', 1, 2});
        byte[] local = Arrays.copyOf(one, cd);
        byte[] twice = new byte[local.length * 2];
        System.arraycopy(local, 0, twice, 0, local.length);
        System.arraycopy(local, 0, twice, local.length, local.length);
        rejects(twice, "twice");
    }

    @Test
    public void refusesNonZip() throws Exception {
        try {
            BrandingPack.Pack p = read("hello".getBytes(StandardCharsets.UTF_8));
            fail("accepted " + p);
        } catch (BrandingPack.InvalidPackException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("manifest") || e.getMessage().contains("ZIP"));
        }
    }

    @Test
    public void entryNameRules() {
        assertNull(BrandingPack.pathProblem("manifest.json"));
        assertNull(BrandingPack.pathProblem("assets/p0-manufacturer.png"));
        assertNull(BrandingPack.pathProblem("assets/Logo_2.SVG".toLowerCase()));
        List<String> bad = Arrays.asList("", "assets/", "assets/sub/x.png", "x.png", "assets/.png" + "x", "manifest.json/x");
        for (String b : bad) assertTrue(b, BrandingPack.pathProblem(b) != null);
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
