package com.maxhayim.retrodeck.core;

import java.util.ArrayList;
import java.util.List;

/** The built-in radio skins. */
public final class Skins {

    public static final String CLASSIC = "classic85";
    public static final String DIGITAL = "digital96";
    public static final String MODERN = "modern";
    public static final String FACTORY = "z32factory90";
    public static final String FACTORY_94 = "a80factory94";
    public static final String HIFI = "hifi84";
    public static final String DETACHABLE = "detach98";
    public static final String BLUETOOTH = "bt16";
    public static final String EQ_STACK = "eqstack89";
    public static final String LEVEL = "level91";
    public static final String TRIO = "trio93";
    public static final String SPECTRUM = "spectrum97";
    public static final String MULTI = "multi2000";

    private static List<Skin> all;

    private Skins() {}

    public static synchronized List<Skin> all() {
        if (all == null) {
            // Single DIN, then double DIN, each by year: the order Settings lists them in.
            all = new ArrayList<>();
            all.add(hifi());
            all.add(classic());
            all.add(digital());
            all.add(detachable());
            all.add(bluetooth());
            all.add(eqStack());
            all.add(factory());
            all.add(level());
            all.add(trio());
            all.add(factory94());
            all.add(spectrum());
            all.add(multi());
            all.add(modern());
        }
        return all;
    }

    /** The skin with this id, or the classic one. */
    public static Skin get(String id) {
        for (Skin s : all()) if (s.id.equals(id)) return s;
        for (Skin s : all()) if (s.id.equals(CLASSIC)) return s;
        return all().get(0);
    }

    /**
     * Where a single-DIN radio sits on an ordinary screen: across the lower half, about 3.5 to 1 as a
     * real one is, with a screen of its own above it.
     */
    static final Box SINGLE_BODY = new Box(0.02f, 0.48f, 0.98f, 0.97f);
    /** The visualizer screen above it, set into the dash. */
    static final Box SINGLE_SCREEN = new Box(0.10f, 0.06f, 0.90f, 0.40f);
    /** On a single-DIN head unit's own wide screen the radio is the whole screen. */
    static final Box WIDE_BODY = new Box(0f, 0f, 1f, 1f);
    /** Screens at least this wide for their height are a single-DIN unit's: 1280 x 480 and 1920 x 720 are 2.67. */
    static final float WIDE_ASPECT = 2.2f;

    /** The faceplate being built: SINGLE_BODY, or WIDE_BODY while building the wide variants. */
    private static Box face = SINGLE_BODY;
    private static List<Skin> wide;

    private static void single(Skin s) {
        s.body = face;
        s.screen = face == SINGLE_BODY ? SINGLE_SCREEN : null;
    }

    /**
     * The skin as it should be drawn on a w x h view: a single-DIN skin fills a wide screen on its
     * own, and on any other it sits under a visualizer screen. Other skins are drawn as they are.
     */
    public static synchronized Skin fit(Skin s, int w, int h) {
        if (s.din != 1 || h <= 0) return s;
        boolean wantWide = w >= h * WIDE_ASPECT;
        if (wantWide == s.wide) return s;
        if (!wantWide) return get(s.id);
        if (wide == null) {
            List<Skin> built = new ArrayList<>();
            face = WIDE_BODY;
            try {
                built.add(hifi());
                built.add(classic());
                built.add(digital());
                built.add(detachable());
                built.add(bluetooth());
            } finally {
                face = SINGLE_BODY;
            }
            for (Skin k : built) {
                k.wide = true;
                k.art = k.art + "-wide";
            }
            wide = built;
        }
        for (Skin k : wide) if (k.id.equals(s.id)) return k;
        return s;
    }

    /** The wide variants, for tools and tests. */
    static List<Skin> wide() {
        fit(get(CLASSIC), 3, 1);
        return wide;
    }

    /** A box on a single-DIN faceplate, given as fractions of the faceplate, on the screen. */
    private static Box in(float l, float t, float r, float b) {
        Box f = face;
        return new Box(f.left + l * f.width(), f.top + t * f.height(), f.left + r * f.width(), f.top + b * f.height());
    }

    public static boolean exists(String id) {
        for (Skin s : all()) if (s.id.equals(id)) return true;
        return false;
    }

