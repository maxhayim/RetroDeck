package com.maxhayim.retrodeck.core;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;

import java.util.Random;

/**
 * Draws a skin's faceplate as a physical object: brushed or spun metal, grained plastic, glass, soft
 * shadows, bevels and recesses, lit from above. Everything here is static, so StereoView draws it
 * once into a cached bitmap at the screen's own resolution; it stays sharp on HD panels and the
 * blurs and textures cost nothing per frame. The parts that light up are drawn by StereoView.
 */
final class Faceplate {

    private static final int AA = Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG;

    private final Canvas c;
    private final Skin skin;
    private final int w, h;
    private final float u;
    private final Paint p = new Paint(AA);

    private Faceplate(Canvas c, Skin skin, int w, int h) {
        this.c = c;
        this.skin = skin;
        this.w = w;
        this.h = h;
        this.u = Math.min(w, h);
    }

    static void draw(Canvas c, Skin skin, int w, int h) {
        Faceplate f = new Faceplate(c, skin, w, h);
        switch (skin.lcdStyle) {
            case Skin.LCD_DOT: f.digital(); break;
            case Skin.LCD_MODERN: f.glass(); break;
            default: f.classic(); break;
        }
    }

    // ---- skins ----

    /** Brushed aluminium with black keys, a spun-metal knob and a recessed VFD window. */
    private void classic() {
        housing();
        RectF face = new RectF(w * 0.006f, h * 0.012f, w * 0.994f, h * 0.988f);
        float fr = u * 0.018f;
        drop(face, fr, u * 0.02f, u * 0.006f, 0xD0);
        p.setShader(new LinearGradient(0, face.top, 0, face.bottom,
                new int[]{0xFFC3C8CE, 0xFFA7ADB4, 0xFF8C9299, 0xFF70757C},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(face, fr, fr, p);
        p.setShader(null);
        save(face, fr);
        overlay(brushed(1024, 256, 85), 0.85f);
        anisotropic(face, 0x2C);
        light(face, 0x30, 0x48);
        c.restore();
        bevel(face, fr, u * 0.004f, 0x90FFFFFF, 0x90000000);

        // Engraved lines framing the display, as on stamped faceplates.
        groove(w * 0.215f, h * 0.645f, w * 0.785f);
        screws(0.018f, 0.045f);

        RectF lcd = px(skin.lcd);
        window(lcd, u * 0.016f, skin.corner * u, 0xFF060807, 0xFF0F1311);

        for (Skin.Control ctl : skin.controls) key(px(ctl.box), skin.corner * u, false);
        for (Box b : skin.presetBoxes()) key(px(b), skin.corner * u, false);

        knob(px(skin.knob), false);
        print("VOLUME", px(skin.knob).centerX(), px(skin.knob).bottom + u * 0.03f, u * 0.026f, 0xFF25272B, true);

        RectF s = px(skin.slot);
        cassette(s);
        print("AUTO REVERSE", s.left, s.top - u * 0.012f, u * 0.018f, 0xFF2B2D31, false);
    }

    /** Grained black plastic, soft rubber keys, a ribbed knob and a glossy dot-matrix window. */
    private void digital() {
        housing();
        RectF face = new RectF(w * 0.006f, h * 0.012f, w * 0.994f, h * 0.988f);
        float fr = u * 0.04f;
        drop(face, fr, u * 0.02f, u * 0.006f, 0xE0);
        p.setShader(new LinearGradient(0, face.top, 0, face.bottom,
                new int[]{0xFF34363B, 0xFF222428, 0xFF16171A}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(face, fr, fr, p);
        p.setShader(null);
        save(face, fr);
        overlay(grain(256, 96, 22), 0.7f);
        light(face, 0x22, 0x40);
        // A soft sheen across the top, from the rounded front edge.
        p.setShader(new LinearGradient(0, face.top, 0, face.top + h * 0.12f, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(face.left, face.top, face.right, face.top + h * 0.12f, p);
        p.setShader(null);
        c.restore();
        bevel(face, fr, u * 0.004f, 0x70FFFFFF, 0xA0000000);

        RectF lcd = px(skin.lcd);
        window(lcd, u * 0.014f, skin.corner * u * 0.6f, 0xFF050807, 0xFF0B110F);

        float kr = skin.corner * u;
        for (Skin.Control ctl : skin.controls) key(px(ctl.box), kr, true);
        for (Box b : skin.presetBoxes()) key(px(b), kr, true);

        knob(px(skin.knob), true);
        print("VOLUME", px(skin.knob).centerX(), px(skin.knob).bottom + u * 0.03f, u * 0.026f, 0xFF8E949C, true);

        slot(px(skin.slot), false);
    }

    /** One sheet of black glass with a bonded display and touch keys, like a current head unit. */
    private void glass() {
        c.drawColor(0xFF000000);
        RectF face = new RectF(0, 0, w, h);
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF15181E, 0xFF0A0C10, 0xFF050608},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(face, p);
        p.setShader(null);
        overlay(grain(256, 2020, 6), 0.5f);

        // The display, bonded under the glass: a black mask, then the panel itself.
        RectF lcd = px(skin.lcd);
        float r = skin.corner * u;
        RectF mask = new RectF(lcd);
        mask.inset(-u * 0.008f, -u * 0.008f);
        p.setColor(0xFF020203);
        c.drawRoundRect(mask, r * 1.3f, r * 1.3f, p);
        p.setShader(new RadialGradient(lcd.centerX(), lcd.top, lcd.width() * 0.8f, 0xFF0E131C, 0xFF05070B, Shader.TileMode.CLAMP));
        c.drawRoundRect(lcd, r, r, p);
        p.setShader(null);
        inner(lcd, r, u * 0.01f, u * 0.003f, 0x90);

        for (Skin.Control ctl : skin.controls) touchKey(px(ctl.box), r);

        // The glass edge catches the light; a broad reflection runs across the whole sheet.
        RectF edge = new RectF(u * 0.004f, u * 0.004f, w - u * 0.004f, h - u * 0.004f);
        bevel(edge, u * 0.02f, u * 0.005f, 0x60FFFFFF, 0x30FFFFFF);
        Path sheen = new Path();
        sheen.moveTo(0, 0);
        sheen.lineTo(w * 0.62f, 0);
        sheen.lineTo(w * 0.34f, h);
        sheen.lineTo(0, h);
        sheen.close();
        p.setShader(new LinearGradient(0, 0, w * 0.5f, h * 0.4f, 0x12FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(sheen, p);
        p.setShader(null);
    }

    // ---- parts ----

    /** The dashboard around the unit: dark textured plastic. */
    private void housing() {
        c.drawColor(0xFF0C0D0F);
        overlay(grain(256, 7, 18), 0.6f);
    }

    /** A recessed window: a bevelled cut in the faceplate, a dark surround, then the glass. */
    private void window(RectF lcd, float border, float r, int glassEdge, int glassCentre) {
        RectF cut = new RectF(lcd);
        cut.inset(-border, -border);
        float cr = r + border * 0.8f;
        p.setShader(new LinearGradient(0, cut.top, 0, cut.bottom, 0xFF151618, 0xFF0B0C0D, Shader.TileMode.CLAMP));
        c.drawRoundRect(cut, cr, cr, p);
        p.setShader(null);
        inner(cut, cr, border * 0.9f, border * 0.35f, 0xC0);
        bevel(cut, cr, u * 0.003f, 0xA0000000, 0x70FFFFFF);

        p.setShader(new RadialGradient(lcd.centerX(), lcd.centerY(), Math.max(lcd.width(), lcd.height()) * 0.7f,
                glassCentre, glassEdge, Shader.TileMode.CLAMP));
        c.drawRoundRect(lcd, r, r, p);
        p.setShader(null);
        inner(lcd, r, u * 0.022f, u * 0.008f, 0xD0);
    }

    /** A key standing out of its hole. Rubber keys are softer and less shiny than hard plastic ones. */
    private void key(RectF b, float r, boolean rubber) {
        RectF hole = new RectF(b);
        hole.inset(-u * 0.006f, -u * 0.006f);
        float hr = r + u * 0.006f;
        p.setColor(0xFF070708);
        c.drawRoundRect(hole, hr, hr, p);
        bevel(hole, hr, u * 0.0025f, 0x90000000, rubber ? 0x30FFFFFF : 0x80FFFFFF);
        save(hole, hr);
        drop(b, r, u * 0.012f, u * 0.007f, 0xF0);
        c.restore();

        // Side walls, then the face, which sits a little high so more wall shows below it.
        p.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                rubber ? 0xFF3A3C41 : 0xFF484B51, rubber ? 0xFF111214 : 0xFF0E0F11, Shader.TileMode.CLAMP));
        c.drawRoundRect(b, r, r, p);
        p.setShader(null);
        float ch = Math.min(b.height() * 0.1f, u * 0.009f);
        RectF face = new RectF(b.left + ch, b.top + ch * 0.6f, b.right - ch, b.bottom - ch * 1.5f);
        float fr = Math.max(1, r - ch * 0.6f);
        p.setShader(new LinearGradient(0, face.top, 0, face.bottom,
                rubber ? 0xFF2D2F33 : 0xFF2E3034, rubber ? 0xFF1E1F22 : 0xFF1A1B1E, Shader.TileMode.CLAMP));
        c.drawRoundRect(face, fr, fr, p);
        p.setShader(null);
        save(face, fr);
        overlay(grain(128, 11, rubber ? 26 : 12), rubber ? 0.8f : 0.5f);
        p.setShader(new LinearGradient(0, face.top, 0, face.top + face.height() * (rubber ? 0.7f : 0.45f),
                rubber ? 0x14FFFFFF : 0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(face, p);
        p.setShader(null);
        c.restore();
        bevel(face, fr, Math.max(1, u * 0.002f), rubber ? 0x40FFFFFF : 0x60FFFFFF, 0x00000000);
    }

    /** A touch key on the glass: a faint raised pad, only its edge catching the light. */
    private void touchKey(RectF b, float r) {
        p.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xFF181B21, 0xFF0D0F13, Shader.TileMode.CLAMP));
        c.drawRoundRect(b, r, r, p);
        p.setShader(null);
        bevel(b, r, Math.max(1, u * 0.002f), 0x38FFFFFF, 0x50000000);
        p.setShader(new LinearGradient(0, b.top, 0, b.centerY(), 0x10FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(b, r, r, p);
        p.setShader(null);
    }

    /**
     * The volume knob: a collar in the faceplate, a shadow, a knurled (or ribbed rubber) grip and a
     * spun-metal (or gloss black) cap. Its lit pointer and scale are drawn by StereoView.
     */
    private void knob(RectF k, boolean rubber) {
        float cx = k.centerX(), cy = k.centerY(), rad = Math.min(k.width(), k.height()) / 2 * 0.80f;
        // Collar.
        p.setColor(0xFF09090A);
        c.drawCircle(cx, cy, rad * 1.08f, p);
        ring(cx, cy, rad * 1.08f, u * 0.003f, 0xA0000000, 0x70FFFFFF);
        // Shadow.
        Paint s = new Paint(AA);
        s.setColor(0xF0000000);
        s.setMaskFilter(new BlurMaskFilter(rad * 0.18f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx + rad * 0.05f, cy + rad * 0.13f, rad * 1.02f, s);

        // Grip: grooves whose brightness follows the light from the top left.
        p.setColor(rubber ? 0xFF16171A : 0xFF3A3E44);
        c.drawCircle(cx, cy, rad, p);
        int n = rubber ? 40 : 150;
        Paint g = new Paint(AA);
        g.setStrokeWidth((float) (Math.PI * 2 * rad / n * (rubber ? 0.42f : 0.5f)));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            float lit = (float) (0.5 + 0.5 * Math.cos(a - Math.toRadians(-125)));
            int base = rubber ? 0x2A : 0x60;
            int range = rubber ? 0x40 : 0xA0;
            int v = Math.min(255, base + (int) (lit * range));
            g.setColor(0xFF000000 | v << 16 | v << 8 | (Math.min(255, v + 6)));
            float in = rad * (rubber ? 0.80f : 0.86f);
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * rad * 0.995f, cy + (float) Math.sin(a) * rad * 0.995f, g);
        }
        ring(cx, cy, rad, Math.max(1, u * 0.002f), 0x80FFFFFF, 0xA0000000);

        float capR = rad * (rubber ? 0.78f : 0.84f);
        if (rubber) {
            // Gloss black cap with a silver ring.
            p.setShader(new RadialGradient(cx - capR * 0.3f, cy - capR * 0.4f, capR * 1.5f,
                    0xFF3A3D42, 0xFF08090A, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, capR, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(capR * 0.07f);
            p.setShader(new SweepGradient(cx, cy, new int[]{0xFFE8EBEE, 0xFF6C7178, 0xFFD5D9DD, 0xFF5F646B, 0xFFE8EBEE}, null));
            c.drawCircle(cx, cy, capR * 0.93f, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
        } else {
            // Spun aluminium: a sweep of highlights plus fine concentric tool marks.
            p.setShader(new SweepGradient(cx, cy,
                    new int[]{0xFFD9DDE1, 0xFF8A9097, 0xFFF4F6F8, 0xFF7E848B, 0xFFCDD1D6, 0xFF858B92, 0xFFF4F6F8, 0xFF7E848B, 0xFFD9DDE1},
                    null));
            c.drawCircle(cx, cy, capR, p);
            p.setShader(null);
            Paint m = new Paint(AA);
            m.setStyle(Paint.Style.STROKE);
            m.setStrokeWidth(Math.max(0.6f, u * 0.0012f));
            Random rnd = new Random(1985);
            float step = Math.max(1.2f, u * 0.0028f);
            for (float rr = step; rr < capR; rr += step) {
                m.setColor(rnd.nextBoolean() ? 0x16000000 : 0x14FFFFFF);
                c.drawCircle(cx, cy, rr, m);
            }
        }
        // Light falls off towards the cap's edge; a soft hot spot sits up and to the left.
        p.setShader(new RadialGradient(cx, cy, capR, new int[]{0x00000000, 0x00000000, 0x50000000},
                new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, capR, p);
        p.setShader(new RadialGradient(cx - capR * 0.35f, cy - capR * 0.4f, capR * 0.6f,
                rubber ? 0x40FFFFFF : 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, capR, p);
        p.setShader(null);
        ring(cx, cy, capR, Math.max(1, u * 0.002f), 0x90FFFFFF, 0x90000000);
    }

    /** A cassette or CD slot: chrome (or dark satin) lips around a dark opening with felt inside. */
    private void slot(RectF s, boolean chrome) {
        float r = s.height() / 2;
        float lw = u * (chrome ? 0.006f : 0.004f);
        RectF lip = new RectF(s);
        lip.inset(-lw, -lw);
        drop(lip, r + lw, u * 0.006f, u * 0.003f, 0x90);
        p.setShader(new LinearGradient(0, lip.top, 0, lip.bottom,
                chrome ? new int[]{0xFFEEF0F3, 0xFF8B9198, 0xFFC9CDD2, 0xFF5D6269}
                        : new int[]{0xFF6A6F76, 0xFF2E3034, 0xFF4A4E54, 0xFF1A1B1E},
                new float[]{0f, 0.45f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(lip, r + lw, r + lw, p);
        p.setShader(null);
        p.setColor(0xFF030303);
        c.drawRoundRect(s, r, r, p);
        inner(s, r, s.height() * 0.5f, s.height() * 0.2f, 0xFF);
        p.setColor(0x30A0A4AA);
        p.setStrokeWidth(Math.max(1, u * 0.0015f));
        c.drawLine(s.left + r, s.centerY() + s.height() * 0.15f, s.right - r, s.centerY() + s.height() * 0.15f, p);
    }

    /** A cassette door: a chrome-trimmed opening with a hinged flap, open a crack at the top. */
    private void cassette(RectF s) {
        float r = u * 0.006f;
        float lw = u * 0.005f;
        RectF lip = new RectF(s);
        lip.inset(-lw, -lw);
        drop(lip, r + lw, u * 0.008f, u * 0.004f, 0xA0);
        p.setShader(new LinearGradient(0, lip.top, 0, lip.bottom,
                new int[]{0xFFF2F4F6, 0xFF9399A0, 0xFFD4D8DC, 0xFF60656C}, new float[]{0f, 0.4f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRoundRect(lip, r + lw, r + lw, p);
        p.setShader(null);
        p.setColor(0xFF030303);
        c.drawRoundRect(s, r, r, p);
        // The flap fills the opening below a dark gap; it is brushed like the faceplate, but darker.
        RectF flap = new RectF(s.left + u * 0.003f, s.top + s.height() * 0.22f, s.right - u * 0.003f, s.bottom - u * 0.003f);
        p.setShader(new LinearGradient(0, flap.top, 0, flap.bottom, 0xFF5A5F66, 0xFF2C2F33, Shader.TileMode.CLAMP));
        c.drawRect(flap, p);
        p.setShader(null);
        c.save();
        c.clipRect(flap);
        overlay(brushed(512, 64, 1986), 0.6f);
        c.restore();
        p.setColor(0x70FFFFFF);
        c.drawRect(flap.left, flap.top, flap.right, flap.top + Math.max(1, u * 0.0018f), p);
        inner(s, r, s.height() * 0.3f, s.height() * 0.12f, 0xC0);
        // A finger notch in the middle of the flap.
        float nw = flap.width() * 0.16f, nh = flap.height() * 0.28f;
        RectF notch = new RectF(flap.centerX() - nw / 2, flap.bottom - nh - flap.height() * 0.18f,
                flap.centerX() + nw / 2, flap.bottom - flap.height() * 0.18f);
        p.setColor(0xFF1A1B1E);
        c.drawRoundRect(notch, nh / 2, nh / 2, p);
        inner(notch, nh / 2, nh * 0.4f, nh * 0.2f, 0xC0);
    }

    /** Phillips screws in the faceplate corners. */
    private void screws(float fx, float fy) {
        float[][] at = {{fx, fy}, {1 - fx, fy}, {fx, 1 - fy}, {1 - fx, 1 - fy}};
        float r = u * 0.011f;
        Random rnd = new Random(7);
        for (float[] a : at) {
            float x = a[0] * w, y = a[1] * h;
            p.setColor(0xFF1A1B1D);
            c.drawCircle(x, y, r * 1.12f, p);
            p.setShader(new RadialGradient(x - r * 0.4f, y - r * 0.4f, r * 1.6f, 0xFFE6E9EC, 0xFF5B6067, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
            p.setShader(null);
            ring(x, y, r, Math.max(1, u * 0.0015f), 0x70FFFFFF, 0x90000000);
            // The cross, turned a little differently on each screw.
            double t = Math.toRadians(rnd.nextInt(90));
            Paint x1 = new Paint(AA);
            x1.setStrokeWidth(r * 0.28f);
            x1.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 2; i++) {
                double a2 = t + i * Math.PI / 2;
                float dx = (float) Math.cos(a2) * r * 0.6f, dy = (float) Math.sin(a2) * r * 0.6f;
                x1.setColor(0x70FFFFFF);
                c.drawLine(x - dx, y - dy + r * 0.08f, x + dx, y + dy + r * 0.08f, x1);
                x1.setColor(0xFF2A2C30);
                c.drawLine(x - dx, y - dy, x + dx, y + dy, x1);
            }
        }
    }

    /** A fine engraved line: dark cut with a light edge under it. */
    private void groove(float x1, float y, float x2) {
        Paint g = new Paint(AA);
        g.setStrokeWidth(Math.max(1, u * 0.002f));
        g.setColor(0x80000000);
        c.drawLine(x1, y, x2, y, g);
        g.setColor(0x60FFFFFF);
        c.drawLine(x1, y + Math.max(1, u * 0.002f), x2, y + Math.max(1, u * 0.002f), g);
    }

    /** Silk-screened lettering, with a faint highlight under it on metal so it reads as printed. */
    private void print(String s, float x, float y, float size, int color, boolean centre) {
        Paint t = new Paint(AA);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        t.setTextSize(size);
        t.setTextAlign(centre ? Paint.Align.CENTER : Paint.Align.LEFT);
        StereoView.spacing(t, 0.18f);
        if (skin.lcdStyle == Skin.LCD_SEGMENT) {
            t.setColor(0x50FFFFFF);
            c.drawText(s, x, y + Math.max(1, size * 0.06f), t);
        }
        t.setColor(color);
        c.drawText(s, x, y, t);
    }

    // ---- lighting and materials ----

    /** Brighter towards the top, darker towards the bottom. */
    private void light(RectF r, int top, int bottom) {
        p.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.45f, top << 24 | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(r, p);
        p.setShader(new LinearGradient(0, r.top + r.height() * 0.55f, 0, r.bottom, 0x00000000, bottom << 24, Shader.TileMode.CLAMP));
        c.drawRect(r, p);
        p.setShader(null);
    }

    /** Brushed metal reflects in soft bands across the brushing. */
    private void anisotropic(RectF r, int a) {
        int hi = a << 24 | 0xFFFFFF, lo = (a / 2) << 24 | 0xFFFFFF;
        p.setShader(new LinearGradient(r.left, 0, r.right, 0,
                new int[]{0, 0, hi, 0, 0, lo, 0, 0},
                new float[]{0f, 0.16f, 0.27f, 0.40f, 0.66f, 0.76f, 0.86f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(r, p);
        p.setShader(null);
    }

    /** Blends a mid-grey texture over what is there: lighter texels lighten, darker ones darken. */
    private void overlay(Bitmap tex, float strength) {
        Paint t = new Paint(AA);
        t.setShader(new BitmapShader(tex, Shader.TileMode.MIRROR, Shader.TileMode.REPEAT));
        t.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
        t.setAlpha(Math.round(255 * strength));
        c.drawPaint(t);
    }

    private void drop(RectF r, float rad, float blur, float dy, int alpha) {
        Paint s = new Paint(AA);
        s.setColor(alpha << 24);
        s.setMaskFilter(new BlurMaskFilter(Math.max(0.5f, blur), BlurMaskFilter.Blur.NORMAL));
        RectF o = new RectF(r);
        o.offset(0, dy);
        c.drawRoundRect(o, rad, rad, s);
    }

    /** A shadow inside a shape, cast by its top edge: the shape looks sunk into the surface. */
    private void inner(RectF r, float rad, float blur, float dy, int alpha) {
        save(r, rad);
        Path ring = new Path();
        ring.setFillType(Path.FillType.EVEN_ODD);
        RectF out = new RectF(r);
        out.inset(-blur * 3, -blur * 3);
        ring.addRect(out, Path.Direction.CW);
        RectF hole = new RectF(r);
        hole.offset(0, dy);
        ring.addRoundRect(hole, rad, rad, Path.Direction.CW);
        Paint s = new Paint(AA);
        s.setColor(alpha << 24);
        s.setMaskFilter(new BlurMaskFilter(Math.max(0.5f, blur), BlurMaskFilter.Blur.NORMAL));
        c.drawPath(ring, s);
        c.restore();
    }

    /** An edge lit from above: top colour fading to the bottom colour. Raised edges are light on top. */
    private void bevel(RectF r, float rad, float width, int top, int bottom) {
        Paint b = new Paint(AA);
        b.setStyle(Paint.Style.STROKE);
        b.setStrokeWidth(width);
        b.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bottom, Shader.TileMode.CLAMP));
        RectF in = new RectF(r);
        in.inset(width / 2, width / 2);
        c.drawRoundRect(in, Math.max(0, rad - width / 2), Math.max(0, rad - width / 2), b);
    }

    private void ring(float cx, float cy, float r, float width, int top, int bottom) {
        Paint b = new Paint(AA);
        b.setStyle(Paint.Style.STROKE);
        b.setStrokeWidth(width);
        b.setShader(new LinearGradient(0, cy - r, 0, cy + r, top, bottom, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r - width / 2, b);
    }

    private void save(RectF r, float rad) {
        c.save();
        Path clip = new Path();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
    }

    private RectF px(Box b) {
        return new RectF(b.left * w, b.top * h, b.right * w, b.bottom * h);
    }

    // ---- textures ----

    /** Fine random grain around mid-grey. */
    static Bitmap grain(int size, long seed, int amount) {
        Random r = new Random(seed);
        int[] px = new int[size * size];
        for (int i = 0; i < px.length; i++) px[i] = grey(128 + (int) (r.nextGaussian() * amount));
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
    }

    /**
     * Brushed metal: each row is a long, slowly wandering streak, with rows only loosely related, so
     * the grain runs left to right like a real brushed sheet.
     */
    static Bitmap brushed(int tw, int th, long seed) {
        Random r = new Random(seed);
        int[] px = new int[tw * th];
        float row = 0;
        for (int y = 0; y < th; y++) {
            row = row * 0.55f + (float) r.nextGaussian() * 9f;
            float walk = (float) r.nextGaussian() * 6f;
            for (int x = 0; x < tw; x++) {
                walk = walk * 0.994f + (float) r.nextGaussian() * 1.1f;
                px[y * tw + x] = grey(128 + Math.round(row + walk + (float) r.nextGaussian() * 2.5f));
            }
        }
        return Bitmap.createBitmap(px, tw, th, Bitmap.Config.ARGB_8888);
    }

    private static int grey(int v) {
        v = Math.max(0, Math.min(255, v));
        return 0xFF000000 | v << 16 | v << 8 | v;
    }
}
