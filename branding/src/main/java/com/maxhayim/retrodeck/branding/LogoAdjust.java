package com.maxhayim.retrodeck.branding;

import java.util.Locale;

/**
 * Non-destructive edits to an imported logo. The optimised image on disk is never changed; these are
 * applied when the logo is drawn, so every edit can be undone.
 */
public final class LogoAdjust {

    /** Crop, as fractions of the image. */
    public float cropLeft, cropTop, cropRight = 1f, cropBottom = 1f;
    /** Degrees, -180..180. */
    public float rotation;
    /** 0..1. */
    public float opacity = 1f;
    /** -1 (black) .. 0 (unchanged) .. 1 (white). */
    public float brightness;
    /** Tint colour and how much of it to apply, 0..1. 0 keeps the logo's own colours. */
    public int tint = 0xFFFFFFFF;
    public float tintStrength;
    /** When false the logo may be stretched to fill its zone. On by default: logos are never distorted. */
    public boolean keepAspect = true;

    public LogoAdjust copy() {
        LogoAdjust a = new LogoAdjust();
        a.cropLeft = cropLeft;
        a.cropTop = cropTop;
        a.cropRight = cropRight;
        a.cropBottom = cropBottom;
        a.rotation = rotation;
        a.opacity = opacity;
        a.brightness = brightness;
        a.tint = tint;
        a.tintStrength = tintStrength;
        a.keepAspect = keepAspect;
        return a;
    }

    /** Puts every value back in range and makes the crop at least 5% of the image each way. */
    public LogoAdjust sanitize() {
        cropLeft = clamp(cropLeft, 0f, 0.95f);
        cropTop = clamp(cropTop, 0f, 0.95f);
        cropRight = clamp(cropRight, cropLeft + 0.05f, 1f);
        cropBottom = clamp(cropBottom, cropTop + 0.05f, 1f);
        if (Float.isNaN(rotation)) rotation = 0;
        rotation = clamp(rotation, -180f, 180f);
        opacity = clamp(opacity, 0f, 1f);
        brightness = clamp(brightness, -1f, 1f);
        tintStrength = clamp(tintStrength, 0f, 1f);
        tint |= 0xFF000000;
        return this;
    }

    public boolean isCropped() {
        return cropLeft > 0.001f || cropTop > 0.001f || cropRight < 0.999f || cropBottom < 0.999f;
    }

    /** Identifies the pixels these edits produce (opacity excluded; it is applied while drawing). */
    public String signature() {
        return String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f,%.1f,%.3f,%08x,%.3f", cropLeft, cropTop, cropRight,
                cropBottom, rotation, brightness, tint, tintStrength);
    }

    static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
