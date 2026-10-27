package com.maxhayim.retrodeck;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.maxhayim.retrodeck.core.RetroDeckModule;
import com.maxhayim.retrodeck.core.Ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings, two panes: sections on the left, their rows on the right. Rows from feature modules are
 * listed under the section each module asks for, so Personalization → Vehicle Branding comes from the
 * branding module.
 */
public class SettingsActivity extends Activity {

    private static final String PERSONALIZATION = "Personalization", AUDIO = "Audio", DISPLAY = "Display", BUTTONS = "Buttons", ABOUT = "About";

    private LinearLayout sections, rows;
    private String current = PERSONALIZATION;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "Settings", null));
        LinearLayout body = Ui.horizontal(this);
        body.setGravity(0);
        sections = Ui.vertical(this);
        sections.setBackgroundColor(Ui.PANEL);
        body.addView(Ui.scroll(this, sections), Ui.weight(0.32f));
        rows = Ui.vertical(this);
        body.addView(Ui.scroll(this, rows), Ui.weight(0.68f));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        show();
    }

    private Map<String, List<RetroDeckModule.SettingsEntry>> entries() {
        Map<String, List<RetroDeckModule.SettingsEntry>> m = new LinkedHashMap<>();
        m.put(PERSONALIZATION, new ArrayList<>());
        m.put(AUDIO, new ArrayList<>());
        m.put(DISPLAY, new ArrayList<>());
        m.put(BUTTONS, new ArrayList<>());
        for (RetroDeckModule mod : RetroDeckApp.modules()) {
            for (RetroDeckModule.SettingsEntry e : mod.settings(this)) {
                List<RetroDeckModule.SettingsEntry> list = m.get(e.section);
                if (list == null) m.put(e.section, list = new ArrayList<>());
                list.add(e);
            }
        }
        m.put(ABOUT, new ArrayList<>());
        return m;
    }

    private void show() {
        Map<String, List<RetroDeckModule.SettingsEntry>> all = entries();
        sections.removeAllViews();
        for (final String s : all.keySet()) {
            Ui.Row r = Ui.row(this, s, null, v -> {
                current = s;
                show();
            });
            if (s.equals(current)) {
                r.setBackgroundColor(Ui.ROW_DOWN);
                r.title.setTextColor(Ui.ACCENT);
            }
            sections.addView(r);
            sections.addView(Ui.divider(this));
        }

        rows.removeAllViews();
        rows.addView(Ui.section(this, current));
        List<RetroDeckModule.SettingsEntry> list = all.get(current);
        if (list != null) {
            for (final RetroDeckModule.SettingsEntry e : list) {
                rows.addView(Ui.row(this, e.title, e.summary, v -> startActivity(e.intent)));
                rows.addView(Ui.divider(this));
            }
        }
        if (AUDIO.equals(current)) audioRows();
        if (DISPLAY.equals(current)) displayRows();
        if (BUTTONS.equals(current)) buttonRows();
        if (ABOUT.equals(current)) aboutRows();
    }

    private void audioRows() {
        final Player p = new Player(this, new com.maxhayim.retrodeck.core.StereoState(), () -> {});
        rows.addView(Ui.row(this, "Music files", p.count() + (p.count() == 1 ? " file" : " files")
                + "  ·  press SRC on the stereo to add more", null));
        rows.addView(Ui.divider(this));
        rows.addView(Ui.row(this, "Clear music list", "Files stay on the device; only the list is cleared", v ->
                Ui.dialog(this).setTitle("Clear the music list?").setPositiveButton("Clear", (d, w) -> {
                    p.clear();
                    show();
                }).setNegativeButton("Cancel", null).show()));
        rows.addView(Ui.divider(this));
        melodyRow(true);
        melodyRow(false);
        note("The melody box plays a station chime, like a Japanese train platform's, when the stereo comes on: at start-up, "
                + "and when PWR or a key wakes it. The shut-down chime plays on PWR, and when the head unit turns its screen "
                + "off, which some do too late to hear. For your own, copy sound files into:\n" + MelodyBox.folder(this).getPath());
        note("The volume knob sets the device's media volume. Steering-wheel keys are set under Buttons.");
    }

    /** Choosing one plays it, so you can hear them in turn. */
    private void melodyRow(final boolean start) {
        String now = start ? MelodyBox.start(this) : MelodyBox.stop(this);
        rows.addView(Ui.row(this, start ? "Start-up melody" : "Shut-down melody", MelodyBox.label(now), v -> {
            final java.util.List<String> c = MelodyBox.choices(this);
            String[] labels = new String[c.size()];
            for (int i = 0; i < c.size(); i++) labels[i] = MelodyBox.label(c.get(i));
            String cur = start ? MelodyBox.start(this) : MelodyBox.stop(this);
            Ui.dialog(this).setTitle(start ? "Start-up melody" : "Shut-down melody")
                    .setSingleChoiceItems(labels, Math.max(0, c.indexOf(cur)), (d, w) -> {
                        if (start) MelodyBox.setStart(this, c.get(w));
                        else MelodyBox.setStop(this, c.get(w));
                        MelodyBox.play(this, c.get(w));
                    })
                    .setPositiveButton("Done", (d, w) -> show())
                    .setOnCancelListener(d -> show())
                    .show();
        }));
        rows.addView(Ui.divider(this));
    }

    private void displayRows() {
        rows.addView(Ui.row(this, "Full-screen visualizer", DisplaySettings.label(DisplaySettings.fullscreenAfter(this))
                + "  ·  comes on by itself while music plays", v -> {
            final int[] c = DisplaySettings.CHOICES;
            String[] labels = new String[c.length];
            int now = 0;
            for (int i = 0; i < c.length; i++) {
                labels[i] = DisplaySettings.label(c[i]);
                if (c[i] == DisplaySettings.fullscreenAfter(this)) now = i;
            }
            Ui.dialog(this).setTitle("Full-screen visualizer").setSingleChoiceItems(labels, now, (d, w) -> {
                DisplaySettings.setFullscreenAfter(this, c[w]);
                d.dismiss();
                show();
            }).show();
        }));
        rows.addView(Ui.divider(this));
        note("Hold DISP, or press a key set to Full-screen visualizer under Buttons, to show it any time. Touch the screen "
                + "or press a key to go back; media keys and presets keep working while it shows.");
    }

    private void buttonRows() {
        int n = ButtonsActivity.load(this).codes().size();
        rows.addView(Ui.row(this, "Head-unit and steering-wheel keys", n + (n == 1 ? " key set" : " keys set")
                + "  ·  give any key any stereo action", v -> startActivity(new android.content.Intent(this, ButtonsActivity.class))));
        rows.addView(Ui.divider(this));
        note("Presets 1-6 play a favourite song. Hold a preset, on screen or on a key set to it, to save the song that's playing.");
    }

    private void aboutRows() {
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "?";
        }
        rows.addView(Ui.row(this, "RetroDeck", "Version " + version + "  ·  MIT License", null));
        rows.addView(Ui.divider(this));
        rows.addView(Ui.row(this, "Privacy", "No account, no cloud, no network. Profiles and logos stay on this device.", null));
        rows.addView(Ui.divider(this));
        rows.addView(Ui.row(this, "Open source", "AndroidSVG 1.4 by Paul LeBeau, Apache License 2.0", null));
        rows.addView(Ui.divider(this));
        note("Vehicle names and logos are trademarks of their owners. RetroDeck ships with none; you import your own.");
    }

    private void note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.SUB, false);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        rows.addView(t);
    }
}
