package com.maxhayim.retrodeck.branding;

import com.maxhayim.retrodeck.core.Illumination;
import com.maxhayim.retrodeck.core.KeyLights;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.Visualizers;
import com.maxhayim.retrodeck.core.Zones;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** One vehicle: its names, logos, colours, skin and start-up screen. Stored on the device only. */
public final class VehicleProfile {

    /**
     * The logos a profile can hold: the car maker's, a model badge, and the audio brand a factory radio
     * carried (say, on its tape door). AUDIO came later: packs that use it are format 3.
     */
    public static final int MANUFACTURER = 0, MODEL = 1, AUDIO = 2;
    public static final String[] ROLES = {"manufacturer", "model", "audio"};
    public static final int COUNT = ROLES.length;
    public static final int MAX_NAME = 40;

    public String id;
    public String manufacturer = "";
    public String model = "";
    /** Kept as text so it can be blank; four digits when set. */
    public String year = "";
    public String trim = "";

    /** File names inside the profile's folder, or null when no logo was imported. */
    public final String[] logoFile = new String[COUNT];
    public final LogoAdjust[] adjust = {new LogoAdjust(), new LogoAdjust(), new LogoAdjust()};
    /** Colour found in each logo by ColorExtractor, or 0. */
    public final int[] logoColor = new int[COUNT];

    public String skin = Skins.CLASSIC;
    public String visualizer = Visualizers.BARS;
    /** The GIF the Animation visualizer plays: a file name in the profile's folder, or null. */
    public String animationFile;
    /** Lit in the display's colour (true) or in the GIF's own colours. */
    public boolean animationTint = true;
    /** Across the whole display behind the track text, instead of the visualizer strip. */
    public boolean animationFull;
    public String illumination = Illumination.AMBER;
    public int customColor = 0xFFFF7A00;
    /** Second vehicle colour, used for visualizer peaks and the start-up glow. */
    public int accentColor = 0xFFFF3B30;
    /** The visualizer's colour, the screen behind it and the radio's body; 0 keeps the skin's own. */
    public int visualizerColor, screenColor, bodyColor;
    /** When the key legends are backlit; see KeyLights. */
    public String keyLighting = KeyLights.AUTO;
    public StartupConfig startup = new StartupConfig();

    /** zone -> {manufacturer, model, audio placement}. */
    public final Map<String, Placement[]> placements = new HashMap<>();

    public long updatedAt;

    public VehicleProfile(String id) {
        this.id = id;
        resetLayout();
    }

    /** The placements a new profile starts with. */
    public void resetLayout() {
        placements.clear();
        put(Zones.HEADER, new Placement(true, 0.9f, 0.5f, 0.5f), new Placement(false, 0.8f, 0.8f, 0.5f));
        put(Zones.LCD, new Placement(true, 0.85f, 0.5f, 0.5f), new Placement(false, 0.6f, 0.5f, 0.8f));
        put(Zones.FACEPLATE, new Placement(false, 0.8f, 0.25f, 0.5f), new Placement(true, 0.9f, 0.5f, 0.5f));
        put(Zones.CORNER, new Placement(false, 0.8f, 0.5f, 0.5f), new Placement(false, 0.8f, 0.5f, 0.5f));
        put(Zones.VISUALIZER, new Placement(false, 0.9f, 0.5f, 0.5f), new Placement(false, 0.9f, 0.5f, 0.5f));
        put(Zones.STARTUP, new Placement(true, 0.65f, 0.5f, 0.42f), new Placement(true, 0.32f, 0.5f, 0.9f));
        put(Zones.STANDBY, new Placement(true, 0.8f, 0.5f, 0.5f), new Placement(false, 0.4f, 0.5f, 0.9f));
    }

    private void put(String zone, Placement manufacturer, Placement model) {
        // The audio brand starts hidden everywhere: it's placed by hand or by a pack.
        placements.put(zone, new Placement[]{manufacturer, model, new Placement(false, 0.8f, 0.5f, 0.5f)});
    }

    public Placement placement(String zone, int role) {
        Placement[] p = placements.get(zone);
        if (p == null) {
            p = new Placement[]{new Placement(), new Placement(), new Placement()};
            placements.put(zone, p);
        }
        return p[role];
    }

    /** "Manufacturer logo", "Model badge" or "Audio brand logo". */
    public static String roleLabel(int role) {
        return role == MANUFACTURER ? "Manufacturer logo" : role == MODEL ? "Model badge" : "Audio brand logo";
    }

    /** The name shown in place of a missing logo; the audio brand has none. */
    public String roleName(int role) {
        return role == MANUFACTURER ? manufacturer : role == MODEL ? model : "";
    }

    public boolean hasLogo(int role) {
        return logoFile[role] != null;
    }

    /** "1989 Halcyon Mesa GT", skipping blank parts. */
    public String displayName() {
        StringBuilder b = new StringBuilder();
        for (String s : new String[]{year, manufacturer, model, trim}) {
            if (s != null && !s.trim().isEmpty()) {
                if (b.length() > 0) b.append(' ');
                b.append(s.trim());
            }
        }
        return b.length() == 0 ? "Unnamed vehicle" : b.toString();
    }

    /** The colour that lights the stereo for this profile. */
    public int illuminationColor() {
        int fromLogos = logoColor[MANUFACTURER] != 0 ? logoColor[MANUFACTURER] : logoColor[MODEL];
        return Illumination.resolve(illumination, customColor, fromLogos);
    }

    public VehicleProfile copy() {
        VehicleProfile p = new VehicleProfile(id);
        p.manufacturer = manufacturer;
        p.model = model;
        p.year = year;
        p.trim = trim;
        for (int r = 0; r < COUNT; r++) {
            p.logoFile[r] = logoFile[r];
            p.adjust[r] = adjust[r].copy();
            p.logoColor[r] = logoColor[r];
        }
        p.skin = skin;
        p.visualizer = visualizer;
        p.animationFile = animationFile;
        p.animationTint = animationTint;
        p.animationFull = animationFull;
        p.illumination = illumination;
        p.customColor = customColor;
        p.accentColor = accentColor;
        p.visualizerColor = visualizerColor;
        p.screenColor = screenColor;
        p.bodyColor = bodyColor;
        p.keyLighting = keyLighting;
        p.startup = startup.copy();
        p.placements.clear();
        for (Map.Entry<String, Placement[]> e : placements.entrySet()) {
            Placement[] from = e.getValue(), to = new Placement[COUNT];
            for (int r = 0; r < COUNT; r++) to[r] = from[r].copy();
            p.placements.put(e.getKey(), to);
        }
        p.updatedAt = updatedAt;
        return p;
    }

    /** Trims a name typed by the user or read from a pack: no control characters, at most 40 long. */
    public static String cleanName(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < MAX_NAME; i++) {
            char c = s.charAt(i);
            if (!Character.isISOControl(c)) b.append(c);
        }
        return b.toString().trim();
    }

    /** A four-digit year from 1886 (the first car) to 2100, or "" when blank or invalid. */
    public static String cleanYear(String s) {
        if (s == null) return "";
        s = s.trim();
        if (!s.matches("\\d{4}")) return "";
        int y = Integer.parseInt(s);
        return y >= 1886 && y <= 2100 ? String.format(Locale.US, "%d", y) : "";
    }
}
