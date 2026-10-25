package com.maxhayim.retrodeck.core;

/**
 * A plain rectangle. Skins describe their layout in normalised units (0..1 of the stereo's width and
 * height) so one skin fits a 1024x600 head unit and a 1920x1080 tablet alike. Kept free of
 * android.graphics so layout rules can be unit-tested on the JVM.
 */
public final class Box {

    public final float left, top, right, bottom;

    public Box(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float width() { return right - left; }

    public float height() { return bottom - top; }

    public float centerX() { return (left + right) / 2f; }

    public float centerY() { return (top + bottom) / 2f; }

    /** This normalised box in pixels of a w x h view. */
    public Box scale(float w, float h) {
        return new Box(left * w, top * h, right * w, bottom * h);
    }

    /** A point inside this box, given as 0..1 fractions of it. */
    public float x(float f) { return left + width() * f; }

    public float y(float f) { return top + height() * f; }

    public Box inset(float dx, float dy) {
        return new Box(left + dx, top + dy, right - dx, bottom - dy);
    }

    public boolean contains(float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public boolean contains(Box o) {
        return o.left >= left - 1e-4f && o.top >= top - 1e-4f && o.right <= right + 1e-4f && o.bottom <= bottom + 1e-4f;
    }

    public boolean intersects(Box o) {
        return left < o.right && o.left < right && top < o.bottom && o.top < bottom;
    }

    @Override
    public String toString() {
        return "Box[" + left + ", " + top + ", " + right + ", " + bottom + "]";
    }
}
