package com.maxhayim.retrodeck.branding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.maxhayim.retrodeck.core.Illumination;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.Visualizers;
import com.maxhayim.retrodeck.core.Zones;

import org.json.JSONObject;
import org.junit.Test;

public class ProfileJsonTest {

    static VehicleProfile sample() {
        VehicleProfile p = new VehicleProfile("abc-123");
        p.manufacturer = "Jeep";
        p.model = "Wrangler YJ";
        p.year = "1989";
        p.trim = "Sahara";
        p.logoFile[0] = "manufacturer-1700000000000.png";
        p.logoColor[0] = 0xFF2E7D32;
        p.adjust[0].rotation = -12.5f;
        p.adjust[0].opacity = 0.8f;
        p.adjust[0].cropLeft = 0.1f;
        p.adjust[0].tint = 0xFFFFA81E;
        p.adjust[0].tintStrength = 0.4f;
        p.adjust[1].keepAspect = false;
        p.skin = Skins.DIGITAL;
        p.visualizer = Visualizers.SCOPE;
        p.illumination = Illumination.CUSTOM;
        p.customColor = 0xFF00C8FF;
        p.accentColor = 0xFFFF00AA;
        p.visualizerColor = 0xFF00C8FF;
        p.bodyColor = 0xFF8A1020;
        p.keyLighting = com.maxhayim.retrodeck.core.KeyLights.OFF;
        p.startup.preset = StartupConfig.CRT;
        p.startup.durationMs = 4500;
        p.startup.lcdBoot = false;
        p.placement(Zones.CORNER, VehicleProfile.MODEL).visible = true;
        p.placement(Zones.CORNER, VehicleProfile.MODEL).scale = 0.7f;
        p.placement(Zones.HEADER, VehicleProfile.MANUFACTURER).x = 0.2f;
        return p;
    }

    @Test
    public void roundTripKeepsEverything() throws Exception {
        VehicleProfile p = sample();
        VehicleProfile q = ProfileJson.read(new JSONObject(ProfileJson.write(p).toString()));
        assertEquals(p.id, q.id);
        assertEquals("Jeep", q.manufacturer);
        assertEquals("Wrangler YJ", q.model);
        assertEquals("1989", q.year);
        assertEquals("Sahara", q.trim);
        assertEquals(p.logoFile[0], q.logoFile[0]);
        assertNull(q.logoFile[1]);
        assertEquals(0xFF2E7D32, q.logoColor[0]);
        assertEquals(-12.5f, q.adjust[0].rotation, 0.001);
        assertEquals(0.8f, q.adjust[0].opacity, 0.001);
        assertEquals(0.1f, q.adjust[0].cropLeft, 0.001);
        assertEquals(0xFFFFA81E, q.adjust[0].tint);
        assertEquals(0.4f, q.adjust[0].tintStrength, 0.001);
        assertFalse(q.adjust[1].keepAspect);
        assertTrue(q.adjust[0].keepAspect);
        assertEquals(Skins.DIGITAL, q.skin);
        assertEquals(Visualizers.SCOPE, q.visualizer);
        assertEquals(Illumination.CUSTOM, q.illumination);
        assertEquals(0xFF00C8FF, q.customColor);
        assertEquals(0xFFFF00AA, q.accentColor);
        assertEquals(0xFF00C8FF, q.visualizerColor);
        assertEquals(0, q.screenColor);
        assertEquals(0xFF8A1020, q.bodyColor);
        assertEquals(com.maxhayim.retrodeck.core.KeyLights.OFF, q.keyLighting);
        assertEquals(StartupConfig.CRT, q.startup.preset);
        assertEquals(4500, q.startup.durationMs);
        assertFalse(q.startup.lcdBoot);
        assertTrue(q.placement(Zones.CORNER, VehicleProfile.MODEL).visible);
        assertEquals(0.7f, q.placement(Zones.CORNER, VehicleProfile.MODEL).scale, 0.001);
        assertEquals(0.2f, q.placement(Zones.HEADER, VehicleProfile.MANUFACTURER).x, 0.001);
        assertEquals(p.illuminationColor(), q.illuminationColor());
    }

    @Test
    public void emptyObjectGivesDefaults() {
        VehicleProfile q = ProfileJson.read(new JSONObject());
        assertEquals("", q.id);
        assertEquals(Skins.CLASSIC, q.skin);
        assertEquals(Illumination.AMBER, q.illumination);
        assertTrue(q.placement(Zones.HEADER, VehicleProfile.MANUFACTURER).visible);
        assertTrue(q.startup.enabled);
    }

