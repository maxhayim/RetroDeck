package com.maxhayim.retrodeck.branding;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.maxhayim.retrodeck.core.KeyLights;
import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every radio skin as a gallery, grouped like the themes pages: single DIN and double DIN, and by
 * decade. Each card is the skin drawn at this screen's shape, so it shows how it will look here.
 */
public class SkinGalleryActivity extends Activity {

    private static final String ALL = "All", SINGLE = "Single DIN", DOUBLE = "Double DIN";

    private ProfileStore store;
    private VehicleProfile profile;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> thumbs = new HashMap<>();
    private final List<Runnable> pending = new ArrayList<>();
    private LinearLayout grid, filters;
    private ScrollView scroll;
    private String filter = ALL;
    private int screenW, screenH, columns;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ProfileStore.get(this);
        profile = store.active();
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        screenW = Math.max(m.widthPixels, m.heightPixels);
        screenH = Math.min(m.widthPixels, m.heightPixels);
        columns = m.widthPixels / Ui.dp(this, 240) >= 4 ? 4 : 3;

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "Radio Skin", "Vehicle Branding  ›  " + Skins.get(profile.skin).name));
        filters = Ui.horizontal(this);
        filters.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        HorizontalScrollView fs = new HorizontalScrollView(this);
        fs.setHorizontalScrollBarEnabled(false);
        fs.addView(filters);
        root.addView(fs);
        grid = Ui.vertical(this);
        grid.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 24));
        scroll = Ui.scroll(this, grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        buildFilters();
        buildGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        for (Bitmap b : thumbs.values()) b.recycle();
        thumbs.clear();
    }

    /** All, Single DIN, Double DIN, then each decade that has a skin. */
    private void buildFilters() {
        filters.removeAllViews();
        Set<String> names = new LinkedHashSet<>();
        names.add(ALL);
        names.add(SINGLE);
        names.add(DOUBLE);
        List<String> eras = new ArrayList<>();
        for (Skin s : Skins.all()) if (!eras.contains(s.era)) eras.add(s.era);
        java.util.Collections.sort(eras);
        names.addAll(eras);
        for (String n : names) {
            TextView chip = Ui.chip(this, n, n.equals(filter), v -> {
                filter = n;
                buildFilters();
                buildGrid();
            });
            filters.addView(chip, Ui.margins(Ui.wrap(), this, 0, 0, 8, 0));
        }
    }

    private boolean shown(Skin s) {
        if (ALL.equals(filter)) return true;
        if (SINGLE.equals(filter)) return s.din == 1;
        if (DOUBLE.equals(filter)) return s.din == 2;
        return filter.equals(s.era);
    }

    /** On a wide single-DIN screen a single-DIN skin fills it; on any other screen a double-DIN one does. */
    private boolean fillsScreen(Skin s) {
        boolean wide = screenW >= screenH * 2.2f;
        return wide ? s.din == 1 : s.din == 2;
    }

    private void buildGrid() {
        main.removeCallbacksAndMessages(null);
        pending.clear();
        grid.removeAllViews();
        // Single DIN first, then double DIN, each under its heading, as on the themes pages.
        for (int din = 1; din <= 2; din++) {
            List<Skin> group = new ArrayList<>();
            for (Skin s : Skins.all()) if (s.din == din && shown(s)) group.add(s);
            if (group.isEmpty()) continue;
            grid.addView(Ui.section(this, (din == 1 ? SINGLE : DOUBLE) + "  ·  " + group.size()));
            LinearLayout line = null;
            for (int i = 0; i < group.size(); i++) {
                if (i % columns == 0) {
                    line = Ui.horizontal(this);
                    grid.addView(line, Ui.matchWidth());
                }
                line.addView(card(group.get(i)), Ui.weight(1));
            }
            for (int i = group.size() % columns; i != 0 && i < columns; i++) line.addView(new View(this), Ui.weight(1));
        }
        drawNext();
    }

    private View card(Skin s) {
        boolean current = s.id.equals(profile.skin);
        LinearLayout c = Ui.vertical(this);
        int pad = Ui.dp(this, 8);
        c.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(current ? Ui.ROW_DOWN : Ui.ROW);
        bg.setCornerRadius(Ui.dp(this, 10));
        bg.setStroke(Ui.dp(this, current ? 3 : 1), current ? Ui.ACCENT : Ui.LINE);
        c.setBackground(bg);

        FrameLayout frame = new FrameLayout(this);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_XY);
        img.setBackgroundColor(0xFF000000);
        frame.addView(img, new FrameLayout.LayoutParams(-1, -1));
        c.addView(new com.maxhayim.retrodeck.core.AspectFrame(this, screenW / (float) screenH) {
            {
                addView(frame, new FrameLayout.LayoutParams(-1, -1));
            }
        }, Ui.matchWidth());

        TextView name = Ui.text(this, (current ? "✓  " : "") + s.name, 15, current ? Ui.ACCENT : Ui.TEXT, true);
        name.setPadding(0, Ui.dp(this, 6), 0, 0);
        c.addView(name);
        String sub = (s.din == 1 ? SINGLE : DOUBLE) + "  ·  " + s.era + (fillsScreen(s) ? "  ·  fills this screen" : "");
        c.addView(Ui.text(this, sub, 12, Ui.SUB, false));
        c.setOnClickListener(v -> {
            profile.skin = s.id;
            store.save(profile);
            finish();
        });
        c.setClickable(true);
        c.setFocusable(true);

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        holder.addView(c, new LinearLayout.LayoutParams(-1, -2));
        Bitmap ready = thumbs.get(s.id);
        if (ready != null) img.setImageBitmap(ready);
        else pending.add(() -> img.setImageBitmap(thumb(s)));
        if (current) main.post(() -> scroll.requestChildFocus(grid, holder));
        return holder;
    }

    /** Draws the cards one at a time so the gallery opens at once and fills in. */
    private void drawNext() {
        if (destroyed || pending.isEmpty()) return;
        pending.remove(0).run();
        main.post(this::drawNext);
    }

    /** The skin by day with this profile's colours and logos, at this screen's shape. */
    private Bitmap thumb(Skin s) {
        Bitmap b = thumbs.get(s.id);
        if (b != null) return b;
        int w = 480, h = Math.round(w * screenH / (float) screenW);
        StereoView v = new StereoView(this);
        VehicleProfile p = profile.copy();
        p.skin = s.id;
        v.setOverlay(new BrandingRenderer(store, p));
        Previews.apply(v, p);
        v.setKeyLighting(KeyLights.OFF);
        v.setState(Previews.state());
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        v.setSkin(s);
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF000000);
        v.draw(c);
        thumbs.put(s.id, b);
        return b;
    }
}
