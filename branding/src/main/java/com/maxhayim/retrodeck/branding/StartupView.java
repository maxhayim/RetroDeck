package com.maxhayim.retrodeck.branding;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.maxhayim.retrodeck.core.Illumination;
import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.Zones;

import java.io.File;
import java.util.Locale;

/**
 * The start-up sequence, drawn over the stereo. The stereo underneath is already running, so the app
 * is usable at once: a tap skips the sequence, and it never lasts more than six seconds. Each frame
 * only blits the cached logo bitmaps with alpha and colour filters, which the GPU does cheaply.
 */
public final class StartupView extends View {

    public interface Done {
        void onStartupDone();
    }

    private static final int GREEN = 0xFF7BFF8E;

    private final ProfileStore store;
    private VehicleProfile profile;
    private StartupTimeline timeline;
    private long startedAt = -1;
    private boolean still;
    private Done done;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BrandingRenderer renderer;
    private Shader lcdGrid;

    public StartupView(Context context, ProfileStore store) {
        super(context);
        this.store = store;
        renderer = new BrandingRenderer(store, null);
        setClickable(true);
    }

    /** Plays the sequence once, then hides itself and calls done. */
    public void play(VehicleProfile p, Done done) {
        this.profile = p;
        this.done = done;
        this.still = false;
        renderer.setProfile(p);
        timeline = new StartupTimeline(p.startup);
        if (timeline.durationMs() <= 0) {
            finish();
            return;
        }
        setAlpha(1f);
        setVisibility(VISIBLE);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        startedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    /** Shows the finished welcome screen without animating, for the placement editor. */
    public void showStill(VehicleProfile p) {
        profile = p;
        renderer.setProfile(p);
        timeline = new StartupTimeline(p.startup);
        still = true;
        setAlpha(1f);
        setVisibility(VISIBLE);
        invalidate();
    }

    public BrandingRenderer renderer() {
        return renderer;
    }

    public boolean isPlaying() {
        return !still && startedAt >= 0 && getVisibility() == VISIBLE;
    }

    public void skip() {
        if (isPlaying()) finish();
    }

    private void finish() {
        startedAt = -1;
        setVisibility(GONE);
        setLayerType(LAYER_TYPE_NONE, null);
        Done d = done;
        done = null;
        if (d != null) d.onStartupDone();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (still) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) skip();
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (profile == null || timeline == null) return;
        StartupTimeline.Frame f;
        if (still) {
            f = new StartupTimeline.Frame();
            f.logo = f.badge = 1;
            f.lcd = profile.startup.lcdBoot ? 1 : 0;
        } else {
            long t = SystemClock.uptimeMillis() - startedAt;
            if (timeline.finished(t)) {
                finish();
                return;
            }
            f = timeline.at(t);
            setAlpha(1f - f.exit);
        }
        draw(c, f);
        if (!still) postInvalidateOnAnimation();
    }

