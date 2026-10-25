package com.maxhayim.retrodeck.branding;

import android.app.Activity;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.maxhayim.retrodeck.core.AspectFrame;
import com.maxhayim.retrodeck.core.Box;
import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.StereoState;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Ui;
import com.maxhayim.retrodeck.core.Zones;

import java.util.ArrayList;
import java.util.List;

/**
 * Places the logos in the skin's branding zones. Pick a zone and a logo, then drag it on the preview
 * or use the sliders. A logo always stays inside its zone, and zones are declared by the skin away
 * from the buttons and track text, so branding can't hide a control.
 */
public class PlacementEditorActivity extends Activity {

    private ProfileStore store;
    private VehicleProfile draft;
    private Skin skin;
    private String zone = Zones.HEADER;
    private int role = VehicleProfile.MANUFACTURER;

    private StereoView stereo;
    private StartupView startup;
    private BrandingRenderer stereoRenderer;
    private LinearLayout zoneChips, roleChips, controls;
    private Ui.Slider size, horizontal, vertical;
    private TextView zoneNote;
    private final StereoState playing = Previews.state();
    private final StereoState standby = Previews.state();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ProfileStore.get(this);
        draft = store.active();
        skin = Skins.get(draft.skin);
        standby.power = false;

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "Edit Logo Placement", draft.displayName() + "  ·  " + skin.name));

        zoneChips = Ui.horizontal(this);
        zoneChips.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setBackgroundColor(Ui.PANEL);
        hs.addView(zoneChips);
        root.addView(hs);

        LinearLayout body = Ui.horizontal(this);
        body.setGravity(0);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        controls = Ui.vertical(this);
        controls.setBackgroundColor(Ui.PANEL);
        body.addView(Ui.scroll(this, controls), Ui.weight(0.38f));

        LinearLayout right = Ui.vertical(this);
        right.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        AspectFrame frame = new AspectFrame(this, 1024f / 600f);
        stereo = new StereoView(this);
        stereoRenderer = new BrandingRenderer(store, draft);
        stereo.setOverlay(stereoRenderer);
        Previews.apply(stereo, draft);
        frame.addView(stereo, new FrameLayout.LayoutParams(-1, -1));
        startup = new StartupView(this, store);
        startup.setVisibility(View.GONE);
        frame.addView(startup, new FrameLayout.LayoutParams(-1, -1));
        View touch = new View(this);
        touch.setOnTouchListener(this::onPreviewTouch);
        frame.addView(touch, new FrameLayout.LayoutParams(-1, -1));
        right.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        zoneNote = Ui.text(this, "", 13, Ui.SUB, false);
        zoneNote.setPadding(0, Ui.dp(this, 8), 0, 0);
        right.addView(zoneNote);
        body.addView(right, Ui.weight(0.62f));

        LinearLayout footer = Ui.horizontal(this);
        footer.setBackgroundColor(Ui.PANEL);
        footer.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        footer.addView(Ui.button(this, "Reset all zones", false, v -> {
            draft.resetLayout();
            showZone();
        }));
        footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        footer.addView(Ui.button(this, "Cancel", false, v -> finish()));
        footer.addView(Ui.button(this, "Save", true, v -> {
            store.save(draft);
            finish();
        }), Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        root.addView(footer);
        setContentView(root);
        showZone();
    }

    private List<String> zones() {
        List<String> out = new ArrayList<>();
        for (String z : Zones.ALL) if (skin.zone(z) != null) out.add(z);
        return out;
    }

    private void showZone() {
        zoneChips.removeAllViews();
        for (final String z : zones()) {
            zoneChips.addView(Ui.chip(this, Zones.shortLabel(z), z.equals(zone), v -> {
                zone = z;
                // Start with a logo that is shown here, so a drag never moves a hidden one.
                if (!draft.placement(zone, role).visible && draft.placement(zone, 1 - role).visible) role = 1 - role;
                showZone();
            }), Ui.margins(Ui.wrap(), this, 4, 0, 4, 0));
        }
        buildControls();
        boolean faceplate = Zones.onFaceplate(zone);
        if (Zones.STARTUP.equals(zone)) {
            startup.showStill(draft);
            startup.renderer().setSelection(zone, role);
        } else {
            startup.setVisibility(View.GONE);
        }
        stereo.setState(Zones.STANDBY.equals(zone) ? standby : playing);
        stereo.setShowZones(faceplate || Zones.STANDBY.equals(zone), zone);
        stereoRenderer.setSelection(zone, role);
        stereo.invalidate();
        zoneNote.setText(Zones.label(zone) + ".  Drag the framed logo to move it.");
    }

    private void buildControls() {
        controls.removeAllViews();
        controls.addView(Ui.section(this, Zones.label(zone)));
        roleChips = Ui.horizontal(this);
        roleChips.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        String[] names = {"Manufacturer logo", "Model badge"};
        for (int r = 0; r < 2; r++) {
            final int rr = r;
            roleChips.addView(Ui.chip(this, names[r], r == role, v -> {
                role = rr;
                showZone();
            }), Ui.margins(Ui.wrap(), this, 4, 0, 4, 0));
        }
        controls.addView(roleChips);
        final Placement p = draft.placement(zone, role);
        if (!draft.hasLogo(role)) {
            String name = role == VehicleProfile.MANUFACTURER ? draft.manufacturer : draft.model;
            TextView t = Ui.text(this, name.isEmpty()
                    ? "No image or name yet. Import one in Vehicle Branding."
                    : "No image yet, so the name \"" + name + "\" is shown instead.", 13, Ui.SUB, false);
            t.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4));
            controls.addView(t);
        }
        controls.addView(Ui.toggle(this, "Show in this zone", p.visible, (b, on) -> {
            p.visible = on;
            refresh();
        }));
        size = new Ui.Slider(this, "Size", 90, Math.round(p.scale * 100) - 10, v -> (v + 10) + "%", Ui.onChange(v -> {
            p.scale = (v + 10) / 100f;
            refresh();
        }));
        controls.addView(size);
        horizontal = new Ui.Slider(this, "Horizontal", 100, Math.round(p.x * 100), v -> position(v, "Left", "Right"), Ui.onChange(v -> {
            p.x = v / 100f;
            refresh();
        }));
        controls.addView(horizontal);
        vertical = new Ui.Slider(this, "Vertical", 100, Math.round(p.y * 100), v -> position(v, "Top", "Bottom"), Ui.onChange(v -> {
            p.y = v / 100f;
            refresh();
        }));
        controls.addView(vertical);
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 16));
        row.addView(Ui.button(this, "Centre", false, v -> {
            p.x = p.y = 0.5f;
            horizontal.set(50);
            vertical.set(50);
            refresh();
        }));
        row.addView(Ui.button(this, "Fill zone", false, v -> {
            p.scale = 1f;
            p.x = p.y = 0.5f;
            p.visible = true;
            showZone();
        }), Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        controls.addView(row);
    }

    private static String position(int v, String low, String high) {
        if (v == 50) return "Centre";
        return v < 50 ? low + " " + (50 - v) * 2 + "%" : high + " " + (v - 50) * 2 + "%";
    }

    private void refresh() {
        stereo.invalidate();
        if (Zones.STARTUP.equals(zone)) startup.invalidate();
    }

    /** Dragging moves the selected logo; its centre follows the finger, kept inside the zone. */
    private boolean onPreviewTouch(View v, MotionEvent e) {
        Box b = skin.zone(zone);
        if (b == null) return false;
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_UP) {
            // A drag shows a hidden logo; rebuild so the switch says so.
            buildControls();
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) return true;
        v.getParent().requestDisallowInterceptTouchEvent(true);
        RectF px = new RectF(b.left * v.getWidth(), b.top * v.getHeight(), b.right * v.getWidth(), b.bottom * v.getHeight());
        float[] c = PlacementMath.centreFromPoint(new Box(px.left, px.top, px.right, px.bottom), e.getX(), e.getY());
        Placement p = draft.placement(zone, role);
        p.x = c[0];
        p.y = c[1];
        p.visible = true;
        horizontal.set(Math.round(p.x * 100));
        vertical.set(Math.round(p.y * 100));
        refresh();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }
}
