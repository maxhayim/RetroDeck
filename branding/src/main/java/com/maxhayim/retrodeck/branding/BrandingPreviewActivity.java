package com.maxhayim.retrodeck.branding;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.StereoState;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Ui;
import com.maxhayim.retrodeck.core.Visualizers;

/**
 * The whole branded experience at full screen: startup, then the stereo. PWR shows the standby
 * screen, DISP cycles visualizers and MENU closes the preview.
 */
public class BrandingPreviewActivity extends Activity {

    private StereoView stereo;
    private StartupView startup;
    private VehicleProfile profile;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ProfileStore store = ProfileStore.get(this);
        profile = store.active();
        FrameLayout root = new FrameLayout(this);
        stereo = new StereoView(this);
        final StereoState s = Previews.state();
        stereo.setState(s);
        stereo.setOverlay(new BrandingRenderer(store, profile));
        Previews.apply(stereo, profile);
        stereo.setListener(new StereoView.Listener() {
            @Override
            public void onControl(String control, int index) {
                switch (control) {
                    case Skin.POWER:
                        s.power = !s.power;
                        break;
                    case Skin.PLAY:
                        s.playing = !s.playing;
                        break;
                    case Skin.DISPLAY:
                        stereo.setVisualizer(profile.visualizer = Visualizers.next(profile.visualizer));
                        break;
                    case Skin.MENU:
                        finish();
                        return;
                    default:
                        break;
                }
                stereo.invalidate();
            }

            @Override
            public void onVolume(int volume) {
                s.volume = volume;
                stereo.invalidate();
            }
        });
        root.addView(stereo, new FrameLayout.LayoutParams(-1, -1));
        startup = new StartupView(this, store);
        root.addView(startup, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        VehicleProfile p = profile.copy();
        // The preview always plays the animation, even when it's switched off for launches.
        p.startup.enabled = true;
        startup.play(p, () -> Toast.makeText(this, "Preview: PWR for standby, MENU to close", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }
}
