package com.maxhayim.retrodeck;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.FrameLayout;

import com.maxhayim.retrodeck.branding.Animations;
import com.maxhayim.retrodeck.branding.BrandingRenderer;
import com.maxhayim.retrodeck.branding.ProfileStore;
import com.maxhayim.retrodeck.branding.StartupView;
import com.maxhayim.retrodeck.branding.VehicleProfile;
import com.maxhayim.retrodeck.core.ButtonMap;
import com.maxhayim.retrodeck.core.Pickers;
import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.StereoState;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Ui;
import com.maxhayim.retrodeck.core.Visualizers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The stereo. The active vehicle profile decides its skin, colours, visualizer and startup. */
public class MainActivity extends Activity implements StereoView.Listener {

    private static final int PICK_MUSIC = 1, HEAR_MUSIC = 2;
    private static final String[] AUDIO = {"audio/*"};
    private static final String SONG = ".+\\.(mp3|m4a|aac|ogg|oga|opus|flac|wav|mid|midi|3gp|mkv|webm)";

    private ProfileStore store;
    private VehicleProfile profile;
    private StereoView stereo;
    private StartupView startup;
    private BrandingRenderer renderer;
    private Player player;
    private final LiveLevels levels = new LiveLevels();
    private ButtonMap buttons = ButtonMap.defaults();
    private long lastInput = android.os.SystemClock.uptimeMillis();
    private int swallowUp = -1;
    private final StereoState state = new StereoState();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable onProfileChanged = this::applyProfile;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            player.tick();
            checkIdle();
            main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = ProfileStore.get(this);
        profile = store.active();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        stereo = new StereoView(this);
        renderer = new BrandingRenderer(store, profile);
        stereo.setOverlay(renderer);
        stereo.setState(state);
        stereo.setListener(this);
        root.addView(stereo, new FrameLayout.LayoutParams(-1, -1));
        startup = new StartupView(this, store);
        startup.setVisibility(android.view.View.GONE);
        root.addView(startup, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        stereo.setLevels(levels);
        player = new Player(this, state, () -> {
            stereo.invalidate();
            followMusic();
        });
        applyProfile();
        store.addListener(onProfileChanged);
        // The stereo is built and running underneath, so a tap on the animation reaches it at once.
        if (saved == null) {
            startup.play(profile, null);
            MelodyBox.play(this, MelodyBox.start(this));
        }
        // The head unit switching its screen off is usually the car being switched off.
        registerReceiver(screenOff, new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    private void applyProfile() {
        profile = store.active();
        renderer.setProfile(profile);
        stereo.setSkin(Skins.get(profile.skin));
        stereo.setIllumination(profile.illuminationColor());
        stereo.setAccent(profile.accentColor);
        stereo.setVisualizerColor(profile.visualizerColor);
        stereo.setScreenColor(profile.screenColor);
        stereo.setBodyColor(profile.bodyColor);
        stereo.setKeyLighting(profile.keyLighting);
        stereo.setVisualizer(profile.visualizer);
        Animations.apply(stereo, store, profile);
        stereo.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        // Time spent in Settings doesn't count as the stereo sitting idle.
        lastInput = android.os.SystemClock.uptimeMillis();
        buttons = ButtonsActivity.load(this);
        applyProfile();
        main.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        store.removeListener(onProfileChanged);
        unregisterReceiver(screenOff);
        player.stop();
        levels.release();
    }

    /** Points the visualizer at the song that's playing; the first time, asks to read it. */
    private void followMusic() {
        if (player == null) return;
        int s = player.session();
        if (s == levels.session()) return;
        if (s != 0 && Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            levels.attach(0);
            askToHearMusic();
            return;
        }
        levels.attach(s);
    }

    /** Once: explains, then asks Android for the permission the Visualizer needs. */
    private void askToHearMusic() {
        SharedPreferences p = getSharedPreferences("levels", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT < 23 || p.getBoolean("asked", false)) return;
        p.edit().putBoolean("asked", true).apply();
        Ui.dialog(this).setTitle("Visualizer")
                .setMessage("To make the visualizer move with your music, RetroDeck needs to read the sound it plays. "
                        + "Android calls this \"record audio\". RetroDeck never uses the microphone, and nothing is "
                        + "recorded, saved or sent anywhere.\n\nWithout it, the visualizer moves on its own.")
                .setPositiveButton("Continue", (d, w) -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, HEAR_MUSIC))
                .setNegativeButton("Not now", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == HEAR_MUSIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            levels.attach(player.session());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }

    @Override
    public void onControl(String control, int index) {
        perform(ButtonMap.forControl(control, index));
    }

    @Override
    public void onHeld(String control, int index) {
        if (index > 0) player.savePreset(index);
        else if (Skin.DISPLAY.equals(control)) stereo.setFullscreen(true);
    }

    /** What every on-screen key and mapped head-unit key comes to. */
    private void perform(String action) {
        if (action == null) return;
        if (!state.power && !ButtonMap.POWER.equals(action)) {
            // Any key wakes a stereo in standby, as on a real one.
            state.power = true;
            MelodyBox.play(this, MelodyBox.start(this));
            stereo.invalidate();
            return;
        }
        int preset = ButtonMap.preset(action);
        if (preset > 0) {
            player.jump(preset);
            stereo.invalidate();
            return;
        }
        switch (action) {
            case ButtonMap.POWER:
                state.power = !state.power;
                if (!state.power) {
                    player.pause();
                    stereo.setFullscreen(false);
                }
                MelodyBox.play(this, state.power ? MelodyBox.start(this) : MelodyBox.stop(this));
                break;
            case ButtonMap.FULLSCREEN:
                stereo.setFullscreen(!stereo.isFullscreen());
                break;
            case ButtonMap.SOURCE:
                chooseMusic();
                break;
            case ButtonMap.PLAY_PAUSE:
                player.toggle();
                break;
            case ButtonMap.PLAY:
                player.play(true);
                break;
            case ButtonMap.PAUSE:
                player.play(false);
                break;
            case ButtonMap.NEXT:
                player.next();
                break;
            case ButtonMap.PREV:
                player.prev();
                break;
            case ButtonMap.VOL_UP:
                player.setVolume(Math.min(StereoState.MAX_VOLUME, state.volume + 1));
                break;
            case ButtonMap.VOL_DOWN:
                player.setVolume(Math.max(0, state.volume - 1));
                break;
            case ButtonMap.MUTE:
                player.toggleMute();
                break;
            case ButtonMap.DISPLAY:
                // DISP cycles visualizers and remembers the choice in the vehicle profile.
                profile.visualizer = Visualizers.next(profile.visualizer);
                stereo.setVisualizer(profile.visualizer);
                store.save(profile);
                break;
            case ButtonMap.MENU:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            default:
                break;
        }
        stereo.invalidate();
    }

    @Override
    public void onVolume(int volume) {
        player.setVolume(volume);
    }

    // Head-unit and steering-wheel keys, as set in Settings → Buttons.

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String action = buttons.action(keyCode);
        if (action == null) return super.onKeyDown(keyCode, event);
        if (ButtonMap.preset(action) > 0) {
            // Decided on release: a short press plays, a long one saves.
            event.startTracking();
            return true;
        }
        boolean volume = ButtonMap.VOL_UP.equals(action) || ButtonMap.VOL_DOWN.equals(action);
        if (event.getRepeatCount() == 0 || volume) perform(action);
        return true;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        int preset = ButtonMap.preset(buttons.action(keyCode));
        if (preset == 0) return super.onKeyLongPress(keyCode, event);
        if (state.power) player.savePreset(preset);
        stereo.invalidate();
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        String action = buttons.action(keyCode);
        if (action == null) return super.onKeyUp(keyCode, event);
        if (ButtonMap.preset(action) > 0 && event.isTracking() && !event.isCanceled()) perform(action);
        return true;
    }

    private static boolean isVolumeKey(int code) {
        return code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    // The full-screen visualizer comes on by itself after a while with no touch or key, while music plays.

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        lastInput = android.os.SystemClock.uptimeMillis();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        lastInput = android.os.SystemClock.uptimeMillis();
        int code = event.getKeyCode();
        // Checked here, before the screen sees the key: in full screen, the first press of a key
        // other than a media key only brings the stereo back, as a touch does. After a touch, Android
        // keeps the press of OK and the arrows to leave touch mode, so only their release arrives.
        if (stereo.isFullscreen()) {
            String action = buttons.action(code);
            if (!ButtonMap.keepsFullscreen(action) && !ButtonMap.FULLSCREEN.equals(action) && !isVolumeKey(code)) {
                stereo.setFullscreen(false);
                if (event.getAction() == KeyEvent.ACTION_DOWN) swallowUp = code;
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_UP && code == swallowUp) {
            swallowUp = -1;
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private final android.content.BroadcastReceiver screenOff = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            if (state.power) MelodyBox.play(MainActivity.this, MelodyBox.stop(MainActivity.this));
        }
    };

    private void checkIdle() {
        int after = DisplaySettings.fullscreenAfter(this);
        if (after <= 0 || stereo.isFullscreen() || !state.power || !state.playing || startup.isPlaying()) return;
        if (android.os.SystemClock.uptimeMillis() - lastInput >= after * 1000L) stereo.setFullscreen(true);
    }

    @Override
    public void onBackPressed() {
        if (stereo.isFullscreen()) {
            stereo.setFullscreen(false);
            return;
        }
        if (startup.isPlaying()) startup.skip();
        else super.onBackPressed();
    }

    /**
     * Songs from the music folder, or the file picker. Some head units and Android TV builds have no
     * working picker, so the folder is always offered, as for logos.
     */
    private void chooseMusic() {
        final File folder = musicFolder();
        File[] found = folder.listFiles((dir, n) -> n.toLowerCase(Locale.US).matches(SONG));
        final List<Uri> songs = new ArrayList<>();
        if (found != null) {
            Arrays.sort(found);
            for (File f : found) songs.add(Uri.fromFile(f));
        }
        if (songs.isEmpty()) {
            Ui.dialog(this).setTitle("Add music")
                    .setMessage("Open the file picker, or copy songs into RetroDeck's music folder with a file manager, "
                            + "a USB cable or adb, then press SRC again:\n\n" + folder.getPath())
                    .setPositiveButton("File picker", (d, w) -> pick())
                    .setNegativeButton("OK", null)
                    .show();
            return;
        }
        String n = songs.size() == 1 ? "1 song" : songs.size() + " songs";
        Ui.dialog(this).setTitle("Add music")
                .setItems(new String[]{"Add " + n + " from the music folder", "Choose with the file picker…"}, (d, w) -> {
                    if (w == 0) player.add(songs);
                    else pick();
                }).show();
    }

    private void pick() {
        if (!Pickers.open(this, AUDIO, PICK_MUSIC)) {
            Ui.dialog(this).setTitle("No file picker")
                    .setMessage("This device has no file picker for music. Copy songs into this folder with a file manager, "
                            + "a USB cable or adb, then press SRC again:\n\n" + musicFolder().getPath())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    /** Needs no storage permission; opened up so files can be copied in. */
    private File musicFolder() {
        File f = getExternalFilesDir("music");
        if (f == null) f = new File(getFilesDir(), "music");
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

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_MUSIC || result != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        for (Uri u : uris) {
            try {
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Picked through a file manager without lasting access; it plays this session.
            }
        }
        player.add(uris);
    }
}
