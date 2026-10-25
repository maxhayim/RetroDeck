package com.maxhayim.retrodeck.branding;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.maxhayim.retrodeck.core.Box;
import com.maxhayim.retrodeck.core.Illumination;
import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Zones;

import java.io.File;
import java.util.Locale;

/**
 * Draws a vehicle profile's logos into a stereo's branding zones. Without a logo, the manufacturer or
 * model name is printed in the same place, the way a stereo maker's name is printed on a faceplate.
 */
public final class BrandingRenderer implements StereoView.Overlay {

    private final ProfileStore store;
    private VehicleProfile profile;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    private String selectedZone;
    private int selectedRole = -1;

    public BrandingRenderer(ProfileStore store, VehicleProfile profile) {
        this.store = store;
        this.profile = profile;
    }

    public void setProfile(VehicleProfile profile) {
        this.profile = profile;
    }

    public VehicleProfile profile() {
        return profile;
    }

    /** Frames one logo, for the placement editor. */
    public void setSelection(String zone, int role) {
        selectedZone = zone;
        selectedRole = role;
    }

    @Override
    public void drawZone(Canvas c, Skin skin, String zone, RectF px, int illumination, boolean standby) {
        if (profile == null) return;
        float alpha = 1f;
        if (Zones.VISUALIZER.equals(zone)) alpha = 0.35f;
        if (Zones.STANDBY.equals(zone)) alpha = 0.75f;
        for (int role = 0; role < 2; role++) {
            Placement p = profile.placement(zone, role);
            if (!p.visible) continue;
            drawLogo(c, skin, zone, px, role, p, alpha, illumination);
        }
    }

    /** Draws one logo in a zone and returns its rectangle, or null when nothing was drawn. */
    public RectF drawLogo(Canvas c, Skin skin, String zone, RectF px, int role, Placement p, float alpha, int illumination) {
        Box zoneBox = new Box(px.left, px.top, px.right, px.bottom);
        File f = store.logo(profile, role);
        Bitmap bm = f == null ? null : LogoCache.get().get(f, profile.adjust[role]);
        RectF out;
        if (bm != null) {
            LogoAdjust a = profile.adjust[role];
            Box b = PlacementMath.layout(zoneBox, bm.getWidth(), bm.getHeight(), p, a.keepAspect);
            dst.set(b.left, b.top, b.right, b.bottom);
            paint.setAlpha(Math.round(255 * a.opacity * alpha));
            c.drawBitmap(bm, null, dst, paint);
            out = new RectF(dst);
        } else {
            out = drawName(c, skin, zone, zoneBox, role, p, alpha, illumination);
        }
        if (out != null && zone.equals(selectedZone) && role == selectedRole) {
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            float u = Math.min(c.getWidth(), c.getHeight());
            s.setStrokeWidth(Math.max(2, u * 0.004f));
            s.setColor(0xFFFFFFFF);
            s.setPathEffect(new DashPathEffect(new float[]{u * 0.01f, u * 0.006f}, 0));
            c.drawRect(out, s);
        }
        return out;
    }

    /** The rectangle a logo would occupy, without drawing it; used for dragging in the editor. */
    public RectF logoRect(String zone, RectF px, int role) {
        Placement p = profile.placement(zone, role);
        Box zoneBox = new Box(px.left, px.top, px.right, px.bottom);
        File f = store.logo(profile, role);
        Bitmap bm = f == null ? null : LogoCache.get().get(f, profile.adjust[role]);
        float w = bm == null ? 4 : bm.getWidth(), h = bm == null ? 1 : bm.getHeight();
        boolean keep = bm == null || profile.adjust[role].keepAspect;
        Box b = PlacementMath.layout(zoneBox, w, h, p, keep);
        return new RectF(b.left, b.top, b.right, b.bottom);
    }

    private RectF drawName(Canvas c, Skin skin, String zoneId, Box zone, int role, Placement p, float alpha, int illumination) {
        String name = role == VehicleProfile.MANUFACTURER ? profile.manufacturer : profile.model;
        if (name == null || name.trim().isEmpty()) return null;
        name = name.trim().toUpperCase(Locale.US);
        text.reset();
        text.setAntiAlias(true);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
        if (android.os.Build.VERSION.SDK_INT >= 21) text.setLetterSpacing(0.12f);
        text.setTextAlign(Paint.Align.LEFT);
        // Size the text like a logo: fit its box into the zone.
        text.setTextSize(100);
        float tw = text.measureText(name);
        float th = -text.ascent();
        Box b = PlacementMath.layout(zone, tw, th, p, true);
        float size = 100 * b.width() / tw;
        text.setTextSize(size);
        // Printed on the faceplate in paint; shown on the LCD and black screens in light.
        boolean lit = Zones.LCD.equals(zoneId) || !Zones.onFaceplate(zoneId);
        int color = lit ? illumination : skin.lcdStyle == Skin.LCD_MODERN ? 0xFFE8ECF2 : skin.printColor;
        text.setColor(Illumination.withAlpha(color, Math.round(255 * alpha)));
        c.drawText(name, b.left, b.bottom, text);
        return new RectF(b.left, b.top, b.right, b.bottom);
    }
}
