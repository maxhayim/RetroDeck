package com.maxhayim.retrodeck;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.FrameLayout;

import com.maxhayim.retrodeck.branding.BrandingRenderer;
import com.maxhayim.retrodeck.branding.ProfileStore;
import com.maxhayim.retrodeck.branding.StartupView;
import com.maxhayim.retrodeck.branding.VehicleProfile;
import com.maxhayim.retrodeck.core.Pickers;
import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.StereoState;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Ui;
import com.maxhayim.retrodeck.core.Visualizers;

import java.util.ArrayList;
import java.util.List;

/** The stereo. The active vehicle profile decides its skin, colours, visualizer and startup. */
public class MainActivity extends Activity implements StereoView.Listener {

    private static final int PICK_MUSIC = 1;
    private static final String[] AUDIO = {"audio/*"};

    private ProfileStore store;
    private VehicleProfile profile;
    private StereoView stereo;
    private StartupView startup;
    private BrandingRenderer renderer;
    private Player player;
    private final StereoState state = new StereoState();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable onProfileChanged = this::applyProfile;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            player.tick();
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

        player = new Player(this, state, () -> stereo.invalidate());
        applyProfile();
        store.addListener(onProfileChanged);
        // The stereo is built and running underneath, so a tap on the animation reaches it at once.
        if (saved == null) startup.play(profile, null);
    }

    private void applyProfile() {
        profile = store.active();
        renderer.setProfile(profile);
        stereo.setSkin(Skins.get(profile.skin));
        stereo.setIllumination(profile.illuminationColor());
        stereo.setAccent(profile.accentColor);
        stereo.setKeyLighting(profile.keyLighting);
        stereo.setVisualizer(profile.visualizer);
        stereo.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
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
        player.stop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }

    @Override
    public void onControl(String control, int index) {
        switch (control) {
            case Skin.POWER:
                state.power = !state.power;
                if (!state.power) player.pause();
                break;
            case Skin.SOURCE:
                Pickers.open(this, AUDIO, PICK_MUSIC);
                break;
            case Skin.PLAY:
                player.toggle();
                break;
            case Skin.NEXT:
                player.next();
                break;
            case Skin.PREV:
                player.prev();
                break;
            case Skin.PRESET:
                player.jump(index);
                break;
            case Skin.VOL_UP:
                player.setVolume(Math.min(StereoState.MAX_VOLUME, state.volume + 1));
                break;
            case Skin.VOL_DOWN:
                player.setVolume(Math.max(0, state.volume - 1));
                break;
            case Skin.DISPLAY:
                // DISP cycles visualizers and remembers the choice in the vehicle profile.
                profile.visualizer = Visualizers.next(profile.visualizer);
                stereo.setVisualizer(profile.visualizer);
                store.save(profile);
                break;
            case Skin.MENU:
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Steering-wheel and head-unit media keys.
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                player.toggle();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                player.next();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                player.prev();
                return true;
            case KeyEvent.KEYCODE_MENU:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onBackPressed() {
        if (startup.isPlaying()) startup.skip();
        else super.onBackPressed();
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
