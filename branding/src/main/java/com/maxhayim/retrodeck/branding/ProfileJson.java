package com.maxhayim.retrodeck.branding;

import com.maxhayim.retrodeck.core.Illumination;
import com.maxhayim.retrodeck.core.KeyLights;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.Visualizers;
import com.maxhayim.retrodeck.core.Zones;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads and writes profiles as JSON, for local storage and for branding packs. Reading is forgiving
 * and strict at once: missing or unknown fields get defaults, and every value is put back in range, so
 * a hand-edited or hostile pack can't produce a broken profile.
 */
public final class ProfileJson {

    private ProfileJson() {}

    public static JSONObject write(VehicleProfile p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        o.put("manufacturer", p.manufacturer);
        o.put("model", p.model);
        o.put("year", p.year);
        o.put("trim", p.trim);
        o.put("skin", p.skin);
        o.put("visualizer", p.visualizer);
        JSONObject anim = new JSONObject();
        if (p.animationFile != null) anim.put("file", p.animationFile);
        anim.put("tint", p.animationTint);
        anim.put("full", p.animationFull);
        o.put("animation", anim);
        o.put("updatedAt", p.updatedAt);

        JSONObject palette = new JSONObject();
        palette.put("illumination", p.illumination);
        palette.put("custom", Illumination.hex(p.customColor));
        palette.put("accent", Illumination.hex(p.accentColor));
        palette.put("keys", p.keyLighting);
        o.put("palette", palette);

        JSONObject logos = new JSONObject();
        for (int r = 0; r < 2; r++) {
            JSONObject l = new JSONObject();
            if (p.logoFile[r] != null) l.put("file", p.logoFile[r]);
            if (p.logoColor[r] != 0) l.put("color", Illumination.hex(p.logoColor[r]));
            l.put("adjust", writeAdjust(p.adjust[r]));
            logos.put(VehicleProfile.ROLES[r], l);
        }
        o.put("logos", logos);

        JSONObject zones = new JSONObject();
        for (String z : Zones.ALL) {
            JSONObject zo = new JSONObject();
            for (int r = 0; r < 2; r++) zo.put(VehicleProfile.ROLES[r], writePlacement(p.placement(z, r)));
            zones.put(z, zo);
        }
        o.put("placements", zones);

        JSONObject s = new JSONObject();
        s.put("enabled", p.startup.enabled);
        s.put("preset", p.startup.preset);
        s.put("durationMs", p.startup.durationMs);
        s.put("powerOn", p.startup.powerOn);
        s.put("lcdBoot", p.startup.lcdBoot);
        o.put("startup", s);
        return o;
    }

