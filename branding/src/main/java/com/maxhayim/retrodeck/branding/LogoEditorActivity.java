package com.maxhayim.retrodeck.branding;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.maxhayim.retrodeck.core.AspectFrame;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Ui;

import java.io.File;
import java.util.Locale;

/**
 * Adjusts one imported logo: crop, rotation, opacity, brightness, tint and aspect ratio. Edits are
 * shown live on the stereo preview and only kept when Save is pressed.
 */
public class LogoEditorActivity extends Activity {

    static final String EXTRA_ROLE = "role";
    /** A just-imported file that replaces the current logo if the user saves. */
    static final String EXTRA_NEW_FILE = "new_file";
    static final String EXTRA_NEW_COLOR = "new_color";

    private ProfileStore store;
    private VehicleProfile draft;
    private int role;
    private File newFile;
    private boolean saved;
    private CropView crop;
    private StereoView preview;
    private Ui.Slider rotation, opacity, brightness, tint;
    private LinearLayout tintChips, controls;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable redraw = () -> preview.invalidate();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ProfileStore.get(this);
        draft = store.active();
        role = getIntent().getIntExtra(EXTRA_ROLE, VehicleProfile.MANUFACTURER);
        String nf = getIntent().getStringExtra(EXTRA_NEW_FILE);
        if (nf != null && ProfileJson.isAssetName(nf)) {
            newFile = new File(store.folder(draft.id), nf);
            draft.logoFile[role] = nf;
            draft.logoColor[role] = getIntent().getIntExtra(EXTRA_NEW_COLOR, 0);
            draft.adjust[role] = new LogoAdjust();
        }
        if (!draft.hasLogo(role)) {
            finish();
            return;
        }
        // A newly imported logo is shown where it matters most, even if that zone was switched off.
        if (newFile != null) {
            draft.placement(com.maxhayim.retrodeck.core.Zones.STARTUP, role).visible = true;
        }

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        String title = role == VehicleProfile.MANUFACTURER ? "Manufacturer Logo" : "Model Badge";
        root.addView(Ui.header(this, title, newFile != null ? "New image — adjust it, then save" : draft.displayName()));
        LinearLayout body = Ui.horizontal(this);
        body.setGravity(0);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout left = Ui.vertical(this);
        left.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        crop = new CropView(this);
        left.addView(Ui.text(this, "Crop: drag the corners or the frame", 13, Ui.SUB, false));
        left.addView(crop, Ui.margins(new LinearLayout.LayoutParams(-1, 0, 1), this, 0, 4, 0, 8));
        AspectFrame frame = new AspectFrame(this, 1024f / 600f);
        preview = new StereoView(this);
        preview.setState(Previews.state());
        preview.setOverlay(new BrandingRenderer(store, draft));
        Previews.apply(preview, draft);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        left.addView(frame, new LinearLayout.LayoutParams(-1, -2));
        body.addView(left, Ui.weight(0.5f));

        controls = Ui.vertical(this);
        controls.setBackgroundColor(Ui.PANEL);
        buildControls(controls);
        body.addView(Ui.scroll(this, controls), Ui.weight(0.5f));

