package com.maxhayim.retrodeck;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.maxhayim.retrodeck.core.StereoState;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Plays the music files the user added, in order, and fills in what the stereo shows. */
final class Player {

    interface Listener {
        void onChanged();
    }

    private static final String PREFS = "player", KEY_TRACKS = "tracks", KEY_INDEX = "index";

    private final Context context;
    private final SharedPreferences prefs;
    private final AudioManager audio;
    private final List<Uri> tracks = new ArrayList<>();
    private int index;
    private MediaPlayer player;
    private final StereoState state;
    private final Listener listener;

    Player(Context c, StereoState state, Listener listener) {
        context = c.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.state = state;
        this.listener = listener;
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY_TRACKS, "[]"));
            for (int i = 0; i < a.length(); i++) tracks.add(Uri.parse(a.getString(i)));
        } catch (JSONException ignored) {
            // Start with an empty list.
        }
        index = Math.max(0, Math.min(prefs.getInt(KEY_INDEX, 0), tracks.size() - 1));
        state.volume = systemVolume();
        describe();
    }

    int count() {
        return tracks.size();
    }

    void add(List<Uri> uris) {
        boolean wasEmpty = tracks.isEmpty();
        for (Uri u : uris) if (!tracks.contains(u)) tracks.add(u);
        persist();
        if (wasEmpty && !tracks.isEmpty()) {
            index = 0;
            play();
        } else {
            describe();
        }
    }

    void clear() {
        stop();
        tracks.clear();
        index = 0;
        persist();
        describe();
    }

    void toggle() {
        if (tracks.isEmpty()) return;
        if (player != null && player.isPlaying()) {
            player.pause();
            state.playing = false;
        } else if (player != null) {
            player.start();
            state.playing = true;
        } else {
            play();
        }
        listener.onChanged();
    }

    void pause() {
        if (player != null && player.isPlaying()) player.pause();
        state.playing = false;
        listener.onChanged();
    }

    void next() {
        if (tracks.isEmpty()) return;
        index = (index + 1) % tracks.size();
        play();
    }

    void prev() {
        if (tracks.isEmpty()) return;
        // Like a real stereo: back to the start of the track unless it just began.
        if (player != null && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            return;
        }
        index = (index - 1 + tracks.size()) % tracks.size();
        play();
    }

    /** Preset buttons jump straight to tracks 1-6. */
    void jump(int number) {
        if (number < 1 || number > tracks.size()) return;
        index = number - 1;
        play();
    }

    void setVolume(int v) {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max / (float) StereoState.MAX_VOLUME), 0);
        state.volume = v;
        listener.onChanged();
    }

    /** Called every second to keep the clock and position current. */
    void tick() {
        if (player != null) {
            try {
                state.positionMs = player.getCurrentPosition();
            } catch (IllegalStateException ignored) {
                // Between tracks.
            }
        }
        int sys = systemVolume();
        // Follow volume changed with the head unit's own keys, but keep finer steps set here.
        if (Math.abs(sys - state.volume) > StereoState.MAX_VOLUME / Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))) {
            state.volume = sys;
        }
    }

    private int systemVolume() {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * StereoState.MAX_VOLUME / (float) max);
    }

    private void play() {
        stop();
        if (tracks.isEmpty()) return;
        prefs.edit().putInt(KEY_INDEX, index).apply();
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(context, tracks.get(index));
            mp.setOnCompletionListener(m -> next());
            mp.setOnErrorListener((m, what, extra) -> {
                state.playing = false;
                listener.onChanged();
                return true;
            });
            mp.prepare();
            mp.start();
            player = mp;
            state.playing = true;
        } catch (Exception e) {
            Log.w("RetroDeck", "Can't play " + tracks.get(index), e);
            mp.release();
            state.playing = false;
        }
        describe();
    }

    void stop() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
                // Already gone.
            }
            player = null;
        }
        state.playing = false;
    }

    private void describe() {
        if (tracks.isEmpty()) {
            state.title = "NO MEDIA";
            state.artist = "Press SRC to add music";
            state.track = 0;
            state.durationMs = state.positionMs = 0;
        } else {
            Uri u = tracks.get(index);
            state.track = index + 1;
            state.title = name(u);
            state.artist = "";
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(context, u);
                String t = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (t != null && !t.trim().isEmpty()) state.title = t.trim();
                if (a != null) state.artist = a.trim();
                state.durationMs = d == null ? 0 : Long.parseLong(d);
            } catch (RuntimeException ignored) {
                state.durationMs = 0;
            } finally {
                try {
                    r.release();
                } catch (Exception ignored) {
                    // Nothing to free.
                }
            }
            state.positionMs = 0;
        }
        listener.onChanged();
    }

    private String name(Uri u) {
        try (Cursor c = context.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null) return n.replaceFirst("\\.[A-Za-z0-9]{2,4}$", "");
            }
        } catch (RuntimeException ignored) {
            // Lost access to the file.
        }
        String s = u.getLastPathSegment();
        return s == null ? "Track " + (index + 1) : s.replaceFirst("\\.[A-Za-z0-9]{2,4}$", "");
    }

    private void persist() {
        JSONArray a = new JSONArray();
        for (Uri u : tracks) a.put(u.toString());
        prefs.edit().putString(KEY_TRACKS, a.toString()).putInt(KEY_INDEX, index).apply();
    }
}
