package com.maxhayim.retrodeck.branding;

import com.maxhayim.retrodeck.core.Box;

/** Fits a logo into a zone. Pure arithmetic so it can be tested without a device. */
public final class PlacementMath {

    private PlacementMath() {}

    /**
     * The logo's rectangle inside zone. With keepAspect the logo keeps its proportions and is as large
     * as fits, times the placement's scale; without it the logo fills the zone at that scale. The result
     * always lies entirely inside the zone, so a logo can never spill onto controls next to its zone.
     */
    public static Box layout(Box zone, float logoW, float logoH, Placement p, boolean keepAspect) {
        float zw = zone.width(), zh = zone.height();
        float scale = LogoAdjust.clamp(p.scale, Placement.MIN_SCALE, 1f);
        float w, h;
        if (keepAspect && logoW > 0 && logoH > 0) {
            float fit = Math.min(zw / logoW, zh / logoH);
            w = logoW * fit * scale;
            h = logoH * fit * scale;
        } else {
            w = zw * scale;
            h = zh * scale;
        }
        float cx = zone.left + zw * LogoAdjust.clamp(p.x, 0f, 1f);
        float cy = zone.top + zh * LogoAdjust.clamp(p.y, 0f, 1f);
        float l = clampStart(cx - w / 2, zone.left, zone.right, w);
        float t = clampStart(cy - h / 2, zone.top, zone.bottom, h);
        return new Box(l, t, l + w, t + h);
    }

    private static float clampStart(float start, float lo, float hi, float size) {
        return Math.max(lo, Math.min(hi - size, start));
    }

    /** The placement centre for a logo dragged so its centre is at (px, py) in the same units as zone. */
    public static float[] centreFromPoint(Box zone, float px, float py) {
        return new float[]{
                LogoAdjust.clamp((px - zone.left) / zone.width(), 0f, 1f),
                LogoAdjust.clamp((py - zone.top) / zone.height(), 0f, 1f)};
    }

    /** Size of a w x h image after rotating it by degrees: the bounding box of the rotated image. */
    public static float[] rotatedSize(float w, float h, float degrees) {
        double r = Math.toRadians(degrees);
        double c = Math.abs(Math.cos(r)), s = Math.abs(Math.sin(r));
        return new float[]{(float) (w * c + h * s), (float) (w * s + h * c)};
    }
}
