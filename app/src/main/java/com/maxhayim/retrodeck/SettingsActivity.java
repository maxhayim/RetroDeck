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

    private static final String PERSONALIZATION = "Personalization", AUDIO = "Audio", DISPLAY = "Display", BUTTONS = "Buttons",
            UPDATES = "Updates", ABOUT = "About";

    private LinearLayout sections, rows;
    private String current = PERSONALIZATION;
    /** Updates: the row showing the updater's status, and whether an install row is shown. */
    private Ui.Row updateStatus;
    private boolean installShown;

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
        Updater.visible++;
        // The updater works on its own thread and reports through its status; show it as it changes.
        Updater.onChange = this::showUpdates;
        show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Updater.visible--;
        Updater.onChange = null;
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
        m.put(UPDATES, new ArrayList<>());
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
        updateStatus = null;
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
        if (UPDATES.equals(current)) updateRows();
        if (ABOUT.equals(current)) aboutRows();
    }

    private void updateRows() {
        rows.addView(Ui.row(this, "Updates", Updater.label(Updater.mode(this)), v -> {
            final String[] modes = Updater.MODES;
            String[] labels = new String[modes.length];
            int now = 0;
            for (int i = 0; i < modes.length; i++) {
                labels[i] = Updater.label(modes[i]);
                if (modes[i].equals(Updater.mode(this))) now = i;
            }
            Ui.dialog(this).setTitle("Updates").setSingleChoiceItems(labels, now, (d, w) -> {
                Updater.setMode(this, modes[w]);
                d.dismiss();
                show();
            }).show();
        }));
        rows.addView(Ui.divider(this));
        updateStatus = Ui.row(this, "Check for updates now", "", v -> Updater.checkNow(this));
        rows.addView(updateStatus);
        rows.addView(Ui.divider(this));
        final Updater.Release r = Updater.available(this);
        installShown = r != null;
        if (r != null) {
            rows.addView(Ui.row(this, "Install RetroDeck " + r.version, "Downloads it from GitHub and installs it. "
                    + "RetroDeck closes while it does; open it again after.", v -> Updater.installNow(this)));
            rows.addView(Ui.divider(this));
        }
        showUpdates();
        note("Off: RetroDeck never checks. Notify only: while RetroDeck is open it checks GitHub every few hours and "
                + "says once when there's a new version. Automatic: it also downloads the new version, and installs it "
                + "when you switch the stereo off with PWR or the head unit's screen goes off, never while music plays.");
        note("Android 12 and newer install without asking once RetroDeck has updated itself once. Older Android, and "
                + "a first update, ask you to tap Install; Android may first ask to allow RetroDeck to install apps. "
                + "An update must be signed with the same key as this copy, or it's refused.");
        note("A check asks GitHub for the latest release of " + Updater.REPO + ". GitHub sees the head unit's "
                + "internet address and RetroDeck's version, and nothing else is sent.");
    }

    /** Keeps the status line current while Updates is open. */
    private void showUpdates() {
        if (updateStatus == null) return;
        if ((Updater.available(this) != null) != installShown) {
            show();
            return;
        }
        String s = Updater.status(this);
        updateStatus.value(s.isEmpty() ? "Version " + BuildConfig.VERSION_NAME : s);
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
        rows.addView(Ui.row(this, "Privacy", "No account, no cloud. Profiles and logos stay on this device. "
                + "Online only for GIPHY search and update checks (Updates can be Off).", null));
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
