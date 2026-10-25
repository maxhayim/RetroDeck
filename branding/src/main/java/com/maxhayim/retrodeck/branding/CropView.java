package com.maxhayim.retrodeck.branding;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Shows the imported logo on a checkerboard (so transparency is visible) with a crop frame. Drag a
 * corner to resize the frame, or inside it to move it.
 */
final class CropView extends View {

    interface Listener {
        void onCrop(float l, float t, float r, float b);
    }

    private Bitmap bitmap;
    private float cl, ct, cr = 1, cb = 1;
    private Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF img = new RectF();
    private int drag = -1;
    private float lastX, lastY;

    private static final int MOVE = 4;

    CropView(Context c) {
        super(c);
    }

    void set(Bitmap bm, LogoAdjust a, Listener l) {
        bitmap = bm;
        cl = a.cropLeft;
        ct = a.cropTop;
        cr = a.cropRight;
        cb = a.cropBottom;
        listener = l;
        invalidate();
    }

    void setCrop(float l, float t, float r, float b) {
        cl = l;
        ct = t;
        cr = r;
        cb = b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float cell = Math.max(8, Math.min(w, h) / 24f);
        for (int y = 0; y * cell < h; y++) {
            for (int x = 0; x * cell < w; x++) {
                paint.setColor(((x + y) & 1) == 0 ? 0xFF2A2D33 : 0xFF1E2025);
                c.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint);
            }
        }
        if (bitmap == null) return;
        float pad = Math.min(w, h) * 0.06f;
        float f = Math.min((w - 2 * pad) / bitmap.getWidth(), (h - 2 * pad) / bitmap.getHeight());
        float bw = bitmap.getWidth() * f, bh = bitmap.getHeight() * f;
        img.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        paint.setAlpha(255);
        c.drawBitmap(bitmap, null, img, paint);

        RectF crop = cropRect();
        paint.setColor(0x99000000);
        c.drawRect(img.left, img.top, img.right, crop.top, paint);
        c.drawRect(img.left, crop.bottom, img.right, img.bottom, paint);
        c.drawRect(img.left, crop.top, crop.left, crop.bottom, paint);
        c.drawRect(crop.right, crop.top, img.right, crop.bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2, pad * 0.08f));
        paint.setColor(0xFFFFA81E);
        c.drawRect(crop, paint);
        paint.setStyle(Paint.Style.FILL);
        float hr = pad * 0.35f;
        for (float[] p : corners(crop)) c.drawCircle(p[0], p[1], hr, paint);
    }

    private RectF cropRect() {
        return new RectF(img.left + img.width() * cl, img.top + img.height() * ct,
                img.left + img.width() * cr, img.top + img.height() * cb);
    }

    private static float[][] corners(RectF r) {
        return new float[][]{{r.left, r.top}, {r.right, r.top}, {r.left, r.bottom}, {r.right, r.bottom}};
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bitmap == null || img.width() <= 0) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                RectF crop = cropRect();
                float[][] cs = corners(crop);
                float reach = Math.min(getWidth(), getHeight()) * 0.09f;
                drag = -1;
                for (int i = 0; i < 4; i++) {
                    if (Math.hypot(x - cs[i][0], y - cs[i][1]) < reach) drag = i;
                }
                if (drag < 0 && crop.contains(x, y)) drag = MOVE;
                lastX = x;
                lastY = y;
                getParent().requestDisallowInterceptTouchEvent(true);
                return drag >= 0;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = (x - lastX) / img.width(), dy = (y - lastY) / img.height();
                lastX = x;
                lastY = y;
                float min = 0.05f;
                if (drag == MOVE) {
                    float w = cr - cl, h = cb - ct;
                    cl = clamp(cl + dx, 0, 1 - w);
                    ct = clamp(ct + dy, 0, 1 - h);
                    cr = cl + w;
                    cb = ct + h;
                } else {
                    if (drag == 0 || drag == 2) cl = clamp(cl + dx, 0, cr - min);
                    if (drag == 1 || drag == 3) cr = clamp(cr + dx, cl + min, 1);
                    if (drag == 0 || drag == 1) ct = clamp(ct + dy, 0, cb - min);
                    if (drag == 2 || drag == 3) cb = clamp(cb + dy, ct + min, 1);
                }
                invalidate();
                if (listener != null) listener.onCrop(cl, ct, cr, cb);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drag = -1;
                return true;
        }
        return false;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