    private void draw(Canvas c, StartupTimeline.Frame f) {
        int w = getWidth(), h = getHeight();
        float u = Math.min(w, h);
        c.drawColor(0xFF000000);
        String preset = profile.startup.preset;
        int illum = profile.illuminationColor();
        Skin skin = Skins.get(profile.skin);
        RectF zone = new RectF(Zones.STARTUP_BOX.left * w, Zones.STARTUP_BOX.top * h,
                Zones.STARTUP_BOX.right * w, Zones.STARTUP_BOX.bottom * h);

        // CRT: a bright line that opens into the picture.
        float open = 1f;
        if (StartupConfig.CRT.equals(preset) && !still) {
            open = Math.min(1f, f.logo * 1.6f);
            float lineW = w * Math.min(1f, f.logo * 5f);
            float lineH = Math.max(2f, h * open);
            paint.setColorFilter(null);
            if (open < 1f) {
                paint.setColor(Illumination.withAlpha(0xFFE8F4FF, Math.round(255 * (1 - open))));
                c.drawRect(w / 2f - lineW / 2, h / 2f - lineH / 2, w / 2f + lineW / 2, h / 2f + lineH / 2, paint);
            }
        }

        for (int role = 0; role < 2; role++) {
            float a = role == VehicleProfile.MANUFACTURER ? f.logo : f.badge;
            if (a <= 0.001f) continue;
            Placement p = profile.placement(Zones.STARTUP, role);
            if (!p.visible) continue;
            drawStartupLogo(c, skin, zone, role, p, a, f, preset, illum, open, u);
        }

        if (f.power > 0) drawPowerOn(c, w, h, u, f.power, illum);
        if (f.lcd > 0) drawLcd(c, w, h, u, f.lcd, illum, skin);
        if (StartupConfig.GREEN_LCD.equals(preset)) {
            if (lcdGrid == null) lcdGrid = grid(Math.max(3, Math.round(u * 0.006f)));
            paint.setColorFilter(null);
            paint.setShader(lcdGrid);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    private void drawStartupLogo(Canvas c, Skin skin, RectF zone, int role, Placement p, float a,
                                 StartupTimeline.Frame f, String preset, int illum, float open, float u) {
        File file = store.logo(profile, role);
        Bitmap bm = file == null ? null : LogoCache.get().get(file, profile.adjust[role]);
        if (bm == null) {
            // No logo imported: the name in lights instead.
            int color = StartupConfig.GREEN_LCD.equals(preset) ? GREEN : illum;
            renderer.drawLogo(c, skin, Zones.STARTUP, zone, role, p, a * f.flicker, color);
            return;
        }
        RectF r = renderer.logoRect(Zones.STARTUP, zone, role);
        float opacity = profile.adjust[role].opacity;
        int save = c.save();
        switch (preset) {
            case StartupConfig.GLOW: {
                // A halo in the vehicle's accent colour first, then the lamp-lit logo.
                RectF halo = new RectF(r);
                halo.inset(-r.width() * 0.04f, -r.height() * 0.04f);
                paint.setColorFilter(new PorterDuffColorFilter(profile.accentColor, PorterDuff.Mode.SRC_IN));
                paint.setAlpha(Math.round(90 * a * f.flicker * opacity));
                c.drawBitmap(bm, null, halo, paint);
                paint.setColorFilter(null);
                paint.setAlpha(Math.round(255 * a * f.flicker * opacity));
                c.drawBitmap(bm, null, r, paint);
                break;
            }
            case StartupConfig.GREEN_LCD:
                paint.setColorFilter(new PorterDuffColorFilter(GREEN, PorterDuff.Mode.SRC_IN));
                paint.setAlpha(Math.round(255 * a * opacity));
                c.drawBitmap(bm, null, r, paint);
                paint.setColorFilter(null);
                break;
            case StartupConfig.SCAN: {
                // Revealed line by line behind a scanning bar.
                float y = r.top + r.height() * a;
                c.clipRect(r.left - u, r.top, r.right + u, y);
                paint.setColorFilter(null);
                paint.setAlpha(Math.round(255 * opacity));
                c.drawBitmap(bm, null, r, paint);
                c.restoreToCount(save);
                save = c.save();
                if (a < 1f) {
                    paint.setAlpha(255);
                    paint.setColor(illum);
                    paint.setShader(new LinearGradient(0, y - u * 0.03f, 0, y, 0x00000000, illum, Shader.TileMode.CLAMP));
                    c.drawRect(0, y - u * 0.03f, getWidth(), y + u * 0.004f, paint);
                    paint.setShader(null);
                }
                break;
            }
            case StartupConfig.CRT: {
                float sy = Math.max(0.02f, open);
                c.scale(1f, sy, r.centerX(), getHeight() / 2f);
                paint.setColorFilter(null);
                paint.setAlpha(Math.round(255 * Math.min(1f, a * 1.2f) * opacity));
                c.drawBitmap(bm, null, r, paint);
                break;
            }
            default:
                paint.setColorFilter(null);
                paint.setAlpha(Math.round(255 * a * opacity));
                c.drawBitmap(bm, null, r, paint);
        }
        c.restoreToCount(save);
        paint.setAlpha(255);
    }

    /** The retro power-on: the dial lamps light one after another under the logos. */
    private void drawPowerOn(Canvas c, int w, int h, float u, float p, int illum) {
        int dots = 11;
        float y = h * 0.955f, spread = u * 0.5f, r = u * 0.008f;
        int lit = Math.round(p * dots);
        for (int i = 0; i < dots; i++) {
            float x = w / 2f - spread / 2 + spread * i / (dots - 1);
            paint.setColorFilter(null);
            paint.setColor(i < lit ? illum : Illumination.withAlpha(illum, 0x26));
            c.drawCircle(x, y, r, paint);
        }
    }

    /** A small LCD that types a greeting, like a head unit's start-up message. */
    private void drawLcd(Canvas c, int w, int h, float u, float p, int illum, Skin skin) {
        String preset = profile.startup.preset;
        int color = StartupConfig.GREEN_LCD.equals(preset) ? GREEN : illum;
        String msg = "HELLO  " + profile.displayName().toUpperCase(Locale.US);
        if (profile.displayName().equals("Unnamed vehicle")) msg = "HELLO";
        int n = Math.round(msg.length() * Math.min(1f, p * 1.4f));
        String shown = msg.substring(0, Math.max(0, Math.min(msg.length(), n)));
        text.reset();
        text.setAntiAlias(true);
        text.setTypeface(Typeface.create(skin.lcdStyle == Skin.LCD_MODERN ? Typeface.SANS_SERIF : Typeface.MONOSPACE, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(u * 0.045f);
        if (android.os.Build.VERSION.SDK_INT >= 21) text.setLetterSpacing(0.18f);
        while (text.measureText(msg) > w * 0.9f && text.getTextSize() > 8) text.setTextSize(text.getTextSize() * 0.9f);
        text.setColor(color);
        text.setShadowLayer(u * 0.012f, 0, 0, Illumination.withAlpha(color, 0x99));
        boolean cursor = p < 1f && ((SystemClock.uptimeMillis() / 250) % 2 == 0);
        c.drawText(shown + (cursor ? "_" : " "), w / 2f, h * 0.91f - u * 0.03f, text);
        text.clearShadowLayer();
    }

    private static Shader grid(int cell) {
        Bitmap tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < cell; x++) tile.setPixel(x, cell - 1, 0x99000000);
        for (int y = 0; y < cell; y++) tile.setPixel(cell - 1, y, 0x99000000);
        return new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }
}
