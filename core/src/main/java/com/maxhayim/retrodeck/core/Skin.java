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

    /** LCD lettering. */
    public static final int LCD_SEGMENT = 0, LCD_DOT = 1, LCD_MODERN = 2;

    /** One button on the faceplate. */
    public static final class Control {
        public final String id, label;
        public final Box box;

        Control(String id, String label, Box box) {
            this.id = id;
            this.label = label;
            this.box = box;
        }
    }

    public static final String POWER = "power", SOURCE = "source", PREV = "prev", PLAY = "play", NEXT = "next",
            DISPLAY = "display", MENU = "menu", VOL_DOWN = "vol_down", VOL_UP = "vol_up", PRESET = "preset";

    public final String id, name, era;
    public final int faceTop, faceBottom, bezel, lcdBack, lcdFrame, buttonFace, buttonEdge, printColor;
    public final int lcdStyle;
    public final float corner;
    /** The volume knob, or null when the skin uses VOL buttons. */
    public Box knob;
    public Box lcd, lcdText;
    /** Six preset buttons share this strip, or null for none. */
    public Box presets;
    /** A decorative tape or CD slot, or null. */
    public Box slot;
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
        controls.add(new Control(id, label, new Box(l, t, r, b)));
        return this;
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

    /** The six preset button boxes, or an empty list. */
    public List<Box> presetBoxes() {
        List<Box> out = new ArrayList<>();
        if (presets == null) return out;
        float gap = presets.width() * 0.015f;
        float w = (presets.width() - gap * 5) / 6f;
        for (int i = 0; i < 6; i++) {
            float l = presets.left + i * (w + gap);
            out.add(new Box(l, presets.top, l + w, presets.bottom));
        }
        return out;
    }

    /** Everything a logo must never cover: buttons, knob, presets and the track text. */
    public List<Box> keepClear() {
        List<Box> out = new ArrayList<>();
        for (Control c : controls) out.add(c.box);
        out.addAll(presetBoxes());
        if (knob != null) out.add(knob);
        if (lcdText != null) out.add(lcdText);
        return out;
    }
}
