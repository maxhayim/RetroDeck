package com.maxhayim.retrodeck.branding;

import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.StereoState;
import com.maxhayim.retrodeck.core.StereoView;

/** Sets up stereo previews in the branding screens. */
final class Previews {

    private Previews() {}

    /** Made-up track details, so previews look like a playing stereo. */
    static StereoState state() {
        StereoState s = new StereoState();
        s.playing = true;
        s.source = "MEDIA";
        s.title = "Night Drive";
        s.artist = "RetroDeck preview";
        s.track = 3;
        s.volume = 14;
        s.positionMs = 83_000;
        s.durationMs = 214_000;
        return s;
    }

    static void apply(StereoView v, VehicleProfile p) {
        v.setSkin(Skins.get(p.skin));
        v.setIllumination(p.illuminationColor());
        v.setAccent(p.accentColor);
        v.setVisualizerColor(p.visualizerColor);
        v.setScreenColor(p.screenColor);
        v.setBodyColor(p.bodyColor);
        v.setKeyLighting(p.keyLighting);
        v.setVisualizer(p.visualizer);
        Animations.apply(v, ProfileStore.get(v.getContext()), p);
        v.invalidate();
    }
}
