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
    public boolean muted;
    /** A short message in place of the artist line, such as "P1 SAVED", until noticeUntil (uptime ms). */
    public String notice;
    public long noticeUntil;

    public void notice(String s, long now) {
        notice = s;
        noticeUntil = now + 2500;
    }

    /** The second line: a notice while one is showing, otherwise the artist. */
    public String artistLine(long now) {
        if (notice != null && now < noticeUntil) return notice;
        return muted ? "MUTE" : artist;
    }
}
