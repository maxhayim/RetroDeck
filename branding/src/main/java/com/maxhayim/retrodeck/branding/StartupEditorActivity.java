package com.maxhayim.retrodeck.branding;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.maxhayim.retrodeck.core.AspectFrame;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Ui;

import java.util.Locale;

/** Chooses the startup animation, its length and its steps, with a preview that plays each change. */
public class StartupEditorActivity extends Activity {

    private ProfileStore store;
    private VehicleProfile draft;
    private StartupView startup;
    private StereoView stereo;
    private LinearLayout controls;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ProfileStore.get(this);
        draft = store.active();

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "Startup Animation", draft.displayName()));
        LinearLayout body = Ui.horizontal(this);
        body.setGravity(0);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        controls = Ui.vertical(this);
        controls.setBackgroundColor(Ui.PANEL);
        body.addView(Ui.scroll(this, controls), Ui.weight(0.42f));

        LinearLayout right = Ui.vertical(this);
        right.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        AspectFrame frame = new AspectFrame(this, 1024f / 600f);
        stereo = new StereoView(this);
        stereo.setState(Previews.state());
        stereo.setOverlay(new BrandingRenderer(store, draft));
        Previews.apply(stereo, draft);
        frame.addView(stereo, new FrameLayout.LayoutParams(-1, -1));
        startup = new StartupView(this, store);
        startup.setVisibility(View.GONE);
        frame.addView(startup, new FrameLayout.LayoutParams(-1, -1));
        right.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = Ui.horizontal(this);
        buttons.setPadding(0, Ui.dp(this, 10), 0, 0);
        buttons.addView(Ui.button(this, "Play preview", true, v -> play()));
        right.addView(buttons);
        body.addView(right, Ui.weight(0.58f));

        LinearLayout footer = Ui.horizontal(this);
        footer.setBackgroundColor(Ui.PANEL);
        footer.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        footer.addView(Ui.button(this, "Cancel", false, v -> finish()));
        footer.addView(Ui.button(this, "Save", true, v -> {
            draft.startup.sanitize();
            store.save(draft);
            finish();
        }), Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        root.addView(footer);
        setContentView(root);
        build();
    }

    private void build() {
        controls.removeAllViews();
        final StartupConfig s = draft.startup;
        controls.addView(Ui.toggle(this, "Show when RetroDeck opens", s.enabled, (b, on) -> {
            s.enabled = on;
            build();
        }));
        controls.addView(Ui.divider(this));
        controls.addView(Ui.section(this, "Style"));
        for (final String preset : StartupConfig.PRESETS) {
            Ui.Row r = Ui.row(this, (preset.equals(s.preset) ? "●  " : "○  ") + StartupConfig.label(preset), null, v -> {
                s.preset = preset;
                build();
                play();
            });
            if (preset.equals(s.preset)) r.title.setTextColor(Ui.ACCENT);
            controls.addView(r);
        }
        boolean instant = StartupConfig.INSTANT.equals(s.preset);
        controls.addView(Ui.section(this, "Timing"));
        Ui.Slider duration = new Ui.Slider(this, "Duration", 10, (s.durationMs - StartupConfig.MIN_MS) / 500,
                v -> instant ? "0.6 s (fixed)" : String.format(Locale.US, "%.1f s", (StartupConfig.MIN_MS + v * 500) / 1000f),
                Ui.onChange(v -> s.durationMs = StartupConfig.MIN_MS + v * 500));
        duration.bar.setEnabled(!instant);
        controls.addView(duration);
        controls.addView(Ui.section(this, "Steps"));
        controls.addView(Ui.toggle(this, "Power-on lamps", s.powerOn, (b, on) -> s.powerOn = on));
        controls.addView(Ui.toggle(this, "LCD greeting", s.lcdBoot, (b, on) -> s.lcdBoot = on));
        android.widget.TextView note = Ui.text(this, "Tap the screen to skip the animation at any time. RetroDeck is ready underneath while it plays.",
                13, Ui.SUB, false);
        note.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 16));
        controls.addView(note);
    }

    private void play() {
        VehicleProfile p = draft.copy();
        p.startup.enabled = true;
        startup.play(p, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }
}
