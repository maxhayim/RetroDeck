package com.maxhayim.retrodeck;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.audiofx.Visualizer;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.maxhayim.retrodeck.core.Ui;

import java.util.List;

/**
 * What RetroDeck finds on this head unit, on one page for a screenshot: the music apps and what their
 * media sessions say, whether the keys reach them, whether the visualizer hears anything, and how the
 * volume answers. Nothing identifying (no serial, MAC address, IP address or accounts).
 */
public class DiagnosticsActivity extends Activity {

    private static final int HEAR = 1;
    private static final String[] STREAMS = {"VOICE_CALL", "SYSTEM", "RING", "MUSIC", "ALARM", "NOTIFICATION",
            "BLUETOOTH_SCO", "SYSTEM_ENFORCED", "DTMF", "TTS", "ACCESSIBILITY"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private OtherApps others;
    private TextView left, right;
    private String visualizerResult = "not checked yet", volumeResult = "not tested yet", keyResult = "none sent yet";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        others = new OtherApps(this, () -> main.post(this::refresh));

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "Diagnostics", "Settings  ›  About  ·  take a screenshot or Share"));
        LinearLayout buttons = Ui.horizontal(this);
        buttons.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        addButton(buttons, "Refresh", true, this::refresh);
        addButton(buttons, "⏮ Prev", false, () -> sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "previous"));
        addButton(buttons, "⏯ Play/Pause", false, () -> sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "play/pause"));
        addButton(buttons, "⏭ Next", false, () -> sendKey(KeyEvent.KEYCODE_MEDIA_NEXT, "next"));
        addButton(buttons, "Test volume", false, this::testVolume);
        addButton(buttons, "Test visualizer", false, this::testVisualizer);
        addButton(buttons, "Share", false, this::share);
        android.widget.HorizontalScrollView bs = new android.widget.HorizontalScrollView(this);
        bs.addView(buttons);
        root.addView(bs);

        LinearLayout cols = Ui.horizontal(this);
        cols.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 14));
        cols.setGravity(0);
        left = mono();
        right = mono();
        cols.addView(left, Ui.weight(1));
        cols.addView(right, Ui.weight(1));
        root.addView(Ui.scroll(this, cols), new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addButton(LinearLayout row, String label, boolean primary, Runnable r) {
        row.addView(Ui.button(this, label, primary, v -> r.run()), Ui.margins(Ui.wrap(), this, 0, 0, 8, 0));
    }

    private TextView mono() {
        TextView t = Ui.text(this, "", 12, Ui.TEXT, false);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(0, 0, Ui.dp(this, 12), 0);
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        others.start();
        refresh();
        if (visualizerResult.startsWith("not checked")) main.postDelayed(this::testVisualizer, 300);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        others.stop();
    }

    private void refresh() {
        left.setText(leftReport());
        right.setText(rightReport());
    }

    // ---- the report ----

    private String leftReport() {
        StringBuilder b = new StringBuilder();
        b.append("DEVICE\n");
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "?";
        }
        b.append("RetroDeck      ").append(version).append('\n');
        b.append("Android        ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("Maker / model  ").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL).append('\n');
        b.append("Hardware       ").append(Build.HARDWARE).append(" · ").append(Build.BOARD).append('\n');
        b.append("Build          ").append(Build.DISPLAY).append('\n');
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        b.append("Screen         ").append(m.widthPixels).append('x').append(m.heightPixels).append(" @ ").append(m.densityDpi).append(" dpi\n");

        b.append("\nPERMISSIONS\n");
        b.append("Song titles    ");
        if (!OtherApps.titlesPossible()) b.append("not possible (needs Android 5)\n");
        else b.append(others.hasAccess() ? "ALLOWED" : "not allowed").append(" (Notification access")
                .append(getPackageManager().resolveActivity(OtherApps.accessSettings(), 0) == null ? ": NO SUCH SCREEN)\n" : ")\n");
        b.append("Record audio   ").append(canHear() ? "ALLOWED" : "not allowed").append(" (visualizer)\n");

        b.append("\nVISUALIZER (all the head unit plays)\n");
        b.append(visualizerResult).append('\n');

        b.append("\nVOLUME\n");
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        b.append("Music volume   ").append(audio.getStreamVolume(AudioManager.STREAM_MUSIC)).append(" of ").append(max).append('\n');
        if (Build.VERSION.SDK_INT >= 21) b.append("Fixed volume   ").append(audio.isVolumeFixed() ? "YES (apps can't change it)" : "no").append('\n');
        if (Build.VERSION.SDK_INT >= 23) b.append("Muted          ").append(audio.isStreamMute(AudioManager.STREAM_MUSIC) ? "yes" : "no").append('\n');
        b.append("Knob found     ").append(Player.volumeWayLabel(this)).append('\n');
        b.append("Test           ").append(volumeResult).append('\n');
        return b.toString();
    }

    private String rightReport() {
        StringBuilder b = new StringBuilder();
        b.append("PLAYING NOW\n");
        b.append("Music active   ").append(audio.isMusicActive() ? "YES (some app is playing)" : "no").append('\n');
        b.append("Followed       ").append(others.hasSession() ? others.app + " (" + others.packageName + ")"
                : others.playing ? "an app with no session" : "nothing").append('\n');
        b.append("Keys sent      ").append(keyResult).append('\n');

        b.append("\nMEDIA SESSIONS (apps that say what they play)\n");
        if (!OtherApps.titlesPossible()) b.append("needs Android 5\n");
        else if (!others.hasAccess()) b.append("allow Notification access to list them\n");
        else b.append(sessions());

        b.append("\nMUSIC APPS FOUND (listed in SRC)\n");
        List<OtherApps.App> apps = OtherApps.musicApps(this);
        if (apps.isEmpty()) b.append("none\n");
        for (OtherApps.App a : apps) b.append("• ").append(a.label).append("  ").append(a.packageName).append('\n');
        return b.toString();
    }

    @TargetApi(21)
    private String sessions() {
        StringBuilder b = new StringBuilder();
        try {
            MediaSessionManager m = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = m.getActiveSessions(new ComponentName(this, NowPlayingListener.class));
            if (list.isEmpty()) b.append("none (start the radio or a player, then Refresh)\n");
            for (MediaController c : list) {
                b.append("• ").append(OtherApps.label(this, c.getPackageName())).append("  ").append(c.getPackageName()).append('\n');
                PlaybackState s = c.getPlaybackState();
                b.append("    state  ").append(s == null ? "none" : stateName(s.getState())).append('\n');
                MediaMetadata md = c.getMetadata();
                String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
                String a = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                b.append("    title  ").append(t == null ? "(none)" : t).append('\n');
                b.append("    artist ").append(a == null ? "(none)" : a).append('\n');
                b.append("    keys   ").append(s == null ? "?" : actions(s.getActions())).append('\n');
            }
        } catch (SecurityException e) {
            b.append("refused: ").append(e.getMessage()).append('\n');
        }
        return b.toString();
    }

    private static String stateName(int s) {
        switch (s) {
            case 3: return "PLAYING";
            case 2: return "paused";
            case 1: return "stopped";
            case 6: return "buffering";
            case 7: return "error";
            case 0: return "none";
            default: return "state " + s;
        }
    }

    private static String actions(long a) {
        StringBuilder b = new StringBuilder();
        if ((a & 4) != 0) b.append("play ");
        if ((a & 2) != 0) b.append("pause ");
        if ((a & 512) != 0) b.append("play/pause ");
        if ((a & 32) != 0) b.append("next ");
        if ((a & 16) != 0) b.append("prev ");
        return b.length() == 0 ? "(none declared)" : b.toString().trim();
    }

    // ---- tests ----

    private void sendKey(int key, String name) {
        String to = others.hasSession() ? others.app + " through its session" : "as a media key (to the app that played last)";
        others.send(key);
        keyResult = name + " → " + to + "; Refresh to see if it changed";
        refresh();
        main.postDelayed(this::refresh, 1500);
    }

    /** One step down and back up, both ways RetroDeck can try, noting which streams moved. */
    private void testVolume() {
        int music = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        StringBuilder r = new StringBuilder();
        if (music > 0) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, music - 1, 0);
            boolean moved = audio.getStreamVolume(AudioManager.STREAM_MUSIC) != music;
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, music, 0);
            r.append("music volume ").append(moved ? "MOVES" : "doesn't move");
        } else {
            r.append("music volume is 0: turn it up a little and test again");
        }
        int[] before = streams();
        audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
        int[] after = streams();
        audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE, AudioManager.USE_DEFAULT_STREAM_TYPE, 0);
        StringBuilder movedStreams = new StringBuilder();
        for (int i = 0; i < before.length; i++) if (before[i] != after[i]) movedStreams.append(STREAMS[i]).append(' ');
        r.append("; the playing app's volume moved ").append(movedStreams.length() == 0 ? "nothing" : movedStreams.toString().trim());
        volumeResult = r.toString();
        refresh();
    }

    private int[] streams() {
        int[] v = new int[STREAMS.length];
        for (int i = 0; i < v.length; i++) {
            try {
                v[i] = audio.getStreamVolume(i);
            } catch (RuntimeException e) {
                v[i] = -1;
            }
        }
        return v;
    }

    private boolean canHear() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Listens to everything the head unit plays for two seconds. */
    private void testVisualizer() {
        if (!canHear()) {
            visualizerResult = "needs Record audio: asking";
            refresh();
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, HEAR);
            return;
        }
        final Visualizer v;
        try {
            v = new Visualizer(0);
            int[] range = Visualizer.getCaptureSizeRange();
            v.setCaptureSize(Math.max(range[0], Math.min(1024, range[1])));
            v.setEnabled(true);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            visualizerResult = "NOT AVAILABLE: " + e.getMessage() + "\n(the bars move on their own)";
            refresh();
            return;
        }
        visualizerResult = "listening for 2 seconds…" + (audio.isMusicActive() ? "" : " (play some music)");
        refresh();
        final byte[] wave = new byte[v.getCaptureSize()];
        final int[] loudest = {0};
        final int[] reads = {0};
        Runnable sample = new Runnable() {
            @Override
            public void run() {
                try {
                    if (v.getWaveForm(wave) == Visualizer.SUCCESS) {
                        for (byte x : wave) loudest[0] = Math.max(loudest[0], Math.abs((x & 0xFF) - 128));
                        reads[0]++;
                    }
                } catch (IllegalStateException ignored) {
                    // Released.
                }
                if (reads[0] < 20) {
                    main.postDelayed(this, 100);
                    return;
                }
                try {
                    v.setEnabled(false);
                    v.release();
                } catch (RuntimeException ignored) {
                    // Gone.
                }
                visualizerResult = loudest[0] > 2 ? "HEARS SOUND (level " + loudest[0] + " of 128): the bars follow the music"
                        : "silence" + (audio.isMusicActive() ? " while music plays: this head unit doesn't share its sound; the bars move on their own"
                        : " (nothing playing: play the radio and test again)");
                refresh();
            }
        };
        main.postDelayed(sample, 100);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != HEAR) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) testVisualizer();
        else {
            visualizerResult = "Record audio not allowed: the bars move on their own";
            refresh();
        }
    }

    private void share() {
        String text = "RetroDeck diagnostics\n\n" + leftReport() + "\n" + rightReport();
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "RetroDeck diagnostics").putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "Share diagnostics"));
        } catch (RuntimeException e) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("RetroDeck diagnostics", text));
            android.widget.Toast.makeText(this, "Copied: nothing here to share to", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
