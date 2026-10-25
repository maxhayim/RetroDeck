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

    private static final String PERSONALIZATION = "Personalization", AUDIO = "Audio", ABOUT = "About";

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
        note("The volume knob sets the device's media volume. Steering-wheel media keys work when the head unit sends them to the app on screen.");
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