    @Test
    public void hostileValuesAreCleaned() throws Exception {
        JSONObject o = new JSONObject()
                .put("id", "../../etc")
                .put("manufacturer", "A\u0000B\nC" + "x".repeat(100))
                .put("year", "99999")
                .put("skin", "../skin")
                .put("visualizer", "rm")
                .put("palette", new JSONObject().put("illumination", "ultraviolet").put("custom", "red"))
                .put("logos", new JSONObject().put("manufacturer", new JSONObject()
                        .put("file", "../../../shared_prefs/x.xml")
                        .put("adjust", new JSONObject().put("opacity", 9).put("rotation", 4000).put("crop",
                                new org.json.JSONArray().put(0.9).put(0.9).put(0.1).put(0.1)))))
                .put("placements", new JSONObject().put("header", new JSONObject().put("manufacturer",
                        new JSONObject().put("scale", 50).put("x", -3).put("y", "NaN"))))
                .put("startup", new JSONObject().put("preset", "evil").put("durationMs", 999999));
        VehicleProfile q = ProfileJson.read(o);
        assertEquals("", q.id);
        assertEquals(VehicleProfile.MAX_NAME, q.manufacturer.length());
        assertFalse(q.manufacturer.contains("\u0000"));
        assertFalse(q.manufacturer.contains("\n"));
        assertEquals("", q.year);
        assertEquals(Skins.CLASSIC, q.skin);
        assertEquals(Visualizers.BARS, q.visualizer);
        assertEquals(Illumination.AMBER, q.illumination);
        assertNull(q.logoFile[0]);
        assertEquals(1f, q.adjust[0].opacity, 0);
        assertEquals(180f, q.adjust[0].rotation, 0);
        assertTrue(q.adjust[0].cropRight > q.adjust[0].cropLeft);
        assertTrue(q.adjust[0].cropBottom > q.adjust[0].cropTop);
        Placement h = q.placement(Zones.HEADER, VehicleProfile.MANUFACTURER);
        assertEquals(1f, h.scale, 0);
        assertEquals(0f, h.x, 0);
        assertTrue(h.y >= 0 && h.y <= 1);
        assertEquals(StartupConfig.GLOW, q.startup.preset);
        assertEquals(StartupConfig.MAX_MS, q.startup.durationMs);
    }

    @Test
    public void animationSettings() throws Exception {
        VehicleProfile p = sample();
        p.visualizer = Visualizers.ANIMATION;
        p.animationFile = "animation-42.gif";
        p.animationTint = false;
        p.animationFull = true;
        VehicleProfile q = ProfileJson.read(ProfileJson.write(p));
        assertEquals(Visualizers.ANIMATION, q.visualizer);
        assertEquals("animation-42.gif", q.animationFile);
        assertFalse(q.animationTint);
        assertTrue(q.animationFull);
        // Only names RetroDeck makes itself are kept.
        for (String bad : new String[]{"../x.gif", "animation-1.png", "logo.gif", "animation-1.gif/../../a"}) {
            JSONObject o = ProfileJson.write(p);
            o.getJSONObject("animation").put("file", bad);
            assertNull(bad, ProfileJson.read(o).animationFile);
        }
        // Profiles from before animations get the defaults.
        VehicleProfile old = ProfileJson.read(new JSONObject().put("id", "a"));
        assertNull(old.animationFile);
        assertTrue(old.animationTint);
        assertFalse(old.animationFull);
    }

    @Test
    public void years() {
        assertEquals("1989", VehicleProfile.cleanYear(" 1989 "));
        assertEquals("", VehicleProfile.cleanYear("1885"));
        assertEquals("", VehicleProfile.cleanYear("2101"));
        assertEquals("", VehicleProfile.cleanYear("89"));
        assertEquals("", VehicleProfile.cleanYear("abcd"));
    }

    @Test
    public void displayName() {
        assertEquals("1989 Jeep Wrangler YJ Sahara", sample().displayName());
        assertEquals("Unnamed vehicle", new VehicleProfile("x").displayName());
        VehicleProfile p = new VehicleProfile("x");
        p.manufacturer = "Nissan";
        p.model = "300ZX";
        assertEquals("Nissan 300ZX", p.displayName());
    }

    @Test
    public void copyIsDeep() {
        VehicleProfile p = sample();
        VehicleProfile c = p.copy();
        c.adjust[0].rotation = 90;
        c.placement(Zones.HEADER, 0).x = 0.9f;
        c.startup.preset = StartupConfig.FADE;
        assertEquals(-12.5f, p.adjust[0].rotation, 0);
        assertEquals(0.2f, p.placement(Zones.HEADER, 0).x, 0);
        assertEquals(StartupConfig.CRT, p.startup.preset);
    }

    @Test
    public void autoIlluminationUsesTheLogoColour() {
        VehicleProfile p = new VehicleProfile("x");
        p.illumination = Illumination.AUTO;
        assertEquals(Illumination.preset(Illumination.AMBER), p.illuminationColor());
        p.logoColor[1] = 0xFF0050FF;
        assertEquals(0xFF0050FF, p.illuminationColor());
        p.logoColor[0] = 0xFFFF0000;
        assertEquals(0xFFFF0000, p.illuminationColor());
    }
}
