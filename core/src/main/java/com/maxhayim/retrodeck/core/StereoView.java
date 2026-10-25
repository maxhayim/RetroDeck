package com.maxhayim.retrodeck.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Draws a skin as a working car stereo: faceplate, illuminated buttons, the LCD with track text and a
 * visualizer, and the volume knob. Branding is not drawn here; an {@link Overlay} is asked to fill each
 * zone, so the stereo works the same with or without the branding module.
 *
 * The parts that never change (faceplate, button bodies, knob body) are drawn once into a cached
 * bitmap, so each animation frame only draws text, bars and logos.
 */
public class StereoView extends View {

    /** Draws into a branding zone. px is the zone in view pixels. */
    public interface Overlay {
        void drawZone(Canvas canvas, Skin skin, String zone, RectF px, int illumination, boolean standby);
    }

    public interface Listener {
        /** A control was tapped; index is the preset number (1-6) for presets, otherwise 0. */
        void onControl(String control, int index);

        void onVolume(int volume);
    }

    private Skin skin = Skins.get(Skins.CLASSIC);
    private int illumination = Illumination.preset(Illumination.AMBER);
    private int accent = 0xFFFF3B30;
    private String visualizer = Visualizers.BARS;
    private StereoState state = new StereoState();
    private Overlay overlay;
    private Listener listener;
    private Levels levels = new Levels.Simulated();
    private final float[] bands = new float[Levels.BANDS];
    private final float[] peaks = new float[Levels.BANDS];

    private boolean showZones;
    private String highlightZone;

    private Bitmap background;
    private Shader dotGrid;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final SimpleDateFormat clock = new SimpleDateFormat("H:mm", Locale.US);

    private String pressed;
    private int pressedPreset;
    private float knobStartY;
    private int knobStartVolume;
    private boolean knobDragging;

    public StereoView(Context context) {
        this(context, null);
    }

