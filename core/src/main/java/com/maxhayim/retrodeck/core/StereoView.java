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

        /** A key was held down: a preset saves to it, like a radio; DISP shows the full-screen visualizer. */
        void onHeld(String control, int index);
    }

    private Skin skin = Skins.get(Skins.CLASSIC);
    private int illumination = Illumination.preset(Illumination.AMBER);
    private int accent = 0xFFFF3B30;
    private String visualizer = Visualizers.BARS;
    private GifPlayer animation;
    private boolean animationTint = true, animationFull;
    private long animationAt;
    private String keyLighting = KeyLights.AUTO;
    /** This frame's legend colour, and whether it is backlit. */
    private int legend;
    private boolean keysLit;
    private StereoState state = new StereoState();
    private Overlay overlay;
    private Listener listener;
    private Levels levels = new Levels.Simulated();
    private final float[] bands = new float[Levels.BANDS];
    private final float[] peaks = new float[Levels.BANDS];

    /** How long the display shows the volume after it changes. */
    private static final long VOLUME_SHOWN_MS = 2500;

    /** Darkens the faceplate when the key lights are on, as it is at night. */
    private static final int NIGHT_DIM = 0x70000000;
    /** Unlit segments behind smoked glass: there, but barely seen. */
    private static final int UNLIT = 0x06;
    /**
     * Smoked glass over a tinted display. By day it is over everything, the characters too; at night
     * the segments shine through it as bright as the backlit keys.
     */
    private static final int SMOKE = 0x58000000, SMOKE_NIGHT = 0xA8000000;
    /** DSEG by keshikan (SIL Open Font License), in assets/fonts. Loaded once, shared by every view. */
    private static Typeface seg14, seg7;

    private boolean showZones;
    private String highlightZone;

    private Bitmap background;
    /** For a rendered skin: the faceplate with every key pressed in, and whether both are the night ones. */
    private Bitmap pressedArt;
    private boolean artNight;
    private Shader dotGrid, mesh, glare, panel, panelShade;
    private RectF glareFor, panelFor;
    private int panelColor;
    private int lastVolume = -1;
    private long volumeChangedAt = -10_000;
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
        // A rendered skin's night legends are tinted into its cached picture.
        if (skin.art != null) dropBackground();
        else invalidate();
    }

    public int getIllumination() { return illumination; }

    /** The vehicle's second colour: visualizer peaks and the top of the level ladder. */
    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    /** When the key legends are backlit: {@link KeyLights#AUTO}, ON or OFF. */
    public void setKeyLighting(String mode) {
        keyLighting = KeyLights.exists(mode) ? mode : KeyLights.AUTO;
        invalidate();
    }

    public void setVisualizer(String id) {
        visualizer = Visualizers.exists(id) ? id : Visualizers.BARS;
        invalidate();
    }

    /**
     * The animation the Animation visualizer plays, or null. tint lights it in the display's colour;
     * full spreads it across the whole display behind the track text instead of the visualizer strip.
     */
    public void setAnimation(GifPlayer player, boolean tint, boolean full) {
        animation = player;
        animationTint = tint;
        animationFull = full;
        invalidate();
    }

    public GifPlayer getGifPlayer() { return animation; }

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
        if (pressedArt != null) pressedArt.recycle();
        pressedArt = null;
        dotGrid = null;
        mesh = null;
        glare = null;
        panel = null;
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
        if (fullscreen) {
            drawFullscreen(c, w, h, now);
            if (state.playing || anyLevelAboveZero()) postInvalidateOnAnimation();
            else postInvalidateDelayed(1000);
            return;
        }

        keysLit = KeyLights.lit(keyLighting, java.util.Calendar.getInstance());
        legend = !keysLit ? skin.keyPrint : skin.keyLight != 0 ? skin.keyLight : illumination;
        boolean art = skin.art != null;
        // A rendered skin has a picture for day and one for night.
        if (art && background != null && artNight != keysLit) dropBackground();
        if (background == null) background = buildBackground(w, h);
        if (background != null) c.drawBitmap(background, 0, 0, null);
        else drawStatic(c, w, h);
        art = art && background != null;

        float u = Math.min(w, h);
        // Logos are printed on the faceplate: at night they fall dark with it.
        int logos = keysLit && art ? 0x60 : 0xFF;
        drawOverlay(c, Zones.HEADER, logos);
        drawOverlay(c, Zones.FACEPLATE, logos);
        drawOverlay(c, Zones.CORNER, logos);

        // At night no daylight falls on the faceplate: it goes dark, and only the backlit legends and
        // the display stand out. A rendered night picture is already dark.
        if (keysLit && !art) c.drawColor(NIGHT_DIM);

        drawLcd(c, u, now);
        if (art) drawPressedArt(c, w, h);
        else drawButtons(c, u);
        drawKnob(c);

        if (showZones) drawZoneOutlines(c, u);

        boolean animating = state.playing && !Visualizers.OFF.equals(visualizer);
        if (animating || anyLevelAboveZero()) postInvalidateOnAnimation();
        else if (now - volumeChangedAt < VOLUME_SHOWN_MS) postInvalidateDelayed(250);
        else postInvalidateDelayed(1000);
    }

    private boolean anyLevelAboveZero() {
        for (float b : bands) if (b > 0.01f) return true;
        return false;
    }

    private void drawOverlay(Canvas c, String zone, boolean standby) {
        drawOverlay(c, zone, standby, 0xFF);
    }

    private void drawOverlay(Canvas c, String zone, int alpha) {
        drawOverlay(c, zone, false, alpha);
    }

    @SuppressWarnings("deprecation")
    private void drawOverlay(Canvas c, String zone, boolean standby, int alpha) {
        if (overlay == null) return;
        Box b = skin.zone(zone);
        if (b == null) return;
        RectF r = px(b);
        int save = alpha < 0xFF ? c.saveLayerAlpha(r, alpha, Canvas.ALL_SAVE_FLAG) : c.save();
        c.clipRect(r);
        overlay.drawZone(c, skin, zone, r, illumination, standby);
        c.restoreToCount(save);
    }

    /** A pressed key of a rendered skin: that key's patch of the all-pressed picture. */
    private void drawPressedArt(Canvas c, int w, int h) {
        Box key = null;
        if (pressedPreset > 0) {
            List<Box> presets = skin.presetBoxes();
            if (pressedPreset <= presets.size()) key = presets.get(pressedPreset - 1);
        } else if (pressed != null) {
            for (Skin.Control ctl : skin.controls) if (ctl.id.equals(pressed)) key = ctl.box;
        }
        if (key == null) return;
        if (pressedArt == null) pressedArt = withGlow(loadArt(skin.art + (keysLit ? "/night-pressed.webp" : "/day-pressed.webp"), w, h), w, h);
        if (pressedArt == null) return;
        android.graphics.Rect r = new android.graphics.Rect(Math.round(key.left * w), Math.round(key.top * h),
                Math.round(key.right * w), Math.round(key.bottom * h));
        c.drawBitmap(pressedArt, r, r, null);
    }

    /** A rendered faceplate scaled to the view, or null if it can't be read or there's no memory. */
    private Bitmap loadArt(String path, int w, int h) {
        android.app.ActivityManager am = (android.app.ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inPreferredConfig = am != null && am.isLowRamDevice() ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        try (java.io.InputStream in = getContext().getAssets().open(path)) {
            Bitmap raw = android.graphics.BitmapFactory.decodeStream(in, null, o);
            if (raw == null || (raw.getWidth() == w && raw.getHeight() == h)) return raw;
            Bitmap scaled = Bitmap.createScaledBitmap(raw, w, h, true);
            if (scaled != raw) raw.recycle();
            return scaled;
        } catch (java.io.IOException | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * At night, the backlit legends of a rendered skin that has them as a separate layer (glow.webp,
     * white), in the legend colour: the dash lighting, unless the skin has its own.
     */
    private Bitmap withGlow(Bitmap base, int w, int h) {
        if (base == null || !keysLit) return base;
        Bitmap glow = loadArt(skin.art + "/glow.webp", w, h);
        if (glow == null) return base;
        try {
            if (!base.isMutable()) {
                Bitmap copy = base.copy(base.getConfig(), true);
                if (copy == null) return base;
                base.recycle();
                base = copy;
            }
            Paint add = new Paint(Paint.FILTER_BITMAP_FLAG);
            add.setColorFilter(new android.graphics.LightingColorFilter(legend & 0xFFFFFF, 0));
            add.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD));
            new Canvas(base).drawBitmap(glow, 0, 0, add);
        } catch (OutOfMemoryError e) {
            // The faceplate without its lit legends is still usable.
        } finally {
            glow.recycle();
        }
        return base;
    }

    private Bitmap buildBackground(int w, int h) {
        if (skin.art != null) {
            artNight = keysLit;
            Bitmap art = withGlow(loadArt(skin.art + (keysLit ? "/night.webp" : "/day.webp"), w, h), w, h);
            if (art != null) return art;
        }
        // Full colour keeps the metal and glass gradients smooth; low-RAM head units get 16-bit.
        android.app.ActivityManager am = (android.app.ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        boolean lowRam = am != null && am.isLowRamDevice();
        try {
            Bitmap bm = Bitmap.createBitmap(w, h, lowRam ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
            Faceplate.draw(new Canvas(bm), skin, w, h);
            return bm;
        } catch (OutOfMemoryError e) {
            // Not even that: draw a plain faceplate every frame instead.
            return null;
        }
    }

    /** A plain faceplate, for when there is no memory for the cached one. */
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
        for (Skin.Control ctl : skin.decor) drawButtonBody(c, p, ctl.box, r);
        for (Skin.Control ctl : skin.trims) drawButtonBody(c, p, ctl.box, r);
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
        if (skin.lcdStyle == Skin.LCD_REFLECTIVE) drawPanel(c, lcd, u);
        if (skin.lcdGlass != 0) drawGlass(c, lcd, u);

        // A whole-display animation goes behind everything else on it.
        boolean fullAnimation = Visualizers.ANIMATION.equals(visualizer) && animationFull && animation != null;
        if (fullAnimation) {
            levels.read(bands, now, state.playing);
            drawAnimation(c, lcd, true);
        }
        // Visualizer first, so its overlay logo sits behind the bars.
        Box vz = skin.zone(Zones.VISUALIZER);
        if (vz != null) {
            drawOverlay(c, Zones.VISUALIZER, false);
            if (!fullAnimation) {
                levels.read(bands, now, state.playing);
                drawVisualizer(c, px(vz), u);
            }
        }
        drawOverlay(c, Zones.LCD, false);
        if (skin.segmentFont && loadSegmentFonts()) drawSegmentText(c, px(skin.lcdText), u);
        else drawTrackText(c, px(skin.lcdText), u);

        if (skin.lcdStyle == Skin.LCD_DOT) {
            if (dotGrid == null) dotGrid = dotGridShader(Math.max(3, Math.round(u * 0.006f)));
            paint.setShader(dotGrid);
            c.drawRect(lcd, paint);
            paint.setShader(null);
        } else if (skin.lcdStyle == Skin.LCD_SEGMENT) {
            // The fine control-grid mesh a vacuum fluorescent display shows over its segments.
            if (mesh == null) mesh = meshShader(Math.max(3, Math.round(u * 0.0055f)));
            paint.setShader(mesh);
            c.drawRect(lcd, paint);
            paint.setShader(null);
        }
        if (skin.lcdGlass != 0 && !keysLit) {
            paint.setColor(SMOKE);
            c.drawRect(lcd, paint);
        }
        // The front glass: a diagonal reflection and a bright rim along the top.
        if (glare == null || !lcd.equals(glareFor)) {
            glareFor = new RectF(lcd);
            glare = new LinearGradient(lcd.left, lcd.top, lcd.left + lcd.width() * 0.45f, lcd.bottom,
                    new int[]{0x20FFFFFF, 0x12FFFFFF, 0x03FFFFFF, 0x00FFFFFF, 0x07FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.30f, 0.40f, 0.60f, 0.66f, 0.76f}, Shader.TileMode.CLAMP);
        }
        paint.setShader(glare);
        c.drawRect(lcd, paint);
        paint.setShader(null);
        paint.setColor(0x22FFFFFF);
        c.drawRect(lcd.left, lcd.top, lcd.right, lcd.top + Math.max(1, u * 0.002f), paint);
        c.restoreToCount(save);
    }

    /**
     * A reflective LCD's panel, lit from behind in the illumination colour through a grey-green
     * polariser, with a shadow where the bezel overhangs it.
     */
    private void drawPanel(Canvas c, RectF lcd, float u) {
        int light = skin.backlight(illumination);
        if (panel == null || panelColor != light || !lcd.equals(panelFor)) {
            panelColor = light;
            panelFor = new RectF(lcd);
            // A backlight of the display's own is a strong one: it shows through more of the polariser.
            int top = Illumination.mix(0xFFA9BBB0, light, skin.backlight != 0 ? 0.62f : 0.38f);
            panel = new LinearGradient(0, lcd.top, 0, lcd.bottom, top, Illumination.dim(top, 0.8f), Shader.TileMode.CLAMP);
            panelShade = new LinearGradient(0, lcd.top, 0, lcd.top + u * 0.03f, 0x70000000, 0x00000000, Shader.TileMode.CLAMP);
        }
        paint.setShader(panel);
        c.drawRect(lcd, paint);
        paint.setShader(panelShade);
        c.drawRect(lcd, paint);
        paint.setShader(null);
    }

    /** Tinted filter glass: its colour, darker where the bezel overhangs it. */
    private void drawGlass(Canvas c, RectF lcd, float u) {
        paint.setColor(skin.lcdGlass);
        c.drawRect(lcd, paint);
        if (keysLit) {
            // In the dark cabin the plum is barely seen.
            paint.setColor(SMOKE_NIGHT);
            c.drawRect(lcd, paint);
        }
        paint.setShader(new LinearGradient(0, lcd.top, 0, lcd.bottom,
                new int[]{0x90000000, 0x00000000, 0x00000000, 0x60000000}, new float[]{0f, 0.18f, 0.8f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(lcd, paint);
        paint.setShader(null);
    }

    private Shader meshShader(int cell) {
        Bitmap tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888);
        int line = 0x55000000;
        for (int x = 0; x < cell; x++) tile.setPixel(x, 0, line);
        for (int y = 0; y < cell; y++) tile.setPixel(0, y, line);
        return new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
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
        boolean reflective = skin.lcdStyle == Skin.LCD_REFLECTIVE;
        int ink = skin.lcdInk(illumination);
        lcdText(skin.lcdStyle);
        float h = t.height();

        // Status line: source, track, clock.
        text.setTextSize(h * 0.17f);
        text.setColor(reflective ? ink : Illumination.dim(illumination, modern ? 1f : 0.85f));
        spacing(text, modern ? 0.05f : 0.12f);
        float y1 = t.top + h * 0.20f;
        text.setTextAlign(Paint.Align.LEFT);
        String status = state.source + (state.track > 0 ? "   TRK " + two(state.track) : "");
        boolean segments = skin.lcdStyle == Skin.LCD_SEGMENT || reflective;
        if (segments) ghostText(c, status, t.left, y1, t.width() * 0.6f);
        c.drawText(status, t.left, y1, text);
        text.setTextAlign(Paint.Align.RIGHT);
        long now = SystemClock.uptimeMillis();
        if (state.volume != lastVolume) {
            if (lastVolume >= 0) volumeChangedAt = now;
            lastVolume = state.volume;
        }
        String right = now - volumeChangedAt < VOLUME_SHOWN_MS ? "VOL " + two(state.volume)
                : state.durationMs > 0 ? time(state.positionMs) : clock.format(new Date());
        c.drawText(right, t.right, y1, text);

        // Title.
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(h * (modern ? 0.30f : 0.28f));
        spacing(text, modern ? 0f : 0.06f);
        String title = modern ? state.title : state.title.toUpperCase(Locale.US);
        float y2 = t.top + h * 0.62f;
        if (segments) {
            // Unlit segments behind the lit ones, as on a real vacuum fluorescent display or LCD.
            text.setColor(Illumination.withAlpha(ink, reflective ? 0x12 : 0x1C));
            c.drawText(fit(ghost(title), t.width()), t.left, y2, text);
        }
        text.setColor(modern ? 0xFFF4F6FA : ink);
        if (!modern && !reflective) text.setShadowLayer(h * 0.04f, 0, 0, Illumination.withAlpha(illumination, 0x99));
        c.drawText(fit(title, t.width()), t.left, y2, text);
        text.clearShadowLayer();

        // Artist.
        text.setTextSize(h * 0.17f);
        spacing(text, modern ? 0.02f : 0.08f);
        text.setColor(modern ? 0xFFAEB6C4 : reflective ? Illumination.withAlpha(ink, 0xD0) : Illumination.dim(illumination, 0.75f));
        String line = state.artistLine(now);
        if (state.notice != null && now < state.noticeUntil) postInvalidateDelayed(state.noticeUntil - now + 20);
        String artist = modern ? line : line.toUpperCase(Locale.US);
        if (segments) ghostText(c, fit(artist, t.width()), t.left, t.top + h * 0.90f, t.width());
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

    private boolean loadSegmentFonts() {
        if (seg14 == null) {
            try {
                seg14 = Typeface.createFromAsset(getContext().getAssets(), "fonts/DSEG14Classic-BoldItalic.ttf");
                seg7 = Typeface.createFromAsset(getContext().getAssets(), "fonts/DSEG7Classic-BoldItalic.ttf");
            } catch (RuntimeException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * A fluorescent display as fitted to factory units around 1990: a row of printed indicators that light
     * when they apply, a 7-segment clock, and 14-segment characters in fixed cells, every unlit segment
     * faintly visible behind the glass.
     */
    private void drawSegmentText(Canvas c, RectF t, float u) {
        int ink = skin.lcdInk(illumination);
        float h = t.height();
        text.reset();
        spacing(text, 0f);
        text.setAntiAlias(true);
        text.setTextAlign(Paint.Align.LEFT);

        // Indicators, printed on the glass like ST and FM.
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        text.setTextSize(h * 0.14f);
        spacing(text, 0.06f);
        float y1 = t.top + h * 0.20f, x = t.left;
        String[] names = {"ST", state.source, "PLAY", "RPT"};
        boolean[] lit = {state.playing, true, state.playing, false};
        for (int i = 0; i < names.length; i++) {
            segmentGlow(lit[i], ink, h);
            c.drawText(names[i], x, y1, text);
            text.clearShadowLayer();
            x += text.measureText(names[i]) + h * 0.16f;
        }

        // Track and time on the right, in 7-segment digits; the volume while it changes.
        long now = SystemClock.uptimeMillis();
        if (state.volume != lastVolume) {
            if (lastVolume >= 0) volumeChangedAt = now;
            lastVolume = state.volume;
        }
        boolean volume = now - volumeChangedAt < VOLUME_SHOWN_MS;
        String right = volume ? "VOL " + two(state.volume)
                : (state.track > 0 ? two(state.track) + " " : "") + (state.durationMs > 0 ? time(state.positionMs) : clock.format(new Date()));
        text.setTypeface(volume ? seg14 : seg7);
        text.setTextSize(h * 0.19f);
        spacing(text, 0f);
        text.setTextAlign(Paint.Align.RIGHT);
        segmentLine(c, segments(right, volume), t.right, y1 + h * 0.02f, ink, h, volume ? '~' : '8');
        text.setTextAlign(Paint.Align.LEFT);

        // Title and artist in fixed 14-segment cells.
        text.setTypeface(seg14);
        text.setTextSize(h * 0.30f);
        spacing(text, 0.04f);
        segmentCells(c, state.title, t.left, t.top + h * 0.64f, t.width(), ink, h);
        text.setTextSize(h * 0.17f);
        spacing(text, 0.08f);
        segmentCells(c, state.artistLine(SystemClock.uptimeMillis()), t.left, t.top + h * 0.93f, t.width(), ink, h);
    }

    /** A full row of character cells, unlit ones included, with the text lit from the left. */
    private void segmentCells(Canvas c, String s, float x, float y, float width, int ink, float h) {
        int cells = Math.max(1, (int) (width / text.measureText("~")));
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < cells; i++) all.append('~');
        text.setColor(Illumination.withAlpha(ink, UNLIT));
        c.drawText(all.toString(), x, y, text);
        String lit = segments(s, true);
        if (lit.length() > cells) lit = lit.substring(0, cells);
        segmentGlow(true, ink, h);
        c.drawText(lit, x, y, text);
        text.clearShadowLayer();
    }

    /** One run of digits, its unlit segments behind it. */
    private void segmentLine(Canvas c, String s, float x, float y, int ink, float h, char all) {
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            g.append(ch == '.' || ch == ':' ? ch : all);
        }
        text.setColor(Illumination.withAlpha(ink, UNLIT));
        c.drawText(g.toString(), x, y, text);
        segmentGlow(true, ink, h);
        c.drawText(s, x, y, text);
        text.clearShadowLayer();
    }

    /** Lit phosphor: a near-white core with its colour bleeding around it. Unlit print stays faint. */
    /** At night a phosphor display's lit segments are as bright as the backlit keys: near white at the core. */
    private int bright(int color) {
        return neon() ? Illumination.mix(color, 0xFFFFFFFF, 0.35f) : color;
    }

    private boolean neon() {
        return skin.phosphor != 0 && keysLit;
    }

    private void segmentGlow(boolean lit, int ink, float h) {
        if (lit) {
            text.setColor(bright(ink));
            text.setShadowLayer(h * (neon() ? 0.08f : 0.06f), 0, 0, Illumination.withAlpha(ink, neon() ? 0xE0 : 0xB0));
        } else {
            text.setColor(Illumination.withAlpha(ink, UNLIT));
        }
    }

    /**
     * What a segment display can show: capitals, digits and a little punctuation, accents dropped. A
     * blank cell is "!" in the DSEG fonts, as their space is narrower than a character.
     */
    static String segments(String s, boolean letters) {
        String plain = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).toUpperCase(Locale.US);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < plain.length(); i++) {
            char ch = plain.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) continue;
            boolean ok = ch >= '0' && ch <= '9' || ch == '.' || ch == ':' || ch == '-'
                    || letters && (ch >= 'A' && ch <= 'Z' || "/'+*()?,".indexOf(ch) >= 0);
            b.append(ok ? ch : '!');
        }
        return b.toString();
    }

    /** Unlit segments behind a line of display text; keeps the paint's own colour. */
    private void ghostText(Canvas c, String s, float x, float y, float width) {
        int color = text.getColor();
        Paint.Align align = text.getTextAlign();
        text.setTextAlign(Paint.Align.LEFT);
        boolean reflective = skin.lcdStyle == Skin.LCD_REFLECTIVE;
        text.setColor(Illumination.withAlpha(skin.lcdInk(illumination), reflective ? 0x10 : 0x16));
        c.drawText(fit(ghost(s), width), x, y, text);
        text.setColor(color);
        text.setTextAlign(align);
    }

    private String fit(String s, float width) {
        if (text.measureText(s) <= width) return s;
        int n = text.breakText(s, true, width - text.measureText("…"), null);
        return s.substring(0, Math.max(0, n)) + "…";
    }

    /** Dark segments on a reflective LCD; in full screen there's no panel behind them, so they're drawn lit. */
    private boolean reflective() {
        return skin.lcdStyle == Skin.LCD_REFLECTIVE && !fullscreen;
    }

    /** The colour the visualizer draws in: the display's ink, or a reflective one's backlight in full screen. */
    private int displayInk() {
        return skin.lcdStyle == Skin.LCD_REFLECTIVE && fullscreen ? skin.backlight(illumination) : skin.lcdInk(illumination);
    }

    private void drawVisualizer(Canvas c, RectF v, float u) {
        if (Visualizers.OFF.equals(visualizer)) return;
        if (Visualizers.ANIMATION.equals(visualizer)) {
            if (animation != null) drawAnimation(c, v, false);
            else drawNoAnimation(c, v);
            return;
        }
        boolean modern = skin.lcdStyle == Skin.LCD_MODERN;
        boolean reflective = reflective();
        int ink = displayInk();
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
                    float halo = Math.max(1, u * 0.004f);
                    for (int s = 0; s < segs; s++) {
                        float top = v.bottom - (s + 1) * sh + sh * 0.18f;
                        // Colour displays shade each column from the illumination up into the accent.
                        int col = skin.gradientBars ? Illumination.mix(illumination, accent, s / (float) (segs - 1)) : ink;
                        if (s < lit && !reflective) {
                            // Lit phosphor bleeds light around itself.
                            paint.setColor(Illumination.withAlpha(col, neon() ? 0x50 : 0x30));
                            c.drawRect(l - halo, top - halo, l + bw + halo, v.bottom - s * sh + halo, paint);
                        }
                        paint.setColor(s < lit ? bright(col) : Illumination.withAlpha(col, reflective ? 0x10 : skin.phosphor != 0 ? UNLIT : 0x16));
                        c.drawRect(l, top, l + bw, v.bottom - s * sh, paint);
                    }
                    int pk = Math.min(segs - 1, Math.round(peaks[i] * segs));
                    if (pk > lit) {
                        // One-colour displays have no second colour for the peaks.
                        paint.setColor(reflective || skin.phosphor != 0 ? ink : accent);
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
            paint.setColor(ink);
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /**
     * The animation, fitted into the visualizer strip, or covering the whole display (dimmed, so the
     * track text stays readable). It runs faster when the music is loud and stands still when paused.
     */
    private void drawAnimation(Canvas c, RectF area, boolean cover) {
        long now = SystemClock.uptimeMillis();
        long dt = animationAt == 0 ? 0 : Math.min(200, now - animationAt);
        animationAt = now;
        if (state.playing) animation.advance(dt, 0.5f + 1.5f * avg(0, bands.length));
        int tint = animationTint ? (neon() ? bright(displayInk()) : displayInk()) : 0;
        android.graphics.Bitmap bm = animation.bitmap(tint);
        if (bm == null) return;
        float s = cover ? Math.max(area.width() / bm.getWidth(), area.height() / bm.getHeight())
                : Math.min(area.width() / bm.getWidth(), area.height() / bm.getHeight());
        float w = bm.getWidth() * s, h = bm.getHeight() * s;
        rect.set(area.centerX() - w / 2, area.centerY() - h / 2, area.centerX() + w / 2, area.centerY() + h / 2);
        paint.setFilterBitmap(true);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(cover ? 0x90 : 0xFF);
        int save = c.save();
        c.clipRect(area);
        c.drawBitmap(bm, null, rect, paint);
        c.restoreToCount(save);
        paint.setAlpha(0xFF);
        paint.setFilterBitmap(false);
    }

    /** Animation chosen but no GIF yet: say where to get one. */
    private void drawNoAnimation(Canvas c, RectF v) {
        lcdText(skin.lcdStyle);
        text.setTextSize(v.height() * 0.28f);
        text.setColor(Illumination.withAlpha(skin.lcdInk(illumination), 0x90));
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(fit("NO ANIMATION  ·  ADD A GIF IN VEHICLE BRANDING", v.width()), v.centerX(),
                v.centerY() - (text.descent() + text.ascent()) / 2, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private float avg(int from, int to) {
        float s = 0;
        for (int i = from; i < to; i++) s += bands[i];
        return s / Math.max(1, to - from);
    }

    private void drawLadder(Canvas c, float x, float y, float w, float h, float level, String label) {
        lcdText(skin.lcdStyle);
        int ink = displayInk();
        boolean reflective = reflective();
        text.setTextSize(h * 0.8f);
        text.setColor(ink);
        float lw = h * 1.1f;
        c.drawText(label, x, y + h * 0.82f, text);
        int segs = 24;
        float sw = (w - lw) / segs;
        int lit = Math.round(level * segs);
        for (int s = 0; s < segs; s++) {
            boolean hot = s >= segs - 4;
            int col = hot && !reflective ? accent : ink;
            paint.setColor(s < lit ? col : Illumination.withAlpha(col, reflective ? 0x12 : 0x1C));
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
        // One legend size for the working keys, the largest that fits every one; the keys that are only
        // part of the design get their own, as they are often smaller.
        float size = legendSize(skin.controls);
        List<Box> presets = skin.presetBoxes();
        if (skin.presetLabels != null) {
            for (int i = 0; i < presets.size(); i++) size = Math.min(size, fitLegend(px(presets.get(i)), skin.presetLabels[i]));
        }
        float decorSize = legendSize(skin.decor);
        for (Skin.Control ctl : skin.controls) {
            RectF b = px(ctl.box);
            String label = ctl.id.equals(Skin.PLAY) ? (state.playing ? "PAUSE" : "PLAY") : ctl.label;
            drawLegend(c, b, label, ctl.id.equals(pressed), u, size, ctl.round);
        }
        for (Skin.Control ctl : skin.decor) drawLegend(c, px(ctl.box), ctl.label, false, u, decorSize, ctl.round);
        for (int i = 0; i < presets.size(); i++) {
            RectF b = px(presets.get(i));
            boolean down = pressedPreset == i + 1;
            if (skin.presetLabels == null) {
                drawLegend(c, b, String.valueOf(i + 1), down, u, size, false);
                continue;
            }
            // A tape key: its function on the key, the preset number small in the corner.
            rect.set(b.left, b.top - b.height() * 0.12f, b.right, b.bottom - b.height() * 0.12f);
            drawLegend(c, b, null, down, u, size, false);
            drawLegend(c, rect, skin.presetLabels[i], false, u, size, false);
            text.setTextSize(size * 0.75f);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(Illumination.withAlpha(legend, 0xC0));
            c.drawText(String.valueOf(i + 1), b.right - b.width() * 0.08f, b.bottom - b.height() * 0.2f + (down ? u * 0.004f : 0), text);
            text.setTextAlign(Paint.Align.CENTER);
        }
    }

    private float legendSize(List<Skin.Control> keys) {
        float size = Float.MAX_VALUE;
        for (Skin.Control ctl : keys) {
            RectF b = px(ctl.box);
            if (ctl.round) {
                // The legend has to fit inside the circle.
                float d = Math.min(b.width(), b.height()) * 0.72f;
                b.set(b.centerX() - d / 2, b.centerY() - d / 2, b.centerX() + d / 2, b.centerY() + d / 2);
            }
            size = Math.min(size, fitLegend(b, Skin.PLAY.equals(ctl.id) ? "PAUSE" : ctl.label));
        }
        return size;
    }

    private float fitLegend(RectF b, String label) {
        text.setTextSize(100);
        return Math.min(b.height() * 0.34f, b.width() * 0.7f * 100 / Math.max(1, text.measureText(label)));
    }

    /** A key's lit legend; a null label draws only the pressed look. */
    private void drawLegend(Canvas c, RectF b, String label, boolean down, float u, float size, boolean round) {
        float sink = 0;
        if (down) {
            // A pressed key goes into its hole: darker, its legend lower, the backlight brighter around it.
            sink = u * 0.004f;
            float r = round ? Math.min(b.width(), b.height()) / 2 : skin.corner * u;
            paint.setColor(0x66000000);
            if (round) c.drawCircle(b.centerX(), b.centerY(), r, paint);
            else c.drawRoundRect(b, r, r, paint);
            paint.setColor(Illumination.withAlpha(keysLit ? legend : 0xFF000000, 0x22));
            if (round) c.drawCircle(b.centerX(), b.centerY(), r, paint);
            else c.drawRoundRect(b, r, r, paint);
        }
        if (label == null) return;
        text.setTextSize(size);
        text.setColor(legend);
        // Backlit legends glow; printed ones are just paint.
        if (keysLit) text.setShadowLayer(u * 0.012f, 0, 0, Illumination.withAlpha(legend, down ? 0xCC : 0x99));
        // The legend sits on the key face, a little above the key's centre.
        float faceY = skin.lcdStyle == Skin.LCD_MODERN || round ? b.centerY() : b.centerY() - b.height() * 0.04f;
        c.drawText(label, b.centerX(), faceY + sink - (text.descent() + text.ascent()) / 2, text);
        text.clearShadowLayer();
    }

    private void drawKnob(Canvas c) {
        if (skin.knob == null) return;
        RectF k = px(skin.knob);
        float cx = k.centerX(), cy = k.centerY(), rad = Math.min(k.width(), k.height()) / 2 * 0.80f;
        float f = state.volume / (float) StereoState.MAX_VOLUME;
        // Illuminated dial ring: 11 dots, lit up to the volume.
        for (int i = 0; i <= 10 && skin.knobDots; i++) {
            double a = Math.toRadians(135 + 270.0 * i / 10);
            boolean lit = keysLit && i / 10f <= f + 1e-3f && state.volume > 0;
            float dx = cx + (float) Math.cos(a) * rad * 1.25f, dy = cy + (float) Math.sin(a) * rad * 1.25f;
            if (lit) {
                paint.setColor(Illumination.withAlpha(illumination, 0x38));
                c.drawCircle(dx, dy, rad * 0.09f, paint);
            }
            paint.setColor(lit ? illumination : 0xFF1A1B1D);
            c.drawCircle(dx, dy, rad * 0.045f, paint);
        }
        // The pointer: a groove cut into the cap, lit from inside. The display shows the number.
        double a = Math.toRadians(135 + 270.0 * f);
        float x1 = cx + (float) Math.cos(a) * rad * 0.40f, y1 = cy + (float) Math.sin(a) * rad * 0.40f;
        float x2 = cx + (float) Math.cos(a) * rad * 0.74f, y2 = cy + (float) Math.sin(a) * rad * 0.74f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(rad * 0.11f);
        paint.setColor(0xCC000000);
        c.drawLine(x1, y1, x2, y2, paint);
        if (keysLit) {
            paint.setStrokeWidth(rad * 0.16f);
            paint.setColor(Illumination.withAlpha(legend, 0x30));
            c.drawLine(x1, y1, x2, y2, paint);
        }
        paint.setStrokeWidth(rad * 0.06f);
        paint.setColor(legend);
        c.drawLine(x1, y1, x2, y2, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    // ---- full screen ----

    private boolean fullscreen, swallowTouch;

    /** The visualizer over the whole screen, with the track and clock along the bottom. */
    public void setFullscreen(boolean on) {
        if (fullscreen == on) return;
        fullscreen = on;
        invalidate();
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    private void drawFullscreen(Canvas c, int w, int h, long now) {
        c.drawColor(0xFF000000);
        float u = Math.min(w, h);
        levels.read(bands, now, state.playing);
        RectF area = new RectF(w * 0.05f, h * 0.07f, w * 0.95f, h * 0.80f);
        String was = visualizer;
        // Off still shows something here: it was asked for.
        if (Visualizers.OFF.equals(visualizer)) visualizer = Visualizers.BARS;
        if (Visualizers.ANIMATION.equals(visualizer) && animation != null) drawAnimation(c, new RectF(0, 0, w, h * 0.84f), false);
        else drawVisualizer(c, area, u);
        visualizer = was;

        // Track and clock in the display's lettering.
        lcdText(skin.lcdStyle);
        boolean modern = skin.lcdStyle == Skin.LCD_MODERN;
        int ink = skin.lcdStyle == Skin.LCD_REFLECTIVE ? skin.backlight(illumination) : skin.lcdInk(illumination);
        float y = h * 0.93f;
        text.setTextSize(u * 0.055f);
        spacing(text, modern ? 0.02f : 0.08f);
        text.setColor(ink);
        String clk = clock.format(new Date());
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(clk, w * 0.95f, y, text);
        float clockW = text.measureText(clk);
        text.setTextAlign(Paint.Align.LEFT);
        String line = state.title;
        String second = state.artistLine(now);
        if (second != null && !second.isEmpty()) line += "  ·  " + second;
        if (!modern) line = line.toUpperCase(Locale.US);
        c.drawText(fit(line, w * 0.90f - clockW - u * 0.05f), w * 0.05f, y, text);
        if (state.notice != null && now < state.noticeUntil) postInvalidateDelayed(state.noticeUntil - now + 20);
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
        // Full screen: a touch only brings the stereo back.
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && fullscreen) {
            setFullscreen(false);
            swallowTouch = true;
            return true;
        }
        if (swallowTouch) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) swallowTouch = false;
            return true;
        }
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
                if (pressedPreset > 0 || Skin.DISPLAY.equals(pressed)) postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout() * 2L);
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
                removeCallbacks(hold);
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
                removeCallbacks(hold);
                pressed = null;
                pressedPreset = 0;
                knobDragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** A key held down does its second job instead, and the release does nothing more. */
    private final Runnable hold = () -> {
        if (pressed == null || listener == null) return;
        String id = pressed;
        int idx = pressedPreset;
        pressed = null;
        pressedPreset = 0;
        listener.onHeld(id, idx);
        invalidate();
    };

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