    /** Brushed silver, a black LCD window, a big volume knob and a cassette slot, in one DIN. */
    private static Skin classic() {
        Skin s = new Skin(CLASSIC, "Classic 1985", "1980s", 1, 0xFF5B5F66, 0xFF2E3035, 0xFF141518, 0xFF0B0C0A,
                0xFF1E1F22, 0xFF3A3D43, 0xFF17181B, 0xFF26282C, Skin.LCD_SEGMENT, 0.012f);
        single(s);
        s.art = "skins/" + CLASSIC;
        s.knob = in(0.025f, 0.07f, 0.205f, 0.72f);
        s.lcd = in(0.235f, 0.07f, 0.625f, 0.60f);
        s.lcdText = in(0.36f, 0.11f, 0.615f, 0.40f);
        s.presets = in(0.235f, 0.66f, 0.625f, 0.82f);
        s.slot = in(0.30f, 0.875f, 0.56f, 0.935f);
        s.control(Skin.POWER, "PWR", in(0.025f, 0.80f, 0.11f, 0.94f))
                .control(Skin.MENU, "MENU", in(0.12f, 0.80f, 0.205f, 0.94f))
                .control(Skin.SOURCE, "SRC", in(0.655f, 0.07f, 0.81f, 0.30f))
                .control(Skin.DISPLAY, "DISP", in(0.825f, 0.07f, 0.975f, 0.30f))
                .control(Skin.PREV, "|<<", in(0.655f, 0.36f, 0.727f, 0.58f))
                .control(Skin.NEXT, ">>|", in(0.738f, 0.36f, 0.81f, 0.58f))
                .control(Skin.PLAY, "PLAY", in(0.825f, 0.36f, 0.975f, 0.58f));
        s.zone(Zones.HEADER, in(0.655f, 0.64f, 0.975f, 0.79f))
                .zone(Zones.LCD, in(0.245f, 0.11f, 0.35f, 0.40f))
                .zone(Zones.FACEPLATE, in(0.655f, 0.84f, 0.81f, 0.95f))
                .zone(Zones.CORNER, in(0.825f, 0.84f, 0.975f, 0.95f))
                .zone(Zones.VISUALIZER, in(0.245f, 0.44f, 0.615f, 0.57f));
        return s;
    }

    /** Black rubber buttons, a wide dot-matrix display and a CD slot, in one DIN. */
    private static Skin digital() {
        Skin s = new Skin(DIGITAL, "Digital 1996", "1990s", 1, 0xFF26282C, 0xFF0F1012, 0xFF050506, 0xFF07100D,
                0xFF000000, 0xFF2A2C31, 0xFF0A0A0C, 0xFF9AA0A8, Skin.LCD_DOT, 0.03f);
        single(s);
        s.art = "skins/" + DIGITAL;
        s.knob = in(0.02f, 0.13f, 0.17f, 0.64f);
        s.lcd = in(0.19f, 0.14f, 0.80f, 0.62f);
        s.lcdText = in(0.36f, 0.18f, 0.79f, 0.42f);
        s.presets = in(0.19f, 0.69f, 0.55f, 0.93f);
        s.slot = in(0.82f, 0.665f, 0.98f, 0.70f);
        s.control(Skin.POWER, "PWR", in(0.02f, 0.71f, 0.09f, 0.93f))
                .control(Skin.MENU, "MENU", in(0.10f, 0.71f, 0.17f, 0.93f))
                .control(Skin.PREV, "|<<", in(0.565f, 0.69f, 0.635f, 0.93f))
                .control(Skin.PLAY, "PLAY", in(0.645f, 0.69f, 0.715f, 0.93f))
                .control(Skin.NEXT, ">>|", in(0.725f, 0.69f, 0.795f, 0.93f))
                .control(Skin.SOURCE, "SRC", in(0.82f, 0.14f, 0.98f, 0.36f))
                .control(Skin.DISPLAY, "DISP", in(0.82f, 0.40f, 0.98f, 0.62f));
        s.zone(Zones.HEADER, in(0.36f, 0.02f, 0.64f, 0.11f))
                .zone(Zones.LCD, in(0.20f, 0.18f, 0.35f, 0.42f))
                .zone(Zones.FACEPLATE, in(0.02f, 0.02f, 0.17f, 0.10f))
                .zone(Zones.CORNER, in(0.82f, 0.74f, 0.98f, 0.95f))
                .zone(Zones.VISUALIZER, in(0.20f, 0.46f, 0.79f, 0.59f));
        return s;
    }

