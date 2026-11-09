package com.maxhayim.retrodeck.branding;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Surface;
import android.view.View;

import com.maxhayim.retrodeck.core.KeyLights;
import com.maxhayim.retrodeck.core.StereoState;
import com.maxhayim.retrodeck.core.StereoView;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Records a silent video tour of a vehicle's theme, drawn by the same views as the head unit: the
 * startup animation, the stereo by day and by night with the song playing and its title scrolling, the
 * full-screen visualizer, and standby. Encoded as H.264 in an MP4, with no sound track.
 * <p>
 * Needs Android 6 (API 23) for drawing into the encoder. Frames are drawn on the main thread as the
 * tour plays in real time; the encoder is emptied on a thread of its own.
 */
@TargetApi(23)
public final class VideoTour {

    public interface Listener {
        /** How far the tour has got, 0 to 1. Main thread. */
        void onProgress(float done);

        /** The finished video, or null with a message. Main thread. */
        void onDone(File video, String error);
    }

    /** Scene lengths in ms; the startup scene lasts as long as the profile's startup animation. */
    static final int DAY_MS = 12_000, NIGHT_MS = 12_000, VISUALIZER_MS = 8_000, STANDBY_MS = 4_000;
    static final int FADE_MS = 400, STILL_STARTUP_MS = 3_000;
    private static final int FPS = 30;

    public static boolean supported() {
        return Build.VERSION.SDK_INT >= 23;
    }

    private final Context context;
    private final ProfileStore store;
    private final VehicleProfile profile;
    private final StereoState song;
    private final File out;
    private final Listener listener;

    private int w, h;
    private MediaCodec codec;
    private Surface surface;
    private MediaMuxer muxer;
    private Thread drain;
    private volatile boolean drainFailed;

    private long started;
    private final int startupMs;
    private final int totalMs;
    private StartupView startup;
    private StereoView day, night, visualizer, standby, shown;
    private boolean stopped;

    /** song is shown playing; null or blank uses the preview song. */
    public VideoTour(Context context, ProfileStore store, VehicleProfile profile, StereoState song, File out, Listener listener) {
        this.context = context;
        this.store = store;
        this.profile = profile;
        this.out = out;
        this.listener = listener;
        StereoState st = Previews.state();
        if (song != null && song.title != null && !song.title.trim().isEmpty() && !"NO MEDIA".equals(song.title)) {
            st.title = song.title;
            st.artist = song.artist == null ? "" : song.artist;
        }
        this.song = st;
        int s = new StartupTimeline(profile.startup).durationMs();
        startupMs = s > 0 ? s : STILL_STARTUP_MS;
        totalMs = startupMs + DAY_MS + NIGHT_MS + VISUALIZER_MS + STANDBY_MS;
    }

    public int durationMs() {
        return totalMs;
    }

    /** Starts recording; call on the main thread. */
    public void start() {
        try {
            open();
        } catch (Exception e) {
            release();
            listener.onDone(null, "This device's video encoder couldn't start: " + e.getMessage());
            return;
        }
        startup = new StartupView(context, store);
        layout(startup);
        day = stereo(KeyLights.OFF, false, true);
        night = stereo(KeyLights.ON, false, true);
        visualizer = stereo(KeyLights.OFF, true, true);
        standby = stereo(KeyLights.OFF, false, false);
        started = SystemClock.uptimeMillis();
        Choreographer.getInstance().postFrameCallback(frame);
    }

    /** Stops early; the video so far is kept. */
    public void stop() {
        if (!stopped) finish(null);
    }

    private StereoView stereo(String keys, boolean fullscreen, boolean power) {
        StereoView v = new StereoView(context);
        v.setOverlay(new BrandingRenderer(store, profile));
        Previews.apply(v, profile);
        v.setKeyLighting(keys);
        v.setFullscreen(fullscreen);
        StereoState st = copy(song);
        st.power = power;
        v.setState(st);
        layout(v);
        return v;
    }

    private static StereoState copy(StereoState s) {
        StereoState c = new StereoState();
        c.playing = s.playing;
        c.source = s.source;
        c.title = s.title;
        c.artist = s.artist;
        c.track = s.track;
        c.volume = s.volume;
        c.positionMs = s.positionMs;
        c.durationMs = s.durationMs;
        return c;
    }

