package com.maxhayim.retrodeck.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A radio skin: its colours, where each control sits, and the branding zones it offers. Every box is
 * normalised to the stereo's size. Zones must stay clear of the controls and the track text, which
 * SkinTest checks for every built-in skin, so a logo can never cover a button.
 */
public final class Skin {

    /**
     * Display types: a vacuum fluorescent display (lit segments on dark glass), a dot-matrix one, a
     * modern screen, and a reflective LCD (dark segments on a backlit grey-green panel).
     */
    public static final int LCD_SEGMENT = 0, LCD_DOT = 1, LCD_MODERN = 2, LCD_REFLECTIVE = 3;

    /** One button on the faceplate. */
    public static final class Control {
        /** What the key does, or null for a key that is only part of the look (it lights but does nothing). */
        public final String id;
        public final String label;
        public final Box box;
        /** A round key, drawn as the largest circle in its box. */
        public final boolean round;

        Control(String id, String label, Box box, boolean round) {
            this.id = id;
            this.label = label;
            this.box = box;
            this.round = round;
        }
    }

    /** Dark segments on a reflective LCD, whatever the backlight colour. */
    public static final int REFLECTIVE_INK = 0xFF141B18;

    public static final String POWER = "power", SOURCE = "source", PREV = "prev", PLAY = "play", NEXT = "next",
            DISPLAY = "display", MENU = "menu", VOL_DOWN = "vol_down", VOL_UP = "vol_up", PRESET = "preset";

    public final String id, name, era;
    public final int faceTop, faceBottom, bezel, lcdBack, lcdFrame, buttonFace, buttonEdge, printColor;
    public final int lcdStyle;
    public final float corner;
    /** The volume knob, or null when the skin uses VOL buttons. */
    public Box knob;
    public Box lcd, lcdText;
    /** Six preset buttons share this box, or null for none. */
    public Box presets;
    /** Presets in one row of six, or two rows of three. */
    public int presetRows = 1;
    /** A printed name on each preset key besides its number, as on tape decks (REW, FF), or null. */
    public String[] presetLabels;
    /** Backlit legend colour, or 0 to light them in the illumination colour. */
    public int keyLight;
    /** Legend colour by day, when the backlight is off: the paint printed on the keys. */
    public int keyPrint = 0xFFB4B9C0;
    /** A lit scale of dots around the volume knob. */
    public boolean knobDots = true;
    /** Visualizer segments shade from the illumination colour at the bottom to the accent at the top. */
    public boolean gradientBars;
    /**
     * A fluorescent display with real segments: 14-segment letters in fixed character cells, a 7-segment
     * clock and printed indicators that light, instead of text in a font.
     */
    public boolean segmentFont;
    /** The bulbs behind the keys, as x,y pairs in screen fractions, or null for even backlighting. */
    public float[] bulbs;
    /** Keys that are only part of the design, like a changer's DISC SELECT; they light up but do nothing. */
    public final List<Control> decor = new ArrayList<>();
    /** Small knobs that are only part of the design, like BASS and TREB; label is "TOP|BOTTOM" print. */
    public final List<Control> trims = new ArrayList<>();
    /** A decorative tape or CD slot, or null. */
    public Box slot;
    /** A second, larger door, like a disc changer's under the radio, or null. */
    public Box changer;
    public final List<Control> controls = new ArrayList<>();
    private final Map<String, Box> zones = new LinkedHashMap<>();

    Skin(String id, String name, String era, int faceTop, int faceBottom, int bezel, int lcdBack, int lcdFrame,
         int buttonFace, int buttonEdge, int printColor, int lcdStyle, float corner) {
        this.id = id;
        this.name = name;
        this.era = era;
        this.faceTop = faceTop;
        this.faceBottom = faceBottom;
        this.bezel = bezel;
        this.lcdBack = lcdBack;
        this.lcdFrame = lcdFrame;
        this.buttonFace = buttonFace;
        this.buttonEdge = buttonEdge;
        this.printColor = printColor;
        this.lcdStyle = lcdStyle;
        this.corner = corner;
    }

    Skin control(String id, String label, float l, float t, float r, float b) {
        return control(id, label, new Box(l, t, r, b), false);
    }

    Skin control(String id, String label, Box box, boolean round) {
        if (id == null) decor.add(new Control(null, label, box, round));
        else controls.add(new Control(id, label, box, round));
        return this;
    }

    Skin decor(String label, float l, float t, float r, float b) {
        return control(null, label, new Box(l, t, r, b), false);
    }

    Skin trim(String label, float l, float t, float r, float b) {
        trims.add(new Control(null, label, new Box(l, t, r, b), true));
        return this;
    }

    /** The colour the display draws text and bars in: the illumination, or dark ink on a reflective LCD. */
    public int lcdInk(int illumination) {
        return lcdStyle == LCD_REFLECTIVE ? REFLECTIVE_INK : illumination;
    }

    Skin zone(String zone, float l, float t, float r, float b) {
        zones.put(zone, new Box(l, t, r, b));
        return this;
    }

    /** The zone's box, including the shared startup and standby boxes, or null if the skin lacks it. */
    public Box zone(String zone) {
        if (Zones.STARTUP.equals(zone)) return Zones.STARTUP_BOX;
        if (Zones.STANDBY.equals(zone)) return Zones.STANDBY_BOX;
        return zones.get(zone);
    }

    /** The faceplate zones this skin declares. */
    public List<String> faceplateZones() {
        return Collections.unmodifiableList(new ArrayList<>(zones.keySet()));
    }

    /** The six preset button boxes, left to right and then top to bottom, or an empty list. */
    public List<Box> presetBoxes() {
        List<Box> out = new ArrayList<>();
        if (presets == null) return out;
        int rows = presetRows == 2 ? 2 : 1, cols = 6 / rows;
        float gap = presets.width() * (rows == 2 ? 0.03f : 0.015f);
        float w = (presets.width() - gap * (cols - 1)) / cols;
        float vgap = rows == 2 ? presets.height() * 0.08f : 0;
        float h = (presets.height() - vgap * (rows - 1)) / rows;
        for (int i = 0; i < 6; i++) {
            int row = i / cols, col = i % cols;
            float l = presets.left + col * (w + gap), t = presets.top + row * (h + vgap);
            out.add(new Box(l, t, l + w, t + h));
        }
        return out;
    }

    /** Everything a logo must never cover: buttons (working or not), knob, presets and the track text. */
    public List<Box> keepClear() {
        List<Box> out = new ArrayList<>();
        for (Control c : controls) out.add(c.box);
        for (Control c : decor) out.add(c.box);
        for (Control c : trims) out.add(c.box);
        out.addAll(presetBoxes());
        if (knob != null) out.add(knob);
        if (lcdText != null) out.add(lcdText);
        return out;
    }
}
