package com.maxhayim.retrodeck;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
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

    private static final String PREFS = "player", KEY_TRACKS = "tracks", KEY_INDEX = "index", KEY_PRESETS = "presets";

    private final Context context;
    private final SharedPreferences prefs;
    private final AudioManager audio;
    private final List<Uri> tracks = new ArrayList<>();
    /** Presets 1-6: a favourite song each, or null. */
    private final Uri[] presets = new Uri[6];
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
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY_PRESETS, "[]"));
            for (int i = 0; i < a.length() && i < presets.length; i++) {
                String s = a.optString(i, "");
                presets[i] = s.isEmpty() ? null : Uri.parse(s);
            }
        } catch (JSONException ignored) {
            // No presets yet.
        }
        index = Math.max(0, Math.min(prefs.getInt(KEY_INDEX, 0), tracks.size() - 1));
        state.volume = systemVolume();
        describe();
    }

    int count() {
        return tracks.size();
    }

    /** The playing song's audio session, for the visualizer to follow, or 0. */
    int session() {
        return player == null ? 0 : player.getAudioSessionId();
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

    void play(boolean on) {
        if (on != state.playing) toggle();
    }

    /** A preset plays its favourite song, putting it back in the list if it was cleared. */
    void jump(int number) {
        if (number < 1 || number > presets.length) return;
        Uri u = presets[number - 1];
        if (u == null) {
            notice("P" + number + " EMPTY: HOLD");
            return;
        }
        int i = tracks.indexOf(u);
        if (i < 0) {
            tracks.add(u);
            i = tracks.size() - 1;
            persist();
        }
        index = i;
        play();
        notice("P" + number);
    }

    /** Holding a preset saves the song that's on, like a radio station. */
    void savePreset(int number) {
        if (number < 1 || number > presets.length) return;
        if (tracks.isEmpty()) {
            notice("NO SONG TO SAVE");
            return;
        }
        presets[number - 1] = tracks.get(index);
        JSONArray a = new JSONArray();
        for (Uri u : presets) a.put(u == null ? "" : u.toString());
        prefs.edit().putString(KEY_PRESETS, a.toString()).apply();
        notice("P" + number + " SAVED");
    }

    /** Android's own mute, so the volume is kept and the head unit's keys can undo it too. */
    @SuppressWarnings("deprecation")
    void toggleMute() {
        boolean mute = !state.muted;
        if (Build.VERSION.SDK_INT >= 23) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        } else {
            audio.setStreamMute(AudioManager.STREAM_MUSIC, mute);
        }
        state.muted = mute;
        listener.onChanged();
    }

    @SuppressWarnings("deprecation")
    private void unmute() {
        if (Build.VERSION.SDK_INT >= 23) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        else audio.setStreamMute(AudioManager.STREAM_MUSIC, false);
        state.muted = false;
    }

    /** Muted by RetroDeck, or by the head unit itself (as Android does when the volume reaches 0). */
    private boolean systemMuted() {
        return Build.VERSION.SDK_INT >= 23 && audio.isStreamMute(AudioManager.STREAM_MUSIC);
    }

    private void notice(String s) {
        state.notice(s, SystemClock.uptimeMillis());
        listener.onChanged();
    }

    void setVolume(int v) {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // Turning it up undoes a mute; some head units stay muted otherwise.
        if (v > 0 && (state.muted || systemMuted())) unmute();
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
        // Muted or unmuted with the head unit's own keys.
        if (Build.VERSION.SDK_INT >= 23) state.muted = systemMuted();
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
