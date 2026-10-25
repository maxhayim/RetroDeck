package com.maxhayim.retrodeck.core;

/**
 * The places a skin can show vehicle branding. Skins declare the first five as boxes on their
 * faceplate; the startup and standby screens are full-screen, so their boxes are the same everywhere.
 */
public final class Zones {

    public static final String HEADER = "header";
    public static final String LCD = "lcd";
    public static final String FACEPLATE = "faceplate";
    public static final String CORNER = "corner";
    public static final String VISUALIZER = "visualizer";
    public static final String STARTUP = "startup";
    public static final String STANDBY = "standby";

    /** Every zone, in the order the settings show them. */
    public static final String[] ALL = {HEADER, LCD, FACEPLATE, CORNER, VISUALIZER, STARTUP, STANDBY};

    /** The bottom fifth of the startup screen is kept for the LCD greeting and the power-on lamps. */
    public static final Box STARTUP_BOX = new Box(0.12f, 0.06f, 0.88f, 0.80f);
    public static final Box STANDBY_BOX = new Box(0.25f, 0.14f, 0.75f, 0.62f);

    private Zones() {}

    public static String label(String zone) {
        switch (zone) {
            case HEADER: return "Radio display header";
            case LCD: return "LCD screen";
            case FACEPLATE: return "Faceplate";
            case CORNER: return "Corner badge";
            case VISUALIZER: return "Visualizer overlay";
            case STARTUP: return "Startup screen";
            case STANDBY: return "Standby screen";
            default: return zone;
        }
    }

    public static String shortLabel(String zone) {
        switch (zone) {
            case HEADER: return "Header";
            case LCD: return "LCD";
            case FACEPLATE: return "Faceplate";
            case CORNER: return "Corner";
            case VISUALIZER: return "Visualizer";
            case STARTUP: return "Startup";
            case STANDBY: return "Standby";
            default: return zone;
        }
    }

    /** True for the zones drawn on the stereo itself rather than on a full-screen black page. */
    public static boolean onFaceplate(String zone) {
        return !STARTUP.equals(zone) && !STANDBY.equals(zone);
    }
}
