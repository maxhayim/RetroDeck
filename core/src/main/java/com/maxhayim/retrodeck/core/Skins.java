package com.maxhayim.retrodeck.core;

import java.util.ArrayList;
import java.util.List;

/** The built-in radio skins. */
public final class Skins {

    public static final String CLASSIC = "classic85";
    public static final String DIGITAL = "digital96";
    public static final String MODERN = "modern";
    public static final String FACTORY = "z32factory90";
    public static final String HIFI = "hifi84";
    public static final String DETACHABLE = "detach98";

    private static List<Skin> all;

    private Skins() {}

    public static synchronized List<Skin> all() {
        if (all == null) {
            all = new ArrayList<>();
            all.add(classic());
            all.add(digital());
            all.add(modern());
            all.add(hifi());
            all.add(factory());
            all.add(detachable());
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

    /**
     * A 1980s Japanese hi-fi deck: black anodised aluminium with champagne print, slim keys with bright
     * cut edges, a small spun knob, an amber fluorescent display and a row of tape switches.
     */
    private static Skin hifi() {
        Skin s = new Skin(HIFI, "Hi-Fi 1984", "1980s", 0xFF2A2B2E, 0xFF111214, 0xFF060607, 0xFF0A0B09,
                0xFF141518, 0xFF26282C, 0xFF101113, 0xFFC2AC82, Skin.LCD_SEGMENT, 0.005f);
        s.knob = new Box(0.035f, 0.19f, 0.185f, 0.55f);
        s.lcd = new Box(0.23f, 0.18f, 0.77f, 0.62f);
        s.lcdText = new Box(0.405f, 0.21f, 0.755f, 0.44f);
        s.presets = new Box(0.23f, 0.69f, 0.77f, 0.77f);
        s.slot = new Box(0.30f, 0.05f, 0.70f, 0.10f);
        s.control(Skin.POWER, "PWR", 0.035f, 0.64f, 0.185f, 0.71f)
                .control(Skin.MENU, "MENU", 0.035f, 0.74f, 0.185f, 0.81f)
                .control(Skin.SOURCE, "SRC", 0.80f, 0.18f, 0.965f, 0.265f)
                .control(Skin.PREV, "|<<", 0.80f, 0.295f, 0.877f, 0.38f)
                .control(Skin.NEXT, ">>|", 0.888f, 0.295f, 0.965f, 0.38f)
                .control(Skin.PLAY, "PLAY", 0.80f, 0.41f, 0.965f, 0.495f)
                .control(Skin.DISPLAY, "DISP", 0.80f, 0.535f, 0.965f, 0.62f);
        String[] switches = {"NR B", "NR C", "MTL", "LOUD", "MONO", "DX"};
        for (int i = 0; i < switches.length; i++) {
            float l = 0.23f + i * 0.092f;
            s.decor(switches[i], l, 0.835f, l + 0.08f, 0.885f);
        }
        s.zone(Zones.HEADER, 0.035f, 0.035f, 0.25f, 0.13f)
                .zone(Zones.LCD, 0.245f, 0.21f, 0.39f, 0.44f)
                .zone(Zones.FACEPLATE, 0.80f, 0.69f, 0.965f, 0.80f)
                .zone(Zones.CORNER, 0.80f, 0.845f, 0.965f, 0.95f)
                .zone(Zones.VISUALIZER, 0.245f, 0.46f, 0.755f, 0.60f);
        return s;
    }

    /**
     * A 1990s factory stack, as fitted by the car maker: a tuner and tape deck over a three-disc
     * changer, in black plastic set into a textured dash. Tape keys double as presets, and the
     * changer's reflective LCD shows dark segments on a backlit panel.
     */
    private static Skin factory() {
        Skin s = new Skin(FACTORY, "Z32 Factory 1990", "1990s", 0xFF26282C, 0xFF141517, 0xFF070708, 0xFF93A89C,
                0xFF0A0B0C, 0xFF2A2C30, 0xFF0E0F11, 0xFFB4B9C0, Skin.LCD_REFLECTIVE, 0.008f);
        s.knobDots = false;
        // The radio and tape deck.
        s.knob = new Box(0.044f, 0.07f, 0.168f, 0.305f);
        s.trim("BASS|BAL", 0.046f, 0.35f, 0.098f, 0.44f)
                .trim("TREB|FADER", 0.112f, 0.35f, 0.164f, 0.44f);
        s.slot = new Box(0.272f, 0.066f, 0.69f, 0.211f);
        s.presets = new Box(0.272f, 0.237f, 0.614f, 0.444f);
        s.presetRows = 2;
        s.presetLabels = new String[]{"REW", "STOP", "FF", "APS REW", "NR", "APS FF"};
        s.control(Skin.SOURCE, "EJECT", 0.182f, 0.07f, 0.258f, 0.185f)
                .control(Skin.DISPLAY, "SCAN", 0.182f, 0.211f, 0.258f, 0.308f)
                .decor("FM/AM", 0.182f, 0.334f, 0.258f, 0.44f)
                .control(Skin.MENU, "PROG", 0.704f, 0.07f, 0.785f, 0.211f)
                .decor("\u25B2", 0.628f, 0.237f, 0.69f, 0.334f)
                .decor("\u25BC", 0.628f, 0.347f, 0.69f, 0.444f)
                .decor("LOUD", 0.88f, 0.237f, 0.951f, 0.308f);
        // The changer.
        s.changer = new Box(0.215f, 0.542f, 0.947f, 0.704f);
        s.lcd = new Box(0.044f, 0.74f, 0.50f, 0.938f);
        s.lcdText = new Box(0.16f, 0.752f, 0.49f, 0.858f);
        s.control(Skin.POWER, "PWR", 0.044f, 0.637f, 0.187f, 0.704f)
                .decor("1", 0.528f, 0.762f, 0.590f, 0.826f)
                .decor("2", 0.597f, 0.762f, 0.659f, 0.826f)
                .decor("3", 0.666f, 0.762f, 0.728f, 0.826f)
                .decor("RPT", 0.757f, 0.762f, 0.842f, 0.826f)
                .decor("<<", 0.528f, 0.848f, 0.619f, 0.929f)
                .decor(">>", 0.628f, 0.848f, 0.719f, 0.929f)
                .control(Skin.PREV, "|<<", 0.733f, 0.848f, 0.790f, 0.929f)
                .control(Skin.NEXT, ">>|", 0.795f, 0.848f, 0.852f, 0.929f)
                .control(Skin.PLAY, "PLAY", new Box(0.866f, 0.74f, 0.951f, 0.938f), true);
        s.zone(Zones.HEADER, 0.36f, 0.09f, 0.60f, 0.175f)
                .zone(Zones.LCD, 0.052f, 0.76f, 0.15f, 0.85f)
                .zone(Zones.FACEPLATE, 0.044f, 0.542f, 0.187f, 0.623f)
                .zone(Zones.CORNER, 0.804f, 0.07f, 0.956f, 0.211f)
                .zone(Zones.VISUALIZER, 0.055f, 0.866f, 0.49f, 0.93f);
        return s;
    }

    /**
     * A late-1990s detachable faceplate: curved graphite with chrome trim, a big encoder in a chrome
     * ring, round keys at the side and a colour dot-matrix display whose bars shade into the accent.
     */
    private static Skin detachable() {
        Skin s = new Skin(DETACHABLE, "Detachable 1998", "1990s", 0xFF3A3E45, 0xFF16181B, 0xFF060607, 0xFF040A12,
                0xFF000000, 0xFF2C2F35, 0xFF0B0C0E, 0xFF9DA4AE, Skin.LCD_DOT, 0.03f);
        s.gradientBars = true;
        s.knob = new Box(0.74f, 0.12f, 0.96f, 0.62f);
        s.lcd = new Box(0.10f, 0.15f, 0.70f, 0.58f);
        s.lcdText = new Box(0.255f, 0.18f, 0.69f, 0.38f);
        s.presets = new Box(0.10f, 0.67f, 0.70f, 0.77f);
        s.control(Skin.POWER, "PWR", new Box(0.02f, 0.15f, 0.085f, 0.265f), true)
                .control(null, "OPEN", new Box(0.02f, 0.30f, 0.085f, 0.415f), true)
                .control(null, "ATT", new Box(0.02f, 0.45f, 0.085f, 0.565f), true)
                .control(Skin.SOURCE, "SRC", 0.10f, 0.81f, 0.19f, 0.90f)
                .control(Skin.PREV, "|<<", 0.20f, 0.81f, 0.29f, 0.90f)
                .control(Skin.PLAY, "PLAY", 0.30f, 0.81f, 0.39f, 0.90f)
                .control(Skin.NEXT, ">>|", 0.40f, 0.81f, 0.49f, 0.90f)
                .control(Skin.DISPLAY, "DISP", 0.50f, 0.81f, 0.59f, 0.90f)
                .control(Skin.MENU, "MENU", 0.60f, 0.81f, 0.70f, 0.90f);
        s.zone(Zones.HEADER, 0.32f, 0.055f, 0.68f, 0.125f)
                .zone(Zones.LCD, 0.115f, 0.18f, 0.245f, 0.38f)
                .zone(Zones.FACEPLATE, 0.74f, 0.68f, 0.96f, 0.77f)
                .zone(Zones.CORNER, 0.74f, 0.80f, 0.96f, 0.90f)
                .zone(Zones.VISUALIZER, 0.115f, 0.41f, 0.69f, 0.56f);
        return s;
    }
}
