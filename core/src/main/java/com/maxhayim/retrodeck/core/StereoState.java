package com.maxhayim.retrodeck.core;

/** What the stereo is showing. The app fills it in; the view only draws it. */
public final class StereoState {

    public static final int MAX_VOLUME = 30;

    public boolean power = true;
    public boolean playing;
    public String source = "MEDIA";
    public String title = "NO MEDIA";
    public String artist = "Press SRC to add music";
    /** 1-based track number, or 0 for none. */
    public int track;
    public int volume = 12;
    public long positionMs, durationMs;
}
