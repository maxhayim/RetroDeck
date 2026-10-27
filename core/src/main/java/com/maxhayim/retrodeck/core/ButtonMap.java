package com.maxhayim.retrodeck.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Which head-unit or steering-wheel key does what. Keys are Android key codes; actions are the
 * names below. Kept as plain text ("85=play_pause,87=next") so it needs nothing to read it back.
 */
public final class ButtonMap {

    public static final String PLAY_PAUSE = "play_pause", PLAY = "play", PAUSE = "pause", NEXT = "next", PREV = "prev",
            SOURCE = "source", DISPLAY = "display", POWER = "power", MENU = "menu", VOL_UP = "vol_up",
            VOL_DOWN = "vol_down", MUTE = "mute", PRESET = "preset_";

    /** Every action, in the order the chooser lists them. */
    public static final List<String> ACTIONS;
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(PLAY_PAUSE, "Play / pause");
        LABELS.put(PLAY, "Play");
        LABELS.put(PAUSE, "Pause");
        LABELS.put(NEXT, "Next track");
        LABELS.put(PREV, "Previous track");
        LABELS.put(VOL_UP, "Volume up");
        LABELS.put(VOL_DOWN, "Volume down");
        LABELS.put(MUTE, "Mute");
        for (int i = 1; i <= 6; i++) LABELS.put(PRESET + i, "Preset " + i + " (hold to save)");
        LABELS.put(SOURCE, "SRC: add music");
        LABELS.put(DISPLAY, "DISP: next visualizer");
        LABELS.put(POWER, "Power");
        LABELS.put(MENU, "Settings");
        ACTIONS = Collections.unmodifiableList(new ArrayList<>(LABELS.keySet()));
    }

    // Android key codes, so this class needs no Android to test.
    static final int KEY_1 = 8, KEY_MENU = 82, KEY_HEADSETHOOK = 79, KEY_PLAY_PAUSE = 85, KEY_STOP = 86, KEY_NEXT = 87,
            KEY_PREVIOUS = 88, KEY_MUTE = 91, KEY_PLAY = 126, KEY_PAUSE = 127;

    private final Map<Integer, String> keys = new TreeMap<>();

    public static ButtonMap defaults() {
        ButtonMap m = new ButtonMap();
        m.keys.put(KEY_PLAY_PAUSE, PLAY_PAUSE);
        m.keys.put(KEY_HEADSETHOOK, PLAY_PAUSE);
        m.keys.put(KEY_PLAY, PLAY);
        m.keys.put(KEY_PAUSE, PAUSE);
        m.keys.put(KEY_STOP, PAUSE);
        m.keys.put(KEY_NEXT, NEXT);
        m.keys.put(KEY_PREVIOUS, PREV);
        m.keys.put(KEY_MUTE, MUTE);
        m.keys.put(KEY_MENU, MENU);
        for (int i = 0; i < 6; i++) m.keys.put(KEY_1 + i, PRESET + (i + 1));
        return m;
    }

    /** Reads what {@link #toString()} wrote; anything it doesn't understand is left out. Null gives the defaults. */
    public static ButtonMap parse(String s) {
        if (s == null) return defaults();
        ButtonMap m = new ButtonMap();
        for (String pair : s.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                int code = Integer.parseInt(pair.substring(0, eq).trim());
                String action = pair.substring(eq + 1).trim();
                if (mappable(code) && LABELS.containsKey(action)) m.keys.put(code, action);
            } catch (NumberFormatException ignored) {
                // Skip it.
            }
        }
        return m;
    }

    /** Back and Home stay with Android, so there is always a way out. */
    public static boolean mappable(int code) {
        return code > 0 && code != 3 && code != 4 && code < 1000;
    }

    public String action(int code) {
        return keys.get(code);
    }

    public void set(int code, String action) {
        if (!mappable(code)) return;
        if (action == null || !LABELS.containsKey(action)) keys.remove(code);
        else keys.put(code, action);
    }

    /** The mapped key codes, lowest first. */
    public List<Integer> codes() {
        return new ArrayList<>(keys.keySet());
    }

    public static String label(String action) {
        String l = LABELS.get(action);
        return l == null ? "Nothing" : l;
    }

    /** The preset number of a preset action, or 0. */
    public static int preset(String action) {
        if (action == null || !action.startsWith(PRESET)) return 0;
        try {
            return Integer.parseInt(action.substring(PRESET.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The action an on-screen control performs. */
    public static String forControl(String control, int index) {
        switch (control) {
            case Skin.POWER: return POWER;
            case Skin.SOURCE: return SOURCE;
            case Skin.PLAY: return PLAY_PAUSE;
            case Skin.NEXT: return NEXT;
            case Skin.PREV: return PREV;
            case Skin.DISPLAY: return DISPLAY;
            case Skin.MENU: return MENU;
            case Skin.VOL_UP: return VOL_UP;
            case Skin.VOL_DOWN: return VOL_DOWN;
            case Skin.PRESET: return index >= 1 && index <= 6 ? PRESET + index : null;
            default: return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Integer, String> e : keys.entrySet()) {
            if (b.length() > 0) b.append(',');
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }
}
