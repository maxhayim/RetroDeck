package com.maxhayim.retrodeck;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.maxhayim.retrodeck.core.Melody;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The melody box: a station-style chime when the stereo comes on, and, if chosen, another as it
 * goes off. A choice is a built-in melody's id, "file:" and a sound in the melody folder, or "off".
 */
final class MelodyBox {

    static final String OFF = "off";
    private static final String PREFS = "melody", START = "start", STOP = "stop", FILE = "file:";
    private static final String SOUND = ".+\\.(mp3|m4a|aac|ogg|oga|opus|flac|wav|mid|midi)";

    private static AudioTrack track;
    private static MediaPlayer media;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private MelodyBox() {}

    static String start(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(START, "departure");
    }

    /** Off unless chosen: the head unit may lose power before it finishes. */
    static String stop(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(STOP, OFF);
    }

    static void setStart(Context c, String choice) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(START, choice).apply();
    }

    static void setStop(Context c, String choice) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(STOP, choice).apply();
    }

    /** Your own chimes: sound files copied here. */
    static File folder(Context c) {
        File f = c.getExternalFilesDir("melody");
        if (f == null) f = new File(c.getFilesDir(), "melody");
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        f.setReadable(true, false);
        //noinspection ResultOfMethodCallIgnored
        f.setWritable(true, false);
        //noinspection ResultOfMethodCallIgnored
        f.setExecutable(true, false);
        return f;
    }

    /** Every choice, Off first: the built-in melodies, then files in the melody folder. */
    static List<String> choices(Context c) {
        List<String> out = new ArrayList<>();
        out.add(OFF);
        for (Melody m : Melody.all()) out.add(m.id);
        File[] files = folder(c).listFiles((d, n) -> n.toLowerCase(Locale.US).matches(SOUND));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) out.add(FILE + f.getName());
        }
        return out;
    }

    static String label(String choice) {
        if (choice == null || OFF.equals(choice)) return "Off";
        if (choice.startsWith(FILE)) return choice.substring(FILE.length());
        Melody m = Melody.get(choice);
        return m == null ? "Off" : m.name;
    }

    /** Plays a choice at the media volume; a new one cuts off the last. */
    static void play(final Context context, final String choice) {
        if (choice == null || OFF.equals(choice)) return;
        final Context c = context.getApplicationContext();
        stopPlaying();
        if (choice.startsWith(FILE)) {
            File f = new File(folder(c), choice.substring(FILE.length()));
            if (!f.isFile() || f.getName().contains("/")) return;
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mp.setDataSource(c, Uri.fromFile(f));
                mp.setOnCompletionListener(MediaPlayer::release);
                mp.prepare();
                mp.start();
                media = mp;
            } catch (Exception e) {
                Log.w("RetroDeck", "Can't play melody " + f, e);
            }
            return;
        }
        final Melody m = Melody.get(choice);
        if (m == null) return;
        // Rendering takes a moment on a slow head unit, so it's done off the main thread.
        new Thread(() -> {
            short[] pcm = m.render();
            main.post(() -> start(pcm));
        }, "melody").start();
    }

    @SuppressWarnings("deprecation")
    private static void start(short[] pcm) {
        stopPlaying();
        try {
            AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, Melody.RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
            t.write(pcm, 0, pcm.length);
            t.play();
            track = t;
            final AudioTrack done = t;
            main.postDelayed(() -> {
                if (track == done) stopPlaying();
            }, pcm.length * 1000L / Melody.RATE + 500);
        } catch (Exception e) {
            Log.w("RetroDeck", "Can't play melody", e);
        }
    }

    static void stopPlaying() {
        if (track != null) {
            try {
                track.stop();
                track.release();
            } catch (Exception ignored) {
                // Already gone.
            }
            track = null;
        }
        if (media != null) {
            try {
                media.release();
            } catch (Exception ignored) {
                // Already gone.
            }
            media = null;
        }
    }
}
