package com.maxhayim.retrodeck.core;

import java.util.ArrayList;
import java.util.List;

/** The built-in radio skins. */
public final class Skins {

    public static final String CLASSIC = "classic85";
    public static final String DIGITAL = "digital96";
    public static final String MODERN = "modern";

    private static List<Skin> all;

    private Skins() {}

    public static synchronized List<Skin> all() {
        if (all == null) {
            all = new ArrayList<>();
            all.add(classic());
            all.add(digital());
            all.add(modern());
        }
        return all;
    }

    /** The skin with this id, or the classic one. */
    public static Skin get(String id) {
        for (Skin s : all()) if (s.id.equals(id)) return s;
        return all().get(0);
    }

    public static boolean exists(String id) {
        for (Skin s : all()) if (s.id.equals(id)) return true;
        return false;
    }

    /** Brushed silver, a black LCD window, a big volume knob and a cassette slot. */
    private static Skin classic() {
        Skin s = new Skin(CLASSIC, "Classic 1985", "1980s", 0xFF5B5F66, 0xFF2E3035, 0xFF141518, 0xFF0B0C0A,
                0xFF1E1F22, 0xFF3A3D43, 0xFF17181B, 0xFF26282C, Skin.LCD_SEGMENT, 0.012f);
        s.knob = new Box(0.035f, 0.17f, 0.195f, 0.62f);
        s.lcd = new Box(0.23f, 0.15f, 0.77f, 0.62f);
        s.lcdText = new Box(0.415f, 0.18f, 0.755f, 0.42f);
        s.presets = new Box(0.23f, 0.67f, 0.77f, 0.80f);
        s.slot = new Box(0.555f, 0.875f, 0.77f, 0.935f);
        s.control(Skin.POWER, "PWR", 0.04f, 0.68f, 0.19f, 0.80f)
                .control(Skin.MENU, "MENU", 0.04f, 0.84f, 0.19f, 0.95f)
                .control(Skin.SOURCE, "SRC", 0.805f, 0.15f, 0.965f, 0.27f)
                .control(Skin.PREV, "|<<", 0.805f, 0.30f, 0.88f, 0.44f)
                .control(Skin.NEXT, ">>|", 0.89f, 0.30f, 0.965f, 0.44f)
                .control(Skin.PLAY, "PLAY", 0.805f, 0.47f, 0.965f, 0.60f)
                .control(Skin.DISPLAY, "DISP", 0.805f, 0.67f, 0.965f, 0.80f);
        s.zone(Zones.HEADER, 0.32f, 0.025f, 0.68f, 0.115f)
                .zone(Zones.LCD, 0.245f, 0.18f, 0.40f, 0.42f)
                .zone(Zones.FACEPLATE, 0.23f, 0.85f, 0.53f, 0.96f)
                .zone(Zones.CORNER, 0.805f, 0.84f, 0.965f, 0.96f)
                .zone(Zones.VISUALIZER, 0.245f, 0.44f, 0.755f, 0.60f);
        return s;
    }

    /** Black rubber buttons, a wide dot-matrix display and a CD slot. */
    private static Skin digital() {
        Skin s = new Skin(DIGITAL, "Digital 1996", "1990s", 0xFF26282C, 0xFF0F1012, 0xFF050506, 0xFF07100D,
                0xFF000000, 0xFF2A2C31, 0xFF0A0A0C, 0xFF9AA0A8, Skin.LCD_DOT, 0.03f);
        s.knob = new Box(0.02f, 0.14f, 0.16f, 0.54f);
        s.lcd = new Box(0.18f, 0.14f, 0.82f, 0.54f);
        s.lcdText = new Box(0.345f, 0.17f, 0.81f, 0.38f);
        s.presets = new Box(0.18f, 0.60f, 0.82f, 0.72f);
        s.slot = new Box(0.64f, 0.95f, 0.98f, 0.975f);
        s.control(Skin.POWER, "PWR", 0.02f, 0.60f, 0.16f, 0.72f)
                .control(Skin.MENU, "MENU", 0.02f, 0.77f, 0.16f, 0.90f)
                .control(Skin.PREV, "|<<", 0.18f, 0.77f, 0.31f, 0.90f)
                .control(Skin.PLAY, "PLAY", 0.33f, 0.77f, 0.46f, 0.90f)
                .control(Skin.NEXT, ">>|", 0.48f, 0.77f, 0.61f, 0.90f)
                .control(Skin.SOURCE, "SRC", 0.84f, 0.14f, 0.98f, 0.28f)
                .control(Skin.DISPLAY, "DISP", 0.84f, 0.32f, 0.98f, 0.46f);
        s.zone(Zones.HEADER, 0.36f, 0.02f, 0.64f, 0.115f)
                .zone(Zones.LCD, 0.19f, 0.17f, 0.33f, 0.38f)
                .zone(Zones.FACEPLATE, 0.64f, 0.77f, 0.82f, 0.92f)
                .zone(Zones.CORNER, 0.84f, 0.77f, 0.98f, 0.92f)
                .zone(Zones.VISUALIZER, 0.19f, 0.40f, 0.81f, 0.52f);
        return s;
    }

    /** A full glass touchscreen like a current head unit. */
    private static Skin modern() {
        Skin s = new Skin(MODERN, "Modern Glass", "2020s", 0xFF151922, 0xFF07080B, 0xFF000000, 0xFF0C1018,
                0xFF1C2230, 0xFF1A202C, 0xFF2A3242, 0xFF8C96A8, Skin.LCD_MODERN, 0.025f);
        s.lcd = new Box(0.03f, 0.15f, 0.97f, 0.70f);
        s.lcdText = new Box(0.33f, 0.20f, 0.95f, 0.46f);
        s.control(Skin.POWER, "PWR", 0.03f, 0.76f, 0.12f, 0.94f)
                .control(Skin.SOURCE, "SRC", 0.13f, 0.76f, 0.24f, 0.94f)
                .control(Skin.VOL_DOWN, "VOL-", 0.25f, 0.76f, 0.34f, 0.94f)
                .control(Skin.PREV, "|<<", 0.35f, 0.76f, 0.45f, 0.94f)
                .control(Skin.PLAY, "PLAY", 0.46f, 0.76f, 0.56f, 0.94f)
                .control(Skin.NEXT, ">>|", 0.57f, 0.76f, 0.67f, 0.94f)
                .control(Skin.VOL_UP, "VOL+", 0.68f, 0.76f, 0.77f, 0.94f)
                .control(Skin.DISPLAY, "DISP", 0.78f, 0.76f, 0.87f, 0.94f)
                .control(Skin.MENU, "MENU", 0.88f, 0.76f, 0.97f, 0.94f);
        s.zone(Zones.HEADER, 0.03f, 0.025f, 0.30f, 0.125f)
                .zone(Zones.LCD, 0.05f, 0.20f, 0.31f, 0.66f)
                .zone(Zones.FACEPLATE, 0.38f, 0.025f, 0.62f, 0.125f)
                .zone(Zones.CORNER, 0.86f, 0.50f, 0.95f, 0.66f)
                .zone(Zones.VISUALIZER, 0.33f, 0.49f, 0.84f, 0.66f);
        return s;
    }
}
