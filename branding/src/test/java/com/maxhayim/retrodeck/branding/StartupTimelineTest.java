package com.maxhayim.retrodeck.branding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StartupTimelineTest {

    private static StartupConfig config(String preset, int ms) {
        StartupConfig c = new StartupConfig();
        c.preset = preset;
        c.durationMs = ms;
        return c;
    }

    @Test
    public void startsBlackAndEndsInTheStereo() {
        for (String p : StartupConfig.PRESETS) {
            if (p.equals(StartupConfig.INSTANT)) continue;
            StartupTimeline t = new StartupTimeline(config(p, 3000));
            StartupTimeline.Frame f = t.at(0);
            assertEquals(p, 0f, f.logo, 0.001);
            assertEquals(p, 0f, f.badge, 0.001);
            assertEquals(p, 0f, f.exit, 0.001);
            f = t.at(3000);
            assertEquals(p, 1f, f.logo, 0.001);
            assertEquals(p, 1f, f.badge, 0.001);
            assertEquals(p, 1f, f.exit, 0.001);
            assertTrue(t.finished(3000));
        }
    }

    @Test
    public void stepsComeInOrder() {
        StartupTimeline t = new StartupTimeline(config(StartupConfig.FADE, 4000));
        float firstLogo = -1, firstBadge = -1, firstPower = -1, firstLcd = -1, firstExit = -1;
        for (int ms = 0; ms <= 4000; ms += 20) {
            StartupTimeline.Frame f = t.at(ms);
            if (firstLogo < 0 && f.logo > 0) firstLogo = ms;
            if (firstBadge < 0 && f.badge > 0) firstBadge = ms;
            if (firstPower < 0 && f.power > 0) firstPower = ms;
            if (firstLcd < 0 && f.lcd > 0) firstLcd = ms;
            if (firstExit < 0 && f.exit > 0) firstExit = ms;
        }
        assertTrue(firstLogo > 0);
        assertTrue(firstBadge > firstLogo);
        assertTrue(firstPower > firstBadge);
        assertTrue(firstLcd > firstPower);
        assertTrue(firstExit > firstLcd);
    }

    @Test
    public void logoOnlyEverBrightens() {
        for (String p : StartupConfig.PRESETS) {
            StartupTimeline t = new StartupTimeline(config(p, 2500));
            float last = 0;
            for (int ms = 0; ms <= 2500; ms += 10) {
                float v = t.at(ms).logo;
                assertTrue(p + " at " + ms, v >= last - 1e-4);
                last = v;
            }
        }
    }

    @Test
    public void disabledStepsAreSkipped() {
        StartupConfig c = config(StartupConfig.FADE, 3000);
        c.powerOn = false;
        c.lcdBoot = false;
        StartupTimeline t = new StartupTimeline(c);
        for (int ms = 0; ms <= 3000; ms += 50) {
            assertEquals(0f, t.at(ms).power, 0);
            assertEquals(0f, t.at(ms).lcd, 0);
        }
        // With fewer steps the logo gets more of the time, so it reaches full brightness later.
        StartupTimeline full = new StartupTimeline(config(StartupConfig.FADE, 3000));
        assertEquals(1f, full.at(1150).logo, 0.001);
        assertTrue(t.at(1150).logo < 1f);
        assertEquals(1f, t.at(1800).logo, 0.001);
    }

    @Test
    public void offMeansNoDelay() {
        StartupConfig c = config(StartupConfig.GLOW, 3000);
        c.enabled = false;
        StartupTimeline t = new StartupTimeline(c);
        assertEquals(0, t.durationMs());
        assertTrue(t.finished(0));
        assertEquals(1f, t.at(0).exit, 0);
    }

    @Test
    public void instantShowsTheFinishedScreenBriefly() {
        StartupTimeline t = new StartupTimeline(config(StartupConfig.INSTANT, 6000));
        assertEquals(StartupConfig.INSTANT_MS, t.durationMs());
        StartupTimeline.Frame f = t.at(0);
        assertEquals(1f, f.logo, 0);
        assertEquals(1f, f.badge, 0);
        assertEquals(0f, f.exit, 0);
    }

    @Test
    public void durationIsClampedToKeepTheAppUsable() {
        assertEquals(StartupConfig.MAX_MS, new StartupTimeline(config(StartupConfig.GLOW, 60000)).durationMs());
        assertEquals(StartupConfig.MIN_MS, new StartupTimeline(config(StartupConfig.GLOW, 10)).durationMs());
        assertEquals(StartupConfig.GLOW, new StartupTimeline(config("bogus", 2000)).config().preset);
    }

    @Test
    public void glowFlickersOnlyWhileWarmingUp() {
        StartupTimeline t = new StartupTimeline(config(StartupConfig.GLOW, 3000));
        assertEquals(1f, t.at(0).flicker, 0);
        assertEquals(1f, t.at(3000).flicker, 0);
        boolean flickered = false;
        for (int ms = 300; ms < 1200; ms += 7) if (t.at(ms).flicker < 0.99f) flickered = true;
        assertTrue(flickered);
    }
}
