package com.maxhayim.retrodeck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.maxhayim.retrodeck.core.ButtonMap;
import com.maxhayim.retrodeck.core.Ui;

import java.util.List;
import java.util.Locale;

/**
 * Settings → Buttons. Head units and steering-wheel controls send different keys, so any key can be
 * given any stereo action: press it here, then choose what it does.
 */
public class ButtonsActivity extends Activity {

    private static final String PREFS = "buttons", KEY = "map";

    private ButtonMap map;
    private LinearLayout rows;

    static ButtonMap load(Context c) {
        return ButtonMap.parse(c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY, null));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        map = load(this);
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "Buttons", "Settings  ›  Buttons"));
        rows = Ui.vertical(this);
        root.addView(Ui.scroll(this, rows), new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    private void show() {
        rows.removeAllViews();
        rows.addView(Ui.row(this, "Add a key", "Press a key on the head unit, steering wheel or remote, then choose what it does",
                v -> listen()));
        rows.addView(Ui.divider(this));
        rows.addView(Ui.section(this, "Keys"));
        List<Integer> codes = map.codes();
        if (codes.isEmpty()) note("No keys set. Add one above.");
        for (final int code : codes) {
            rows.addView(Ui.row(this, keyName(code), ButtonMap.label(map.action(code)), v -> choose(code)));
            rows.addView(Ui.divider(this));
        }
        rows.addView(Ui.row(this, "Reset to defaults", "Media keys, Menu, and 1-6 for the presets", v ->
                Ui.dialog(this).setTitle("Reset the buttons?").setPositiveButton("Reset", (d, w) -> {
                    map = ButtonMap.defaults();
                    save();
                }).setNegativeButton("Cancel", null).show()));
        rows.addView(Ui.divider(this));
        note("On the stereo itself every key works by touch. Presets 1-6 play a favourite song: hold one to save the song "
                + "that's playing to it. A key with nothing set keeps doing what Android does with it. Back and Home "
                + "always stay with Android.");
    }

    /** Waits for the next key and offers the actions for it. */
    private void listen() {
        final AlertDialog d = Ui.dialog(this).setTitle("Press a key")
                .setMessage("Press the key on the head unit, steering wheel or remote that you want to set.")
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnKeyListener((dialog, code, ev) -> {
            if (code == KeyEvent.KEYCODE_BACK) return false;
            if (!ButtonMap.mappable(code)) return true;
            if (ev.getAction() != KeyEvent.ACTION_UP) return true;
            dialog.dismiss();
            choose(code);
            return true;
        });
        d.show();
    }

    private void choose(final int code) {
        final List<String> actions = ButtonMap.ACTIONS;
        String[] labels = new String[actions.size() + 1];
        for (int i = 0; i < actions.size(); i++) labels[i] = ButtonMap.label(actions.get(i));
        labels[actions.size()] = "Nothing (leave it to Android)";
        int current = actions.indexOf(map.action(code));
        Ui.dialog(this).setTitle(keyName(code))
                .setSingleChoiceItems(labels, current < 0 ? actions.size() : current, (d, w) -> {
                    map.set(code, w < actions.size() ? actions.get(w) : null);
                    save();
                    d.dismiss();
                }).show();
    }

    private void save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, map.toString()).apply();
        show();
    }

    private void note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.SUB, false);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        rows.addView(t);
    }

    /** "KEYCODE_MEDIA_NEXT" reads as "Media next". */
    static String keyName(int code) {
        String s = KeyEvent.keyCodeToString(code);
        if (s.startsWith("KEYCODE_")) s = s.substring(8);
        else return "Key " + code;
        s = s.replace('_', ' ').toLowerCase(Locale.US);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1) + "  ·  key " + code;
    }
}