        LinearLayout footer = Ui.horizontal(this);
        footer.setBackgroundColor(Ui.PANEL);
        footer.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        footer.addView(Ui.button(this, "Reset all", false, v -> resetAll()));
        footer.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1));
        footer.addView(Ui.button(this, "Cancel", false, v -> finish()));
        footer.addView(Ui.button(this, "Save", true, v -> save()), Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        root.addView(footer);
        setContentView(root);

        bindCrop();
    }

    private void bindCrop() {
        Bitmap original = LogoCache.get().original(store.logo(draft, role));
        crop.set(original, draft.adjust[role], (l, t, r, b) -> {
            LogoAdjust a = draft.adjust[role];
            a.cropLeft = l;
            a.cropTop = t;
            a.cropRight = r;
            a.cropBottom = b;
            changed();
        });
    }

    private void buildControls(LinearLayout c) {
        final LogoAdjust a = draft.adjust[role];
        c.addView(Ui.section(this, "Rotation"));
        rotation = new Ui.Slider(this, "Angle", 360, Math.round(a.rotation) + 180,
                p -> (p - 180) + "°", Ui.onChange(p -> {
                    a.rotation = p - 180;
                    changed();
                }));
        c.addView(rotation);
        LinearLayout turns = Ui.horizontal(this);
        turns.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 4));
        turns.addView(Ui.button(this, "⟲  90°", false, v -> turn(-90)));
        turns.addView(Ui.button(this, "⟳  90°", false, v -> turn(90)), Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        turns.addView(Ui.button(this, "Straight", false, v -> {
            a.rotation = 0;
            rotation.set(180);
            changed();
        }), Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        c.addView(turns);

        c.addView(Ui.section(this, "Light"));
        opacity = new Ui.Slider(this, "Opacity", 100, Math.round(a.opacity * 100), p -> p + "%", Ui.onChange(p -> {
            a.opacity = p / 100f;
            changed();
        }));
        c.addView(opacity);
        brightness = new Ui.Slider(this, "Brightness", 200, Math.round(a.brightness * 100) + 100,
                p -> String.format(Locale.US, "%+d", p - 100), Ui.onChange(p -> {
                    a.brightness = (p - 100) / 100f;
                    changed();
                }));
        c.addView(brightness);

        c.addView(Ui.section(this, "Colour tint"));
        tint = new Ui.Slider(this, "Tint strength", 100, Math.round(a.tintStrength * 100), p -> p + "%", Ui.onChange(p -> {
            a.tintStrength = p / 100f;
            changed();
        }));
        c.addView(tint);
        tintChips = Ui.horizontal(this);
        tintChips.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 6));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.addView(tintChips);
        c.addView(hs);
        drawTintChips();

        c.addView(Ui.section(this, "Shape"));
        c.addView(Ui.toggle(this, "Keep aspect ratio (never stretch)", a.keepAspect, (b, on) -> {
            a.keepAspect = on;
            changed();
        }));
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 12));
        row.addView(Ui.button(this, "Reset crop", false, v -> {
            a.cropLeft = a.cropTop = 0;
            a.cropRight = a.cropBottom = 1;
            crop.setCrop(0, 0, 1, 1);
            changed();
        }));
        c.addView(row);
        TextView hint = Ui.text(this, "Size and position are set per zone in Edit Logo Placement.", 13, Ui.SUB, false);
        hint.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 16));
        c.addView(hint);
    }

    private void drawTintChips() {
        tintChips.removeAllViews();
        final LogoAdjust a = draft.adjust[role];
        String[] names = {"Illumination", "Accent", "White", "Chrome", "Black"};
        int[] colors = {draft.illuminationColor(), draft.accentColor, 0xFFFFFFFF, 0xFFB8C0CC, 0xFF000000};
        for (int i = 0; i < names.length; i++) {
            final int col = colors[i] | 0xFF000000;
            boolean sel = (a.tint | 0xFF000000) == col;
            tintChips.addView(Ui.chip(this, names[i], sel, v -> {
                a.tint = col;
                if (a.tintStrength < 0.01f) {
                    a.tintStrength = 1f;
                    tint.set(100);
                }
                drawTintChips();
                changed();
            }), Ui.margins(Ui.wrap(), this, 4, 0, 4, 0));
        }
    }

    private void turn(int deg) {
        LogoAdjust a = draft.adjust[role];
        float r = a.rotation + deg;
        if (r > 180) r -= 360;
        if (r < -180) r += 360;
        a.rotation = r;
        rotation.set(Math.round(r) + 180);
        changed();
    }

    private void resetAll() {
        draft.adjust[role] = new LogoAdjust();
        // The controls hold the old settings object; rebuild them around the new one.
        controls.removeAllViews();
        buildControls(controls);
        bindCrop();
        changed();
    }

    /** Redraws the preview, at most every 50 ms so slow head units keep up with a dragged slider. */
    private void changed() {
        main.removeCallbacks(redraw);
        main.postDelayed(redraw, 50);
    }

    private void save() {
        draft.adjust[role].sanitize();
        store.save(draft);
        saved = true;
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // A new image the user didn't save is thrown away.
        if (!saved && newFile != null && isFinishing()) store.discard(newFile);
    }
}