    public StereoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
    }

    public void setSkin(Skin skin) {
        if (skin == null || skin == this.skin) return;
        this.skin = skin;
        dropBackground();
    }

    public Skin getSkin() { return skin; }

    public void setIllumination(int color) {
        illumination = color;
        invalidate();
    }

    public int getIllumination() { return illumination; }

    /** The vehicle's second colour: visualizer peaks and the top of the level ladder. */
    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    public void setVisualizer(String id) {
        visualizer = Visualizers.exists(id) ? id : Visualizers.BARS;
        invalidate();
    }

    public void setState(StereoState state) {
        this.state = state;
        invalidate();
    }

    public StereoState getState() { return state; }

    public void setOverlay(Overlay overlay) {
        this.overlay = overlay;
        invalidate();
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setLevels(Levels levels) { this.levels = levels == null ? new Levels.Simulated() : levels; }

    /** Outlines every branding zone; used by the placement editor. */
    public void setShowZones(boolean show, String highlight) {
        showZones = show;
        highlightZone = highlight;
        invalidate();
    }

    /** The zone's box in view pixels. */
    public RectF zoneRect(String zone) {
        Box b = skin.zone(zone);
        if (b == null) return null;
        return px(b);
    }

    private RectF px(Box b) {
        return new RectF(b.left * getWidth(), b.top * getHeight(), b.right * getWidth(), b.bottom * getHeight());
    }

    private void set(RectF r, Box b) {
        r.set(b.left * getWidth(), b.top * getHeight(), b.right * getWidth(), b.bottom * getHeight());
    }

    private void dropBackground() {
        if (background != null) background.recycle();
        background = null;
        dotGrid = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        dropBackground();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dropBackground();
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();

        if (!state.power) {
            drawStandby(c, w, h);
            postInvalidateDelayed(1000);
            return;
        }

        if (background == null) background = buildBackground(w, h);
        if (background != null) c.drawBitmap(background, 0, 0, null);
        else drawStatic(c, w, h);

        float u = Math.min(w, h);
        drawOverlay(c, Zones.HEADER, false);
        drawOverlay(c, Zones.FACEPLATE, false);
        drawOverlay(c, Zones.CORNER, false);

        drawLcd(c, u, now);
        drawButtons(c, u);
        drawKnob(c);

        if (showZones) drawZoneOutlines(c, u);

        boolean animating = state.playing && !Visualizers.OFF.equals(visualizer);
        if (animating || anyLevelAboveZero()) postInvalidateOnAnimation();
        else postInvalidateDelayed(1000);
    }

    private boolean anyLevelAboveZero() {
        for (float b : bands) if (b > 0.01f) return true;
        return false;
    }

    private void drawOverlay(Canvas c, String zone, boolean standby) {
        if (overlay == null) return;
        Box b = skin.zone(zone);
        if (b == null) return;
        RectF r = px(b);
        int save = c.save();
        c.clipRect(r);
        overlay.drawZone(c, skin, zone, r, illumination, standby);
        c.restoreToCount(save);
    }

    private Bitmap buildBackground(int w, int h) {
        try {
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            drawStatic(new Canvas(bm), w, h);
            return bm;
        } catch (OutOfMemoryError e) {
            // Low-memory head unit: draw the faceplate every frame instead.
            return null;
        }
    }

    /** The faceplate, bezels and button bodies. */
    private void drawStatic(Canvas c, int w, int h) {
        float u = Math.min(w, h);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, skin.faceTop, skin.faceBottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        if (skin.lcdStyle == Skin.LCD_SEGMENT) {
            // Brushed metal.
            p.setStrokeWidth(1);
            for (int y = 0; y < h; y += 3) {
                p.setColor(((y * 7919) % 5 < 2) ? 0x0EFFFFFF : 0x0A000000);
                c.drawLine(0, y, w, y, p);
            }
        }

        float r = skin.corner * u;
        RectF lcd = new RectF();
        set(lcd, skin.lcd);
        RectF frame = new RectF(lcd);
        frame.inset(-u * 0.012f, -u * 0.012f);
        p.setColor(skin.lcdFrame);
        c.drawRoundRect(frame, r * 1.4f, r * 1.4f, p);
        p.setColor(skin.lcdBack);
        c.drawRoundRect(lcd, r, r, p);

        if (skin.slot != null) {
            RectF s = new RectF();
            set(s, skin.slot);
            p.setColor(skin.bezel);
            c.drawRoundRect(s, s.height() / 2, s.height() / 2, p);
            p.setColor(0x22FFFFFF);
            c.drawLine(s.left + s.height(), s.bottom - 1, s.right - s.height(), s.bottom - 1, p);
        }

        for (Skin.Control ctl : skin.controls) drawButtonBody(c, p, ctl.box, r);
        for (Box b : skin.presetBoxes()) drawButtonBody(c, p, b, r);

        if (skin.knob != null) {
            RectF k = new RectF();
            set(k, skin.knob);
            float cx = k.centerX(), cy = k.centerY(), rad = Math.min(k.width(), k.height()) / 2 * 0.80f;
            p.setColor(skin.bezel);
            c.drawCircle(cx, cy, rad * 1.12f, p);
            p.setShader(new RadialGradient(cx - rad * 0.3f, cy - rad * 0.35f, rad * 1.4f,
                    lighten(skin.buttonFace, 0.35f), skin.buttonEdge, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, p);
            p.setShader(null);
            // Knurling.
            p.setColor(0x33000000);
            p.setStrokeWidth(Math.max(1, rad * 0.02f));
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48;
                c.drawLine(cx + (float) Math.cos(a) * rad * 0.9f, cy + (float) Math.sin(a) * rad * 0.9f,
                        cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, p);
            }
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(skin.printColor);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(u * 0.028f);
            t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            spacing(t, 0.15f);
            c.drawText("VOLUME", cx, k.bottom + u * 0.03f, t);
        }
    }

    private void drawButtonBody(Canvas c, Paint p, Box box, float r) {
        RectF b = new RectF();
        set(b, box);
        p.setColor(skin.buttonEdge);
        c.drawRoundRect(b, r, r, p);
        RectF in = new RectF(b);
        in.inset(b.height() * 0.06f, b.height() * 0.06f);
        p.setShader(new LinearGradient(0, in.top, 0, in.bottom, lighten(skin.buttonFace, 0.12f), skin.buttonFace,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(in, r * 0.8f, r * 0.8f, p);
        p.setShader(null);
    }

    private void drawLcd(Canvas c, float u, long now) {
        RectF lcd = px(skin.lcd);
        int save = c.save();
        c.clipRect(lcd);

        // Visualizer first, so its overlay logo sits behind the bars.
        Box vz = skin.zone(Zones.VISUALIZER);
        if (vz != null) {
            drawOverlay(c, Zones.VISUALIZER, false);
            levels.read(bands, now, state.playing);
            drawVisualizer(c, px(vz), u);
        }
        drawOverlay(c, Zones.LCD, false);
        drawTrackText(c, px(skin.lcdText), u);

        if (skin.lcdStyle == Skin.LCD_DOT) {
            if (dotGrid == null) dotGrid = dotGridShader(Math.max(3, Math.round(u * 0.006f)));
            paint.setShader(dotGrid);
            c.drawRect(lcd, paint);
            paint.setShader(null);
        }
        // Glass glare.
        paint.setShader(new LinearGradient(0, lcd.top, 0, lcd.bottom, 0x16FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(lcd.left, lcd.top, lcd.right, lcd.centerY(), paint);
        paint.setShader(null);
        c.restoreToCount(save);
    }

    private Shader dotGridShader(int cell) {
        Bitmap tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888);
        int gap = Illumination.withAlpha(skin.lcdBack, 0xB0);
        for (int x = 0; x < cell; x++) tile.setPixel(x, cell - 1, gap);
        for (int y = 0; y < cell; y++) tile.setPixel(cell - 1, y, gap);
        return new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    private void lcdText(int style) {
        text.reset();
        // reset() keeps letter spacing on some Android versions.
        spacing(text, 0f);
        text.setAntiAlias(true);
        if (style == Skin.LCD_MODERN) {
            text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        } else {
            text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        }
    }

    private void drawTrackText(Canvas c, RectF t, float u) {
        boolean modern = skin.lcdStyle == Skin.LCD_MODERN;
        lcdText(skin.lcdStyle);
        float h = t.height();

        // Status line: source, track, clock.
        text.setTextSize(h * 0.17f);
        text.setColor(Illumination.dim(illumination, modern ? 1f : 0.85f));
        spacing(text, modern ? 0.05f : 0.12f);
        float y1 = t.top + h * 0.20f;
        text.setTextAlign(Paint.Align.LEFT);
        String status = state.source + (state.track > 0 ? "   TRK " + two(state.track) : "");
        c.drawText(status, t.left, y1, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(state.durationMs > 0 ? time(state.positionMs) : clock.format(new Date()), t.right, y1, text);

        // Title.
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(h * (modern ? 0.30f : 0.28f));
        spacing(text, modern ? 0f : 0.06f);
        String title = modern ? state.title : state.title.toUpperCase(Locale.US);
        float y2 = t.top + h * 0.62f;
        if (skin.lcdStyle == Skin.LCD_SEGMENT) {
            // Unlit segments behind the lit ones, as on a real vacuum fluorescent display.
            text.setColor(Illumination.withAlpha(illumination, 0x1C));
            c.drawText(fit(ghost(title), t.width()), t.left, y2, text);
        }
        text.setColor(modern ? 0xFFF4F6FA : illumination);
        if (!modern) text.setShadowLayer(h * 0.04f, 0, 0, Illumination.withAlpha(illumination, 0x99));
        c.drawText(fit(title, t.width()), t.left, y2, text);
        text.clearShadowLayer();

        // Artist.
        text.setTextSize(h * 0.17f);
        spacing(text, modern ? 0.02f : 0.08f);
        text.setColor(modern ? 0xFFAEB6C4 : Illumination.dim(illumination, 0.75f));
        String artist = modern ? state.artist : state.artist.toUpperCase(Locale.US);
        c.drawText(fit(artist, t.width()), t.left, t.top + h * 0.90f, text);

        if (state.durationMs > 0 && modern) {
            float f = Math.min(1f, state.positionMs / (float) state.durationMs);
            float y = t.bottom - u * 0.004f;
            paint.setColor(0x33FFFFFF);
            c.drawRect(t.left, y - u * 0.004f, t.right, y, paint);
            paint.setColor(illumination);
            c.drawRect(t.left, y - u * 0.004f, t.left + t.width() * f, y, paint);
        }
    }

    private String fit(String s, float width) {
        if (text.measureText(s) <= width) return s;
        int n = text.breakText(s, true, width - text.measureText("…"), null);
        return s.substring(0, Math.max(0, n)) + "…";
    }

    private void drawVisualizer(Canvas c, RectF v, float u) {
        if (Visualizers.OFF.equals(visualizer)) return;
        boolean modern = skin.lcdStyle == Skin.LCD_MODERN;
        int n = bands.length;
        if (Visualizers.BARS.equals(visualizer)) {
            float gap = v.width() * 0.012f;
            float bw = (v.width() - gap * (n - 1)) / n;
            int segs = 10;
            float sh = v.height() / segs;
            for (int i = 0; i < n; i++) {
                float lv = bands[i];
                peaks[i] = Math.max(lv, peaks[i] - 0.012f);
                float l = v.left + i * (bw + gap);
                if (modern) {
                    paint.setColor(Illumination.withAlpha(illumination, 0x30));
                    rect.set(l, v.top, l + bw, v.bottom);
                    c.drawRoundRect(rect, bw / 3, bw / 3, paint);
                    paint.setColor(illumination);
                    rect.set(l, v.bottom - v.height() * lv, l + bw, v.bottom);
                    c.drawRoundRect(rect, bw / 3, bw / 3, paint);
                } else {
                    int lit = Math.round(lv * segs);
                    for (int s = 0; s < segs; s++) {
                        float top = v.bottom - (s + 1) * sh + sh * 0.18f;
                        paint.setColor(s < lit ? illumination : Illumination.withAlpha(illumination, 0x1A));
                        c.drawRect(l, top, l + bw, v.bottom - s * sh, paint);
                    }
                    int pk = Math.min(segs - 1, Math.round(peaks[i] * segs));
                    if (pk > lit) {
                        paint.setColor(accent);
                        float top = v.bottom - (pk + 1) * sh + sh * 0.18f;
                        c.drawRect(l, top, l + bw, v.bottom - pk * sh, paint);
                    }
                }
            }
        } else if (Visualizers.LADDER.equals(visualizer)) {
            float left = avg(0, n / 2), right = avg(n / 2, n);
            float rowH = v.height() * 0.36f;
            drawLadder(c, v.left, v.top + v.height() * 0.08f, v.width(), rowH, left, "L");
            drawLadder(c, v.left, v.bottom - rowH - v.height() * 0.08f, v.width(), rowH, right, "R");
        } else {
            path.reset();
            int pts = 64;
            long now = SystemClock.uptimeMillis();
            for (int i = 0; i <= pts; i++) {
                float f = i / (float) pts;
                double y = 0;
                for (int b = 0; b < 4; b++) {
                    y += bands[b * 4] * Math.sin(f * Math.PI * 2 * (b + 1) * 1.5 + now / (300.0 + b * 70)) / 4;
                }
                float x = v.left + v.width() * f;
                float yy = v.centerY() - (float) y * v.height() * 0.9f;
                if (i == 0) path.moveTo(x, yy);
                else path.lineTo(x, yy);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, u * 0.005f));
            paint.setColor(illumination);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private float avg(int from, int to) {
        float s = 0;
        for (int i = from; i < to; i++) s += bands[i];
        return s / Math.max(1, to - from);
    }

    private void drawLadder(Canvas c, float x, float y, float w, float h, float level, String label) {
        lcdText(skin.lcdStyle);
        text.setTextSize(h * 0.8f);
        text.setColor(illumination);
        float lw = h * 1.1f;
        c.drawText(label, x, y + h * 0.82f, text);
        int segs = 24;
        float sw = (w - lw) / segs;
        int lit = Math.round(level * segs);
        for (int s = 0; s < segs; s++) {
            boolean hot = s >= segs - 4;
            int col = hot ? accent : illumination;
            paint.setColor(s < lit ? col : Illumination.withAlpha(col, 0x1C));
            float l = x + lw + s * sw;
            c.drawRect(l, y, l + sw * 0.75f, y + h, paint);
        }
    }

    private void drawButtons(Canvas c, float u) {
        text.reset();
        // reset() keeps letter spacing on some Android versions.
        spacing(text, 0f);
        text.setAntiAlias(true);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        spacing(text, 0.08f);
        // One legend size for the whole faceplate, the largest that fits every button.
        float size = Float.MAX_VALUE;
        text.setTextSize(100);
        for (Skin.Control ctl : skin.controls) {
            RectF b = px(ctl.box);
            String widest = ctl.id.equals(Skin.PLAY) ? "PAUSE" : ctl.label;
            size = Math.min(size, Math.min(b.height() * 0.34f, b.width() * 0.7f * 100 / text.measureText(widest)));
        }
        for (Skin.Control ctl : skin.controls) {
            RectF b = px(ctl.box);
            String label = ctl.id.equals(Skin.PLAY) ? (state.playing ? "PAUSE" : "PLAY") : ctl.label;
            drawLegend(c, b, label, ctl.id.equals(pressed), u, size);
        }
        List<Box> presets = skin.presetBoxes();
        for (int i = 0; i < presets.size(); i++) {
            drawLegend(c, px(presets.get(i)), String.valueOf(i + 1), pressedPreset == i + 1, u, size);
        }
    }

    private void drawLegend(Canvas c, RectF b, String label, boolean down, float u, float size) {
        if (down) {
            paint.setColor(Illumination.withAlpha(illumination, 0x40));
            c.drawRoundRect(b, skin.corner * u, skin.corner * u, paint);
        }
        text.setTextSize(size);
        text.setColor(illumination);
        text.setShadowLayer(u * 0.01f, 0, 0, Illumination.withAlpha(illumination, 0x88));
        c.drawText(label, b.centerX(), b.centerY() - (text.descent() + text.ascent()) / 2, text);
        text.clearShadowLayer();
    }

    private void drawKnob(Canvas c) {
        if (skin.knob == null) return;
        RectF k = px(skin.knob);
        float cx = k.centerX(), cy = k.centerY(), rad = Math.min(k.width(), k.height()) / 2 * 0.80f;
        float f = state.volume / (float) StereoState.MAX_VOLUME;
        // Illuminated dial ring: 11 dots, lit up to the volume.
        for (int i = 0; i <= 10; i++) {
            double a = Math.toRadians(135 + 270.0 * i / 10);
            boolean lit = i / 10f <= f + 1e-3f && state.volume > 0;
            paint.setColor(lit ? illumination : Illumination.withAlpha(illumination, 0x30));
            c.drawCircle(cx + (float) Math.cos(a) * rad * 1.25f, cy + (float) Math.sin(a) * rad * 1.25f,
                    rad * 0.045f, paint);
        }
        double a = Math.toRadians(135 + 270.0 * f);
        paint.setColor(illumination);
        paint.setStrokeWidth(rad * 0.08f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(cx + (float) Math.cos(a) * rad * 0.45f, cy + (float) Math.sin(a) * rad * 0.45f,
                cx + (float) Math.cos(a) * rad * 0.82f, cy + (float) Math.sin(a) * rad * 0.82f, paint);
        text.reset();
        // reset() keeps letter spacing on some Android versions.
        spacing(text, 0f);
        text.setAntiAlias(true);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setTextSize(rad * 0.32f);
        text.setColor(illumination);
        c.drawText(two(state.volume), cx, cy + rad * 0.12f, text);
    }

    private void drawStandby(Canvas c, int w, int h) {
        c.drawColor(0xFF000000);
        if (overlay != null) {
            RectF r = px(Zones.STANDBY_BOX);
            overlay.drawZone(c, skin, Zones.STANDBY, r, illumination, true);
        }
        float u = Math.min(w, h);
        text.reset();
        // reset() keeps letter spacing on some Android versions.
        spacing(text, 0f);
        text.setAntiAlias(true);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(skin.lcdStyle == Skin.LCD_MODERN ? Typeface.SANS_SERIF : Typeface.MONOSPACE,
                Typeface.NORMAL));
        text.setTextSize(u * 0.16f);
        text.setColor(Illumination.dim(illumination, 0.55f));
        c.drawText(clock.format(new Date()), w / 2f, h * 0.82f, text);
        text.setTextSize(u * 0.035f);
        spacing(text, 0.3f);
        text.setColor(Illumination.dim(illumination, 0.35f));
        c.drawText("TAP TO POWER ON", w / 2f, h * 0.93f, text);
        if (showZones) drawZoneOutline(c, Zones.STANDBY, px(Zones.STANDBY_BOX), u);
    }

    private void drawZoneOutlines(Canvas c, float u) {
        for (String z : skin.faceplateZones()) drawZoneOutline(c, z, px(skin.zone(z)), u);
    }

    private void drawZoneOutline(Canvas c, String zone, RectF r, float u) {
        boolean hi = zone.equals(highlightZone);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (hi) {
            p.setColor(0x2A00E5FF);
            c.drawRect(r, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, u * (hi ? 0.005f : 0.0028f)));
        p.setColor(hi ? 0xFF00E5FF : 0x9900E5FF);
        p.setPathEffect(new DashPathEffect(new float[]{u * 0.012f, u * 0.008f}, 0));
        c.drawRect(r, p);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(hi ? 0xFF00E5FF : 0xBB00E5FF);
        t.setTextSize(u * 0.024f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(Zones.shortLabel(zone).toUpperCase(Locale.US), r.left + u * 0.006f, r.top + u * 0.026f, t);
    }

    // ---- touch ----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null) return super.onTouchEvent(e);
        float nx = e.getX() / Math.max(1, getWidth()), ny = e.getY() / Math.max(1, getHeight());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = null;
                pressedPreset = 0;
                knobDragging = false;
                if (!state.power) {
                    pressed = Skin.POWER;
                    return true;
                }
                if (skin.knob != null && skin.knob.contains(nx, ny)) {
                    knobDragging = true;
                    knobStartY = e.getY();
                    knobStartVolume = state.volume;
                    return true;
                }
                for (Skin.Control ctl : skin.controls) {
                    if (ctl.box.contains(nx, ny)) pressed = ctl.id;
                }
                List<Box> presets = skin.presetBoxes();
                for (int i = 0; i < presets.size(); i++) {
                    if (presets.get(i).contains(nx, ny)) {
                        pressed = Skin.PRESET;
                        pressedPreset = i + 1;
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (knobDragging) {
                    float step = getHeight() * 0.015f;
                    int v = clampVolume(knobStartVolume + Math.round((knobStartY - e.getY()) / step));
                    if (v != state.volume) listener.onVolume(v);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (knobDragging) {
                    if (Math.abs(e.getY() - knobStartY) < getHeight() * 0.015f) {
                        // A tap: upper half turns it up, lower half down.
                        listener.onVolume(clampVolume(state.volume + (ny < skin.knob.centerY() ? 1 : -1)));
                    }
                } else if (pressed != null) {
                    String id = pressed;
                    int idx = pressedPreset;
                    pressed = null;
                    pressedPreset = 0;
                    listener.onControl(id, idx);
                }
                knobDragging = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = null;
                pressedPreset = 0;
                knobDragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private static int clampVolume(int v) {
        return Math.max(0, Math.min(StereoState.MAX_VOLUME, v));
    }

    // ---- helpers ----

    /** Letter spacing exists from Android 5.0; older head units just get normal spacing. */
    static void spacing(Paint p, float em) {
        if (android.os.Build.VERSION.SDK_INT >= 21) p.setLetterSpacing(em);
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + two((int) (s % 60));
    }

    /** An "8" behind each character, so unlit segments show around the lit ones; gaps stay dark. */
    private static String ghost(String title) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < title.length(); i++) b.append(title.charAt(i) == ' ' ? ' ' : '8');
        return b.toString();
    }

    private static int lighten(int color, float f) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r += (int) ((255 - r) * f);
        g += (int) ((255 - g) * f);
        b += (int) ((255 - b) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