    /** A full glass touchscreen like a current head unit. */
    private static Skin modern() {
        Skin s = new Skin(MODERN, "Modern Glass", "2020s", 2, 0xFF151922, 0xFF07080B, 0xFF000000, 0xFF0C1018,
                0xFF1C2230, 0xFF1A202C, 0xFF2A3242, 0xFF8C96A8, Skin.LCD_MODERN, 0.025f);
        s.art = "skins/" + MODERN;
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
     * A 1980s Japanese hi-fi deck in one DIN: black anodised aluminium with champagne print, slim keys
     * with bright cut edges, a small spun knob, an amber fluorescent display and a row of tape switches.
     */
    private static Skin hifi() {
        Skin s = new Skin(HIFI, "Hi-Fi 1984", "1980s", 1, 0xFF2A2B2E, 0xFF111214, 0xFF060607, 0xFF0A0B09,
                0xFF141518, 0xFF26282C, 0xFF101113, 0xFFC2AC82, Skin.LCD_SEGMENT, 0.005f);
        single(s);
        s.art = "skins/" + HIFI;
        s.knob = in(0.04f, 0.27f, 0.19f, 0.72f);
        s.lcd = in(0.235f, 0.20f, 0.625f, 0.68f);
        s.lcdText = in(0.36f, 0.24f, 0.615f, 0.47f);
        s.presets = in(0.235f, 0.73f, 0.625f, 0.845f);
        s.slot = in(0.30f, 0.06f, 0.56f, 0.13f);
        s.control(Skin.POWER, "PWR", in(0.025f, 0.84f, 0.11f, 0.95f))
                .control(Skin.MENU, "MENU", in(0.12f, 0.84f, 0.205f, 0.95f))
                .control(Skin.SOURCE, "SRC", in(0.655f, 0.20f, 0.81f, 0.40f))
                .control(Skin.DISPLAY, "DISP", in(0.825f, 0.20f, 0.975f, 0.40f))
                .control(Skin.PREV, "|<<", in(0.655f, 0.46f, 0.727f, 0.66f))
                .control(Skin.NEXT, ">>|", in(0.738f, 0.46f, 0.81f, 0.66f))
                .control(Skin.PLAY, "PLAY", in(0.825f, 0.46f, 0.975f, 0.66f));
        String[] switches = {"NR B", "NR C", "MTL", "LOUD", "MONO", "DX"};
        for (int i = 0; i < switches.length; i++) {
            float l = 0.235f + i * 0.0655f;
            s.decor(switches[i], in(l, 0.88f, l + 0.0595f, 0.955f));
        }
        s.zone(Zones.HEADER, in(0.025f, 0.05f, 0.205f, 0.21f))
                .zone(Zones.LCD, in(0.245f, 0.24f, 0.35f, 0.47f))
                .zone(Zones.FACEPLATE, in(0.655f, 0.73f, 0.81f, 0.93f))
                .zone(Zones.CORNER, in(0.825f, 0.73f, 0.975f, 0.93f))
                .zone(Zones.VISUALIZER, in(0.245f, 0.51f, 0.615f, 0.65f));
        return s;
    }

    /**
     * A 1990s factory stack, as fitted by the car maker: a tuner and tape deck over a three-disc
     * changer, in black plastic set into a textured dash. Tape keys double as presets, and the
     * display is a fluorescent one behind black glass, lit the same by day and night.
     */
    private static Skin factory() {
        Skin s = new Skin(FACTORY, "Z32 Factory 1990", "1990s", 2, 0xFF26282C, 0xFF141517, 0xFF070708, 0xFF06090A,
                0xFF0A0B0C, 0xFF2A2C30, 0xFF0E0F11, 0xFFB4B9C0, Skin.LCD_SEGMENT, 0.008f);
        s.knobDots = false;
        s.segmentFont = true;
        s.art = "skins/" + FACTORY;
        // The factory unit's display: pale blue characters behind plum-red filter glass, whatever
        // colour the dash is lit.
        s.phosphor = 0xFF8EC8E4;
        s.lcdGlass = 0xFF2C0F22;
        // White legends: printed by day, backlit white at night.
        s.keyLight = 0xFFF4F6F8;
        s.keyPrint = 0xFFC4C8CD;
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
     * A mid-1990s factory CD and tape unit, as fitted by the car maker: charcoal plastic, a CD slot
     * across the top over a strip display that numbers the six preset keys, reflective LCDs, olive
     * grey by day and lit lime green at night, the knob beside a cassette door, and a row of source keys and a TUNE rocker
     * along the foot. At night the legends light green too, whatever the dash.
     */
    private static Skin factory94() {
        Skin s = new Skin(FACTORY_94, "A80 Factory 1994", "1990s", 2, 0xFF2B2C2F, 0xFF1A1B1D, 0xFF0A0A0B, 0xFF9EB89A,
                0xFF0A0B0C, 0xFF26272A, 0xFF0E0F11, 0xFFC9CDD2, Skin.LCD_REFLECTIVE, 0.008f);
        s.knobDots = false;
        s.art = "skins/" + FACTORY_94;
        // The display panels: olive grey by day, lime green at night when the backlight shows.
        s.backlight = 0xFFA6D83A;
        s.panelDay = 0xFF7A7762;
        s.keyLight = 0xFF7CF27A;
        s.keyPrint = 0xFFC9CDD2;
        // The upper half: CD slot, the preset strip display and keys, and the main display.
        s.slot = new Box(0.133f, 0.073f, 0.875f, 0.159f);
        s.presets = new Box(0.074f, 0.373f, 0.636f, 0.476f);
        s.lcd = new Box(0.675f, 0.255f, 0.965f, 0.47f);
        s.lcdText = new Box(0.74f, 0.27f, 0.955f, 0.37f);
        s.decor("\u23CF", 0.042f, 0.073f, 0.119f, 0.166f);
        // The middle: knob, cassette door, EQ and R·M.
        s.knob = new Box(0.05f, 0.53f, 0.20f, 0.80f);
        s.changer = new Box(0.318f, 0.512f, 0.682f, 0.688f);
        s.control(Skin.POWER, "PWR", 0.205f, 0.545f, 0.25f, 0.755f)
                .decor("\u23CF", 0.254f, 0.537f, 0.303f, 0.678f)
                .control(Skin.MENU, "EQ", 0.71f, 0.561f, 0.803f, 0.683f)
                .decor("R\u00B7M", 0.807f, 0.561f, 0.898f, 0.683f);
        // The foot: source keys, the TUNE rocker (track back and forward), SCAN and the tone knobs.
        s.decor("AM\u00B7FM", 0.11f, 0.824f, 0.193f, 0.946f)
                .control(Skin.PLAY, "TAPE", 0.197f, 0.824f, 0.278f, 0.946f)
                .control(Skin.SOURCE, "CD", 0.283f, 0.824f, 0.366f, 0.946f)
                .control(Skin.PREV, "\u2228", 0.40f, 0.83f, 0.486f, 0.95f)
                .control(Skin.NEXT, "\u2227", 0.488f, 0.83f, 0.574f, 0.95f)
                .control(Skin.DISPLAY, "SCAN", 0.608f, 0.837f, 0.685f, 0.959f);
        s.trim("BASS|", 0.735f, 0.853f, 0.783f, 0.943f)
                .trim("MID|", 0.803f, 0.853f, 0.851f, 0.943f)
                .trim("TREB|", 0.871f, 0.853f, 0.919f, 0.943f);
        s.zone(Zones.HEADER, 0.36f, 0.585f, 0.64f, 0.675f)
                .zone(Zones.LCD, 0.685f, 0.275f, 0.735f, 0.37f)
                .zone(Zones.FACEPLATE, 0.68f, 0.195f, 0.83f, 0.245f)
                .zone(Zones.CORNER, 0.885f, 0.06f, 0.97f, 0.18f)
                .zone(Zones.VISUALIZER, 0.685f, 0.385f, 0.955f, 0.455f);
        return s;
    }

    /**
     * A late-1990s detachable faceplate in one DIN: curved graphite with chrome trim, a big encoder in a
     * chrome ring, round keys at the side and a colour dot-matrix display whose bars shade into the accent.
     */
    private static Skin detachable() {
        Skin s = new Skin(DETACHABLE, "Detachable 1998", "1990s", 1, 0xFF3A3E45, 0xFF16181B, 0xFF060607, 0xFF040A12,
                0xFF000000, 0xFF2C2F35, 0xFF0B0C0E, 0xFF9DA4AE, Skin.LCD_DOT, 0.03f);
        single(s);
        s.art = "skins/" + DETACHABLE;
        s.gradientBars = true;
        s.knob = in(0.735f, 0.08f, 0.965f, 0.70f);
        s.lcd = in(0.10f, 0.15f, 0.70f, 0.56f);
        s.lcdText = in(0.255f, 0.19f, 0.69f, 0.38f);
        s.presets = in(0.10f, 0.62f, 0.70f, 0.76f);
        s.control(Skin.POWER, "PWR", in(0.02f, 0.12f, 0.083f, 0.34f), true)
                .control(null, "OPEN", in(0.02f, 0.41f, 0.083f, 0.63f), true)
                .control(null, "ATT", in(0.02f, 0.70f, 0.083f, 0.92f), true)
                .control(Skin.SOURCE, "SRC", in(0.10f, 0.81f, 0.19f, 0.95f))
                .control(Skin.PREV, "|<<", in(0.20f, 0.81f, 0.29f, 0.95f))
                .control(Skin.PLAY, "PLAY", in(0.30f, 0.81f, 0.39f, 0.95f))
                .control(Skin.NEXT, ">>|", in(0.40f, 0.81f, 0.49f, 0.95f))
                .control(Skin.DISPLAY, "DISP", in(0.50f, 0.81f, 0.59f, 0.95f))
                .control(Skin.MENU, "MENU", in(0.60f, 0.81f, 0.70f, 0.95f));
        s.zone(Zones.HEADER, in(0.30f, 0.025f, 0.50f, 0.12f))
                .zone(Zones.LCD, in(0.115f, 0.19f, 0.245f, 0.38f))
                .zone(Zones.FACEPLATE, in(0.735f, 0.74f, 0.965f, 0.84f))
                .zone(Zones.CORNER, in(0.735f, 0.86f, 0.965f, 0.96f))
                .zone(Zones.VISUALIZER, in(0.115f, 0.42f, 0.69f, 0.53f));
        return s;
    }

    /**
     * A budget Bluetooth unit of the 2010s in one DIN: gloss black, a big knob in a lit ring, curved
     * keys either side of it edged with light, and a segment display lit blue.
     */
    private static Skin bluetooth() {
        Skin s = new Skin(BLUETOOTH, "Bluetooth 2016", "2010s", 1, 0xFF1A1C20, 0xFF060607, 0xFF020203, 0xFF02040A,
                0xFF000000, 0xFF16181C, 0xFF050506, 0xFFC8CCD2, Skin.LCD_SEGMENT, 0.02f);
        single(s);
        s.art = "skins/" + BLUETOOTH;
        s.phosphor = 0xFF4A86FF;
        s.knobDots = false;
        s.keyPrint = 0xFFC8CCD2;
        s.knob = in(0.205f, 0.08f, 0.395f, 0.92f);
        s.lcd = in(0.555f, 0.13f, 0.80f, 0.80f);
        s.lcdText = in(0.62f, 0.17f, 0.79f, 0.47f);
        s.presets = in(0.82f, 0.10f, 0.965f, 0.58f);
        s.presetRows = 2;
        s.control(Skin.PREV, "|<<", in(0.07f, 0.10f, 0.19f, 0.44f))
                .control(Skin.POWER, "PWR", in(0.07f, 0.56f, 0.19f, 0.90f))
                .control(Skin.NEXT, ">>|", in(0.41f, 0.10f, 0.53f, 0.44f))
                .control(Skin.SOURCE, "SRC", in(0.41f, 0.56f, 0.53f, 0.90f))
                .control(Skin.PLAY, "PLAY", in(0.82f, 0.64f, 0.865f, 0.88f))
                .control(Skin.DISPLAY, "DISP", in(0.87f, 0.64f, 0.915f, 0.88f))
                .control(Skin.MENU, "MENU", in(0.92f, 0.64f, 0.965f, 0.88f));
        s.zone(Zones.HEADER, in(0.60f, 0.025f, 0.78f, 0.11f))
                .zone(Zones.LCD, in(0.565f, 0.17f, 0.61f, 0.47f))
                .zone(Zones.FACEPLATE, in(0.555f, 0.84f, 0.80f, 0.96f))
                .zone(Zones.CORNER, in(0.82f, 0.91f, 0.965f, 0.975f))
                .zone(Zones.VISUALIZER, in(0.565f, 0.53f, 0.79f, 0.76f));
        return s;
    }

    /**
     * A late-1980s silver stack: a graphic equaliser, its bars yellow under blue with red peaks, over a
     * CD unit with an orange fluorescent display, a volume rocker and mint green keys.
     */
    private static Skin eqStack() {
        Skin s = new Skin(EQ_STACK, "EQ Stack 1989", "1980s", 2, 0xFFB9BDC2, 0xFF8A8E94, 0xFF0A0A0B, 0xFF050606,
                0xFF0A0B0C, 0xFF45484D, 0xFF1A1B1D, 0xFF2A2C30, Skin.LCD_SEGMENT, 0.006f);
        s.art = "skins/" + EQ_STACK;
        s.segmentFont = true;
        s.knobDots = false;
        s.phosphor = 0xFFFF9A3C;
        int y = 0xFFF4D23A, b = 0xFF4FB0F0;
        s.barInks = new int[]{y, y, y, y, y, y, b, b, b, b};
        s.peakInk = 0xFFFF4A3A;
        s.keyPrint = 0xFFD8DCE0;
        s.units.add(new Box(0.02f, 0.03f, 0.98f, 0.48f));
        s.units.add(new Box(0.02f, 0.52f, 0.98f, 0.97f));
        // The equaliser.
        s.eq = new Box(0.05f, 0.075f, 0.95f, 0.355f);
        s.control(Skin.MENU, "MENU", 0.33f, 0.395f, 0.4233f, 0.44f);
        String[] eqKeys = {"FLAT", "CURVE", "BASS", "TREB", "DEFEAT"};
        for (int i = 0; i < eqKeys.length; i++) {
            float l = 0.4353f + i * 0.1053f;
            s.decor(eqKeys[i], l, 0.395f, l + 0.0933f, 0.44f);
        }
        // The CD unit.
        s.slot = new Box(0.20f, 0.57f, 0.86f, 0.625f);
        s.lcd = new Box(0.585f, 0.665f, 0.955f, 0.875f);
        s.lcdText = new Box(0.665f, 0.685f, 0.945f, 0.86f);
        s.presets = new Box(0.315f, 0.675f, 0.515f, 0.915f);
        s.presetRows = 2;
        s.control(Skin.POWER, "PWR", 0.05f, 0.565f, 0.11f, 0.625f)
                .control(Skin.VOL_UP, "\u25B2", 0.05f, 0.675f, 0.11f, 0.79f)
                .control(Skin.VOL_DOWN, "\u25BC", 0.05f, 0.80f, 0.11f, 0.915f)
                .control(Skin.SOURCE, "SRC", 0.125f, 0.675f, 0.205f, 0.79f)
                .control(Skin.DISPLAY, "DISP", 0.215f, 0.675f, 0.295f, 0.79f)
                .control(Skin.PREV, "|<<", 0.125f, 0.80f, 0.175f, 0.915f)
                .control(Skin.PLAY, "PLAY", 0.183f, 0.80f, 0.237f, 0.915f)
                .control(Skin.NEXT, ">>|", 0.245f, 0.80f, 0.295f, 0.915f)
                .decor("A", 0.53f, 0.675f, 0.565f, 0.745f)
                .decor("B", 0.53f, 0.76f, 0.565f, 0.83f)
                .decor("C", 0.53f, 0.845f, 0.565f, 0.915f)
                .decor("MUTE", 0.585f, 0.89f, 0.65f, 0.945f)
                .decor("TITLE", 0.66f, 0.89f, 0.725f, 0.945f);
        s.zone(Zones.HEADER, 0.05f, 0.385f, 0.30f, 0.455f)
                .zone(Zones.LCD, 0.595f, 0.685f, 0.655f, 0.86f)
                .zone(Zones.FACEPLATE, 0.745f, 0.89f, 0.955f, 0.95f)
                .zone(Zones.CORNER, 0.88f, 0.56f, 0.955f, 0.635f)
                .zone(Zones.VISUALIZER, 0.43f, 0.15f, 0.93f, 0.315f);
        return s;
    }

    /**
     * An early-1990s black head unit with one big window: an orange fluorescent readout over a cyan
     * spectrum analyser whose bars turn orange at the top, between columns of slim keys.
     */
    private static Skin level() {
        Skin s = new Skin(LEVEL, "Level Meter 1991", "1990s", 2, 0xFF222326, 0xFF0D0E10, 0xFF050506, 0xFF040607,
                0xFF000000, 0xFF2A2C30, 0xFF0E0F11, 0xFFA8ADB4, Skin.LCD_SEGMENT, 0.01f);
        s.art = "skins/" + LEVEL;
        s.knobDots = false;
        s.phosphor = 0xFFFFA040;
        int cy = 0xFF4FE3F0, or = 0xFFFF8A2A;
        s.barInks = new int[]{cy, cy, cy, cy, cy, cy, cy, cy, cy, or, or, or};
        s.barSegs = 12;
        s.peakInk = or;
        s.slot = new Box(0.20f, 0.06f, 0.80f, 0.12f);
        s.lcd = new Box(0.17f, 0.18f, 0.86f, 0.78f);
        s.lcdText = new Box(0.30f, 0.21f, 0.84f, 0.40f);
        s.presets = new Box(0.17f, 0.82f, 0.62f, 0.93f);
        s.control(Skin.POWER, "PWR", 0.03f, 0.05f, 0.13f, 0.14f)
                .control(Skin.SOURCE, "SRC", 0.03f, 0.17f, 0.13f, 0.28f)
                .control(Skin.DISPLAY, "DISP", 0.03f, 0.30f, 0.13f, 0.41f)
                .control(Skin.MENU, "MENU", 0.03f, 0.43f, 0.13f, 0.54f)
                .control(Skin.VOL_UP, "\u25B2", 0.03f, 0.57f, 0.13f, 0.72f)
                .control(Skin.VOL_DOWN, "\u25BC", 0.03f, 0.74f, 0.13f, 0.89f)
                .control(Skin.PREV, "|<<", 0.65f, 0.82f, 0.715f, 0.93f)
                .control(Skin.PLAY, "PLAY", 0.725f, 0.82f, 0.79f, 0.93f)
                .control(Skin.NEXT, ">>|", 0.80f, 0.82f, 0.86f, 0.93f);
        String[] side = {"EQ", "LOUD", "BASS", "TREB", "BAL", "FADE"};
        for (int i = 0; i < side.length; i++) {
            float t = 0.18f + i * 0.1f;
            s.decor(side[i], 0.89f, t, 0.97f, t + 0.085f);
        }
        s.zone(Zones.HEADER, 0.82f, 0.045f, 0.97f, 0.13f)
                .zone(Zones.LCD, 0.18f, 0.21f, 0.28f, 0.40f)
                .zone(Zones.FACEPLATE, 0.89f, 0.82f, 0.97f, 0.93f)
                .zone(Zones.CORNER, 0.03f, 0.91f, 0.13f, 0.975f)
                .zone(Zones.VISUALIZER, 0.20f, 0.45f, 0.84f, 0.75f);
        return s;
    }

    /**
     * An early-1990s three-piece stack in black: an equaliser with green fluorescent bars, a disc
     * changer controller and a head unit with a tape door, their keys moulded in mint green.
     */
    private static Skin trio() {
        Skin s = new Skin(TRIO, "Trio Stack 1993", "1990s", 2, 0xFF1E1F22, 0xFF0B0C0D, 0xFF040405, 0xFF030605,
                0xFF000000, 0xFF2A2C30, 0xFF0E0F11, 0xFFB4B9C0, Skin.LCD_SEGMENT, 0.008f);
        s.art = "skins/" + TRIO;
        s.segmentFont = true;
        s.knobDots = false;
        s.phosphor = 0xFF6CF29A;
        s.peakInk = 0xFFE4FFE8;
        s.keyPrint = 0xFF1A2A24;
        s.keyLight = 0xFF9CFFD0;
        s.units.add(new Box(0.03f, 0.03f, 0.97f, 0.33f));
        s.units.add(new Box(0.03f, 0.355f, 0.97f, 0.645f));
        s.units.add(new Box(0.03f, 0.67f, 0.97f, 0.97f));
        // The equaliser.
        s.eq = new Box(0.18f, 0.06f, 0.88f, 0.30f);
        s.control(Skin.SOURCE, "SRC", 0.06f, 0.065f, 0.15f, 0.135f)
                .control(Skin.DISPLAY, "DISP", 0.06f, 0.15f, 0.15f, 0.22f)
                .control(Skin.MENU, "MENU", 0.06f, 0.235f, 0.15f, 0.305f);
        for (int i = 0; i < 4; i++) {
            float t = 0.065f + i * 0.06f;
            s.decor("M" + (i + 1), 0.905f, t, 0.955f, t + 0.05f);
        }
        // The changer controller.
        s.slot = new Box(0.20f, 0.385f, 0.70f, 0.435f);
        s.lcd = new Box(0.58f, 0.465f, 0.95f, 0.62f);
        s.lcdText = new Box(0.65f, 0.475f, 0.94f, 0.61f);
        s.decor("OPEN", 0.06f, 0.385f, 0.13f, 0.435f)
                .control(Skin.PREV, "|<<", 0.06f, 0.465f, 0.13f, 0.535f)
                .control(Skin.PLAY, "PLAY", 0.14f, 0.465f, 0.21f, 0.535f)
                .control(Skin.NEXT, ">>|", 0.22f, 0.465f, 0.29f, 0.535f)
                .decor("SCAN", 0.06f, 0.55f, 0.13f, 0.61f)
                .decor("RPT", 0.14f, 0.55f, 0.21f, 0.61f)
                .decor("RDM", 0.22f, 0.55f, 0.29f, 0.61f);
        // The head unit.
        s.changer = new Box(0.26f, 0.70f, 0.62f, 0.79f);
        s.presets = new Box(0.26f, 0.82f, 0.62f, 0.94f);
        s.presetRows = 2;
        s.control(Skin.VOL_UP, "\u25B2", 0.06f, 0.70f, 0.12f, 0.825f)
                .control(Skin.VOL_DOWN, "\u25BC", 0.06f, 0.835f, 0.12f, 0.94f)
                .control(Skin.POWER, "PWR", 0.14f, 0.70f, 0.22f, 0.78f)
                .decor("MODE", 0.14f, 0.79f, 0.22f, 0.86f)
                .decor("LOUD", 0.14f, 0.87f, 0.22f, 0.94f)
                .decor("BAND", 0.66f, 0.70f, 0.80f, 0.79f)
                .decor("SCAN", 0.81f, 0.70f, 0.95f, 0.79f);
        s.zone(Zones.HEADER, 0.32f, 0.47f, 0.55f, 0.53f)
                .zone(Zones.LCD, 0.59f, 0.475f, 0.64f, 0.61f)
                .zone(Zones.FACEPLATE, 0.66f, 0.85f, 0.95f, 0.94f)
                .zone(Zones.CORNER, 0.72f, 0.385f, 0.95f, 0.435f)
                .zone(Zones.VISUALIZER, 0.21f, 0.12f, 0.85f, 0.265f);
        return s;
    }

    /**
     * A late-1990s silver stack: a receiver with a blue-white dot-matrix display and a knob, over a CD
     * player whose window is a spectrum analyser, its fine bars white under a green line and blue above.
     */
    private static Skin spectrum() {
        Skin s = new Skin(SPECTRUM, "Spectrum 1997", "1990s", 2, 0xFFC4C8CD, 0xFF8C9097, 0xFF0A0A0B, 0xFF03050A,
                0xFF000000, 0xFF505359, 0xFF1C1D20, 0xFF2A2C30, Skin.LCD_DOT, 0.01f);
        s.art = "skins/" + SPECTRUM;
        s.knobDots = false;
        s.phosphor = 0xFFD8E6FF;
        s.barSegs = 20;
        int[] inks = new int[20];
        for (int i = 0; i < inks.length; i++) inks[i] = i < 6 ? 0xFFE8F0FF : i == 6 ? 0xFF3CE0A0 : 0xFF4A74FF;
        s.barInks = inks;
        s.peakInk = 0xFFE8F0FF;
        s.keyPrint = 0xFFE4E7EB;
        s.units.add(new Box(0.02f, 0.03f, 0.98f, 0.47f));
        s.units.add(new Box(0.02f, 0.51f, 0.98f, 0.97f));
        // The receiver.
        s.slot = new Box(0.22f, 0.055f, 0.66f, 0.10f);
        s.lcd = new Box(0.13f, 0.135f, 0.70f, 0.33f);
        s.lcdText = new Box(0.25f, 0.15f, 0.69f, 0.32f);
        s.knob = new Box(0.73f, 0.10f, 0.86f, 0.36f);
        s.presets = new Box(0.13f, 0.37f, 0.70f, 0.44f);
        s.decor("LOUD", 0.04f, 0.15f, 0.11f, 0.23f)
                .control(Skin.SOURCE, "SRC", 0.88f, 0.07f, 0.96f, 0.15f)
                .control(Skin.DISPLAY, "DISP", 0.88f, 0.17f, 0.96f, 0.25f)
                .control(Skin.MENU, "MENU", 0.88f, 0.27f, 0.96f, 0.35f)
                .control(Skin.POWER, "PWR", 0.04f, 0.37f, 0.11f, 0.44f)
                .decor("AUD", 0.73f, 0.38f, 0.86f, 0.44f);
        // The CD player.
        s.eq = new Box(0.20f, 0.56f, 0.78f, 0.86f);
        s.control(Skin.PREV, "|<<", 0.81f, 0.58f, 0.875f, 0.66f)
                .control(Skin.NEXT, ">>|", 0.885f, 0.58f, 0.95f, 0.66f)
                .control(Skin.PLAY, "PLAY", 0.81f, 0.68f, 0.95f, 0.76f)
                .decor("OPEN", 0.81f, 0.78f, 0.95f, 0.86f);
        s.zone(Zones.HEADER, 0.04f, 0.045f, 0.20f, 0.11f)
                .zone(Zones.LCD, 0.14f, 0.15f, 0.24f, 0.32f)
                .zone(Zones.FACEPLATE, 0.04f, 0.56f, 0.18f, 0.64f)
                .zone(Zones.CORNER, 0.81f, 0.89f, 0.95f, 0.95f)
                .zone(Zones.VISUALIZER, 0.23f, 0.58f, 0.75f, 0.83f);
        return s;
    }

    /**
     * A double-DIN unit from around 2000, nearly all display: a big blue fluorescent window whose bars
     * fan round a half circle, keys down both sides and along the foot with a chrome volume lever.
     */
    private static Skin multi() {
        Skin s = new Skin(MULTI, "Multi Control 2000", "2000s", 2, 0xFF1C1D20, 0xFF08090A, 0xFF030304, 0xFF02050A,
                0xFF000000, 0xFF202226, 0xFF0A0A0C, 0xFFE0A040, Skin.LCD_SEGMENT, 0.012f);
        s.art = "skins/" + MULTI;
        s.knobDots = false;
        s.arcBars = true;
        s.barSegs = 8;
        s.phosphor = 0xFF5CC8FF;
        int c = 0xFF46D2FF;
        s.barInks = new int[]{c, c, c, c, c, c, c, 0xFFFFC040};
        s.peakInk = 0xFFFF8A30;
        s.keyPrint = 0xFFE0A040;
        s.keyLight = 0xFFFFB050;
        s.lcd = new Box(0.13f, 0.05f, 0.87f, 0.76f);
        s.lcdText = new Box(0.40f, 0.13f, 0.76f, 0.32f);
        s.control(Skin.DISPLAY, "DSPL", 0.025f, 0.08f, 0.105f, 0.17f)
                .decor("REP", 0.025f, 0.21f, 0.105f, 0.29f)
                .decor("SHUF", 0.025f, 0.32f, 0.105f, 0.40f)
                .decor("PGM", 0.025f, 0.43f, 0.105f, 0.51f)
                .decor("SA", 0.025f, 0.55f, 0.105f, 0.62f)
                .decor("OPEN", 0.895f, 0.08f, 0.975f, 0.17f)
                .decor("ANGLE", 0.895f, 0.20f, 0.975f, 0.29f)
                .control(Skin.POWER, "OFF", 0.895f, 0.33f, 0.975f, 0.42f)
                .control(Skin.SOURCE, "SOURCE", 0.04f, 0.81f, 0.15f, 0.93f)
                .control(Skin.MENU, "MODE", 0.16f, 0.81f, 0.27f, 0.93f)
                .decor("TI", 0.33f, 0.82f, 0.39f, 0.92f)
                .control(Skin.VOL_DOWN, "VOL-", 0.42f, 0.80f, 0.50f, 0.95f)
                .control(Skin.VOL_UP, "VOL+", 0.50f, 0.80f, 0.58f, 0.95f)
                .control(Skin.PLAY, "PLAY", 0.61f, 0.82f, 0.69f, 0.92f)
                .control(Skin.PREV, "|<<", 0.75f, 0.81f, 0.85f, 0.93f)
                .control(Skin.NEXT, ">>|", 0.86f, 0.81f, 0.96f, 0.93f);
        s.zone(Zones.HEADER, 0.42f, 0.06f, 0.58f, 0.12f)
                .zone(Zones.LCD, 0.15f, 0.36f, 0.30f, 0.70f)
                .zone(Zones.FACEPLATE, 0.025f, 0.66f, 0.105f, 0.76f)
                .zone(Zones.CORNER, 0.895f, 0.46f, 0.975f, 0.76f)
                .zone(Zones.VISUALIZER, 0.32f, 0.34f, 0.76f, 0.74f);
        return s;
    }
}