    private static JSONObject writeAdjust(LogoAdjust a) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("crop", new org.json.JSONArray().put(a.cropLeft).put(a.cropTop).put(a.cropRight).put(a.cropBottom));
        o.put("rotation", a.rotation);
        o.put("opacity", a.opacity);
        o.put("brightness", a.brightness);
        o.put("tint", Illumination.hex(a.tint));
        o.put("tintStrength", a.tintStrength);
        o.put("keepAspect", a.keepAspect);
        return o;
    }

    private static JSONObject writePlacement(Placement p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("visible", p.visible);
        o.put("scale", p.scale);
        o.put("x", p.x);
        o.put("y", p.y);
        return o;
    }

    /** Reads a profile. Logo file names are kept only if they look like our own names. */
    public static VehicleProfile read(JSONObject o) {
        VehicleProfile p = new VehicleProfile(safeId(o.optString("id", "")));
        p.manufacturer = VehicleProfile.cleanName(o.optString("manufacturer", ""));
        p.model = VehicleProfile.cleanName(o.optString("model", ""));
        p.year = VehicleProfile.cleanYear(o.optString("year", ""));
        p.trim = VehicleProfile.cleanName(o.optString("trim", ""));
        String skin = o.optString("skin", Skins.CLASSIC);
        p.skin = Skins.exists(skin) ? skin : Skins.CLASSIC;
        String viz = o.optString("visualizer", Visualizers.BARS);
        p.visualizer = Visualizers.exists(viz) ? viz : Visualizers.BARS;
        JSONObject anim = o.optJSONObject("animation");
        if (anim != null) {
            String file = anim.optString("file", null);
            p.animationFile = file != null && isAnimationName(file) ? file : null;
            p.animationTint = anim.optBoolean("tint", true);
            p.animationFull = anim.optBoolean("full", false);
        }
        p.updatedAt = o.optLong("updatedAt", 0);

        JSONObject palette = o.optJSONObject("palette");
        if (palette != null) {
            String mode = palette.optString("illumination", Illumination.AMBER);
            p.illumination = isMode(mode) ? mode : Illumination.AMBER;
            p.customColor = Illumination.parse(palette.optString("custom", null), p.customColor) | 0xFF000000;
            p.accentColor = Illumination.parse(palette.optString("accent", null), p.accentColor) | 0xFF000000;
            String keys = palette.optString("keys", KeyLights.AUTO);
            p.keyLighting = KeyLights.exists(keys) ? keys : KeyLights.AUTO;
        }

        JSONObject logos = o.optJSONObject("logos");
        for (int r = 0; r < 2 && logos != null; r++) {
            JSONObject l = logos.optJSONObject(VehicleProfile.ROLES[r]);
            if (l == null) continue;
            String file = l.optString("file", null);
            p.logoFile[r] = file != null && isAssetName(file) ? file : null;
            p.logoColor[r] = Illumination.parse(l.optString("color", null), 0);
            JSONObject a = l.optJSONObject("adjust");
            if (a != null) p.adjust[r] = readAdjust(a);
        }

        JSONObject zones = o.optJSONObject("placements");
        for (String z : Zones.ALL) {
            JSONObject zo = zones == null ? null : zones.optJSONObject(z);
            if (zo == null) continue;
            for (int r = 0; r < 2; r++) {
                JSONObject pl = zo.optJSONObject(VehicleProfile.ROLES[r]);
                if (pl != null) p.placements.get(z)[r] = readPlacement(pl);
            }
        }

        JSONObject s = o.optJSONObject("startup");
        if (s != null) {
            p.startup.enabled = s.optBoolean("enabled", true);
            p.startup.preset = s.optString("preset", StartupConfig.GLOW);
            p.startup.durationMs = s.optInt("durationMs", StartupConfig.DEFAULT_MS);
            p.startup.powerOn = s.optBoolean("powerOn", true);
            p.startup.lcdBoot = s.optBoolean("lcdBoot", true);
        }
        p.startup.sanitize();
        return p;
    }

    private static LogoAdjust readAdjust(JSONObject o) {
        LogoAdjust a = new LogoAdjust();
        org.json.JSONArray crop = o.optJSONArray("crop");
        if (crop != null && crop.length() == 4) {
            a.cropLeft = (float) crop.optDouble(0, 0);
            a.cropTop = (float) crop.optDouble(1, 0);
            a.cropRight = (float) crop.optDouble(2, 1);
            a.cropBottom = (float) crop.optDouble(3, 1);
        }
        a.rotation = (float) o.optDouble("rotation", 0);
        a.opacity = (float) o.optDouble("opacity", 1);
        a.brightness = (float) o.optDouble("brightness", 0);
        a.tint = Illumination.parse(o.optString("tint", null), 0xFFFFFFFF);
        a.tintStrength = (float) o.optDouble("tintStrength", 0);
        a.keepAspect = o.optBoolean("keepAspect", true);
        return a.sanitize();
    }

    private static Placement readPlacement(JSONObject o) {
        return new Placement(o.optBoolean("visible", false), (float) o.optDouble("scale", 1),
                (float) o.optDouble("x", 0.5), (float) o.optDouble("y", 0.5)).sanitize();
    }

    private static boolean isMode(String m) {
        for (String s : Illumination.MODES) if (s.equals(m)) return true;
        return false;
    }

    /** Logo files are named by us: letters, digits, dash and dot, ending in .png. */
    public static boolean isAssetName(String name) {
        return name.matches("[A-Za-z0-9-]{1,48}\\.png");
    }

    /** Animation files are named by us too, ending in .gif. */
    public static boolean isAnimationName(String name) {
        return name.matches("animation-[0-9]{1,20}\\.gif");
    }

    static String safeId(String id) {
        return id != null && id.matches("[A-Za-z0-9-]{1,40}") ? id : "";
    }
}
