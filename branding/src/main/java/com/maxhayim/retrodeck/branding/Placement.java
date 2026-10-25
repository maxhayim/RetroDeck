package com.maxhayim.retrodeck.branding;

/** Where one logo sits inside one branding zone. */
public final class Placement {

    public static final float MIN_SCALE = 0.1f;

    public boolean visible;
    /** Size as a fraction of the largest size that fits the zone, 0.1..1. Never bigger than the zone. */
    public float scale = 1f;
    /** Centre of the logo, as 0..1 fractions of the zone. */
    public float x = 0.5f, y = 0.5f;

    public Placement() {}

    public Placement(boolean visible, float scale, float x, float y) {
        this.visible = visible;
        this.scale = scale;
        this.x = x;
        this.y = y;
    }

    public Placement copy() {
        return new Placement(visible, scale, x, y);
    }

    public Placement sanitize() {
        scale = LogoAdjust.clamp(scale, MIN_SCALE, 1f);
        x = LogoAdjust.clamp(x, 0f, 1f);
        y = LogoAdjust.clamp(y, 0f, 1f);
        return this;
    }
}
