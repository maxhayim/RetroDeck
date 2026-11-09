package com.maxhayim.retrodeck;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Music playing in other apps: the head unit's radio, a music player, Bluetooth audio, CarPlay and so
 * on. Found by itself:
 * <ul>
 * <li>with notification access (Android 5+), the playing app's name, title, artist and state, from the
 * media session it publishes, and its own play, pause, next and previous;</li>
 * <li>without it, or for an app that publishes no session, only that something is playing
 * ({@link AudioManager#isMusicActive()}), and keys sent as media keys, which the app that played last
 * gets.</li>
 * </ul>
 */
final class OtherApps {

    interface Listener {
        void onChanged();
    }

    private final Context context;
    private final AudioManager audio;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Object sessions; // MediaSessionManager, kept untyped for Android 4.4
    private Object listenerToken;
    private Object controller; // MediaController
    private Object callback; // MediaController.Callback

    /** What's known of the other app's music. Blank when unknown. */
    String app = "", packageName = "", title = "", artist = "";
    boolean playing;
    long positionMs, durationMs;
    /** Last time music from another app was seen playing (uptime ms), or 0. */
    long heardAt;
    private int activeTicks;

    OtherApps(Context c, Listener listener) {
        context = c.getApplicationContext();
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
    }

    /** True when Android lets RetroDeck read other apps' media sessions. */
    boolean hasAccess() {
        String on = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return on != null && on.contains(new ComponentName(context, NowPlayingListener.class).flattenToString());
    }

    /** Whether reading titles is possible at all on this Android. */
    static boolean titlesPossible() {
        return Build.VERSION.SDK_INT >= 21;
    }

    static Intent accessSettings() {
        return new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Starts following; safe to call again, e.g. after access was granted. */
    void start() {
        if (!titlesPossible() || !hasAccess() || sessions != null) return;
        startSessions();
    }

    void stop() {
        if (Build.VERSION.SDK_INT >= 21) stopSessions();
    }

    /** True when there's a session to show or control. */
    boolean hasSession() {
        return controller != null;
    }

    /**
     * Called every second: notices music in other apps even without a session to read, since
     * {@link AudioManager#isMusicActive()} is true for any app playing.
     */
    void tick(boolean ownPlaying) {
        if (controller == null) {
            // A few seconds in a row, so RetroDeck's own startup chime doesn't count.
            activeTicks = !ownPlaying && audio.isMusicActive() ? activeTicks + 1 : 0;
            boolean active = activeTicks >= 3;
            if (active != playing) {
                playing = active;
                listener.onChanged();
            }
        } else if (Build.VERSION.SDK_INT >= 21) {
            readPosition();
        }
        if (playing) heardAt = SystemClock.uptimeMillis();
    }

    /** Sends a key to the other app: through its session when there is one, else as a media key. */
    void send(int keyCode) {
        if (Build.VERSION.SDK_INT >= 21 && controller != null && sendToSession(keyCode)) return;
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    // ---- sessions (Android 5+) ----

    @TargetApi(21)
    private void startSessions() {
        MediaSessionManager m = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        ComponentName me = new ComponentName(context, NowPlayingListener.class);
        try {
            MediaSessionManager.OnActiveSessionsChangedListener l = list -> pick(list);
            m.addOnActiveSessionsChangedListener(l, me, main);
            sessions = m;
            listenerToken = l;
            pick(m.getActiveSessions(me));
        } catch (SecurityException e) {
            // Access was turned off again.
            sessions = null;
        }
    }

    @TargetApi(21)
    private void stopSessions() {
        if (sessions != null && listenerToken != null) {
            ((MediaSessionManager) sessions).removeOnActiveSessionsChangedListener(
                    (MediaSessionManager.OnActiveSessionsChangedListener) listenerToken);
        }
        sessions = null;
        listenerToken = null;
        follow(null);
    }

    /** Follows the session that's playing, or else the most recent one; never RetroDeck's own. */
    @TargetApi(21)
    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                if (context.getPackageName().equals(c.getPackageName())) continue;
                if (best == null) best = c;
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
            }
        }
        follow(best);
    }

    @TargetApi(21)
    private void follow(MediaController c) {
        MediaController old = (MediaController) controller;
        if (old != null && c != null && old.getSessionToken().equals(c.getSessionToken())) return;
        if (old != null && callback != null) old.unregisterCallback((MediaController.Callback) callback);
        controller = c;
        callback = null;
        if (c == null) {
            app = packageName = title = artist = "";
            playing = false;
            listener.onChanged();
            return;
        }
        MediaController.Callback cb = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                read();
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                read();
            }

            @Override
            public void onSessionDestroyed() {
                if (sessions != null) {
                    try {
                        pick(((MediaSessionManager) sessions).getActiveSessions(
                                new ComponentName(context, NowPlayingListener.class)));
                    } catch (SecurityException e) {
                        follow(null);
                    }
                }
            }
        };
        c.registerCallback(cb, main);
        callback = cb;
        packageName = c.getPackageName();
        app = label(context, packageName);
        read();
    }

    @TargetApi(21)
    private void read() {
        MediaController c = (MediaController) controller;
        if (c == null) return;
        MediaMetadata md = c.getMetadata();
        String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null && md != null) t = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String a = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null && md != null) a = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        title = t == null ? "" : t.trim();
        artist = a == null ? "" : a.trim();
        durationMs = md == null ? 0 : Math.max(0, md.getLong(MediaMetadata.METADATA_KEY_DURATION));
        PlaybackState s = c.getPlaybackState();
        playing = s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
        readPosition();
        // A paused session elsewhere shouldn't hide a playing one: look again.
        if (!playing && sessions != null) {
            try {
                List<MediaController> list = ((MediaSessionManager) sessions).getActiveSessions(
                        new ComponentName(context, NowPlayingListener.class));
                for (MediaController o : list) {
                    PlaybackState os = o.getPlaybackState();
                    if (!o.getPackageName().equals(context.getPackageName()) && os != null
                            && os.getState() == PlaybackState.STATE_PLAYING && !o.getSessionToken().equals(c.getSessionToken())) {
                        follow(o);
                        return;
                    }
                }
            } catch (SecurityException ignored) {
                // Access was turned off; keep what we have.
            }
        }
        listener.onChanged();
    }

    @TargetApi(21)
    private void readPosition() {
        MediaController c = (MediaController) controller;
        PlaybackState s = c == null ? null : c.getPlaybackState();
        if (s == null || s.getPosition() < 0) {
            positionMs = 0;
            return;
        }
        long p = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
            p += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        positionMs = durationMs > 0 ? Math.min(p, durationMs) : p;
    }

    @TargetApi(21)
    private boolean sendToSession(int keyCode) {
        MediaController.TransportControls t = ((MediaController) controller).getTransportControls();
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                t.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                t.pause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (playing) t.pause();
                else t.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                t.skipToNext();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                t.skipToPrevious();
                return true;
            default:
                return false;
        }
    }

    // ---- apps to switch to ----

    /** An app that plays music: its name and what opens it. */
    static final class App {
        final String label, packageName;
        final Intent open;

        App(String label, String packageName, Intent open) {
            this.label = label;
            this.packageName = packageName;
            this.open = open;
        }
    }

    /**
     * Apps on this head unit that play music, found by themselves: music players (Android's music
     * category or a media browser service, as Bluetooth audio and most players have), and the head
     * unit's radio, which seldom declares either, by its name.
     */
    static List<App> musicApps(Context c) {
        PackageManager pm = c.getPackageManager();
        List<String> found = new ArrayList<>();
        Intent music = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC);
        for (ResolveInfo r : pm.queryIntentActivities(music, 0)) found.add(r.activityInfo.packageName);
        for (ResolveInfo r : pm.queryIntentServices(new Intent("android.media.browse.MediaBrowserService"), 0)) {
            found.add(r.serviceInfo.packageName);
        }
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(launcher, 0)) {
            String pkg = r.activityInfo.packageName;
            CharSequence l = r.loadLabel(pm);
            if (isMusicName(pkg, l == null ? "" : l.toString())) found.add(pkg);
        }
        List<App> apps = new ArrayList<>();
        for (String pkg : found) {
            if (pkg.equals(c.getPackageName())) continue;
            boolean seen = false;
            for (App a : apps) seen |= a.packageName.equals(pkg);
            if (seen) continue;
            Intent open = pm.getLaunchIntentForPackage(pkg);
            if (open == null) continue;
            apps.add(new App(label(c, pkg), pkg, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    /** Radio, music, Bluetooth audio and similar, by package or name, in English and a few common words. */
    static boolean isMusicName(String pkg, String label) {
        String s = (pkg + " " + label).toLowerCase(Locale.US);
        String[] words = {"radio", "music", "fmradio", ".fm", " fm", "tuner", "btmusic", "bluetooth music", "a2dp",
                "player", "podcast", "audio", "usbmusic", "media"};
        for (String w : words) if (s.contains(w)) return !s.contains("video") && !s.contains("recorder");
        return false;
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo i = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(i);
            return l == null ? pkg : l.toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