    private void layout(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
    }

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (stopped) return;
            long t = SystemClock.uptimeMillis() - started;
            if (t >= totalMs || drainFailed) {
                finish(drainFailed ? "The video encoder stopped." : null);
                return;
            }
            try {
                Canvas c = surface.lockHardwareCanvas();
                try {
                    drawAt(c, t);
                } finally {
                    surface.unlockCanvasAndPost(c);
                }
            } catch (RuntimeException e) {
                finish("Couldn't draw the video: " + e.getMessage());
                return;
            }
            listener.onProgress(t / (float) totalMs);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    /** The tour at t ms. */
    private void drawAt(Canvas c, long t) {
        c.drawColor(0xFF000000);
        if (t < startupMs) {
            // The startup animation fades out into the stereo, as on the head unit.
            float exit = peekExit(t);
            int save = c.save();
            if (exit > 0) day.draw(c);
            c.saveLayerAlpha(0, 0, w, h, Math.round(255 * (1 - exit)));
            startup.drawAt(c, profile, t);
            c.restoreToCount(save);
            return;
        }
        long in = t - startupMs;
        StereoView v;
        long len;
        if (in < DAY_MS) {
            v = day;
            len = DAY_MS;
        } else if ((in -= DAY_MS) < NIGHT_MS) {
            v = night;
            len = NIGHT_MS;
        } else if ((in -= NIGHT_MS) < VISUALIZER_MS) {
            v = visualizer;
            len = VISUALIZER_MS;
        } else {
            in -= VISUALIZER_MS;
            v = standby;
            len = STANDBY_MS;
        }
        if (v != shown) {
            // Each scene's title scrolls from its beginning.
            shown = v;
            v.restartScroll();
        }
        v.draw(c);
        // Through black between scenes; the day scene follows the startup without one.
        long left = len - in;
        float dark = 0;
        if (v != day && in < FADE_MS) dark = 1 - in / (float) FADE_MS;
        if (left < FADE_MS) dark = Math.max(dark, 1 - left / (float) FADE_MS);
        if (dark > 0) c.drawColor(Math.round(255 * dark) << 24);
    }

    private float peekExit(long t) {
        StartupTimeline tl = new StartupTimeline(profile.startup);
        if (tl.durationMs() <= 0) {
            // A still welcome screen: fade it out over the last moments.
            long left = startupMs - t;
            return left < FADE_MS * 2 ? 1 - left / (float) (FADE_MS * 2) : 0;
        }
        return tl.at(t).exit;
    }

    // ---- encoding ----

    private void open() throws IOException {
        // Full HD on the first encoder that can, otherwise 720p on any. The first H.264 encoder listed
        // is often a software one limited to 720p, with a hardware one further down.
        MediaCodecInfo info = encoder(1920, 1080);
        w = 1920;
        h = 1080;
        if (info == null) {
            info = encoder(1280, 720);
            w = 1280;
            h = 720;
        }
        if (info == null) throw new IOException("no H.264 encoder");
        MediaFormat f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_BIT_RATE, w >= 1920 ? 8_000_000 : 5_000_000);
        f.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        codec = MediaCodec.createByCodecName(info.getName());
        codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = codec.createInputSurface();
        codec.start();
        muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        drain = new Thread(this::drainLoop, "video-tour");
        drain.start();
    }

    /** An H.264 encoder that takes width x height, or null. */
    private static MediaCodecInfo encoder(int width, int height) {
        for (MediaCodecInfo i : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!i.isEncoder()) continue;
            for (String t : i.getSupportedTypes()) {
                if (!t.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC)) continue;
                MediaCodecInfo.VideoCapabilities caps = i.getCapabilitiesForType(t).getVideoCapabilities();
                if (caps != null && caps.isSizeSupported(width, height)) return i;
            }
        }
        return null;
    }

    private void drainLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int track = -1;
        boolean muxing = false;
        try {
            while (true) {
                int i = codec.dequeueOutputBuffer(info, 10_000);
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) continue;
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxing = true;
                    continue;
                }
                if (i < 0) continue;
                ByteBuffer buf = codec.getOutputBuffer(i);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0 && muxing && buf != null) {
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    muxer.writeSampleData(track, buf, info);
                }
                codec.releaseOutputBuffer(i, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
        } catch (RuntimeException e) {
            drainFailed = true;
        }
    }

    private void finish(String error) {
        stopped = true;
        String err = error;
        try {
            codec.signalEndOfInputStream();
            drain.join(10_000);
        } catch (Exception e) {
            if (err == null) err = "The video didn't finish: " + e.getMessage();
        }
        if (drainFailed && err == null) err = "The video encoder stopped.";
        release();
        listener.onDone(err == null && out.length() > 0 ? out : null, err == null && out.length() == 0 ? "The video is empty." : err);
    }

    private void release() {
        try {
            if (codec != null) {
                codec.stop();
                codec.release();
            }
        } catch (RuntimeException ignored) {
            // Already stopped.
        }
        codec = null;
        if (surface != null) surface.release();
        surface = null;
        try {
            if (muxer != null) {
                muxer.stop();
                muxer.release();
            }
        } catch (RuntimeException ignored) {
            // Never started: nothing was written.
        }
        muxer = null;
    }
}
