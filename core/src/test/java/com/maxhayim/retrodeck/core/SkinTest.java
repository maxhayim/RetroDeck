package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SkinTest {

    private static final Box SCREEN = new Box(0, 0, 1, 1);

    @Test
    public void everySkinDeclaresEveryFaceplateZone() {
        for (Skin s : Skins.all()) {
            for (String z : Zones.ALL) assertNotNull(s.name + " lacks " + z, s.zone(z));
        }
    }

    /** The promise behind branding zones: a logo inside one can never cover a control or the track text. */
    @Test
    public void zonesNeverOverlapControlsOrTrackText() {
        for (Skin s : Skins.all()) {
            for (String z : s.faceplateZones()) {
                Box zone = s.zone(z);
                for (Box keep : s.keepClear()) {
                    assertFalse(s.name + ": zone " + z + " " + zone + " overlaps " + keep, zone.intersects(keep));
                }
            }
        }
    }

    @Test
    public void everythingIsOnScreen() {
        for (Skin s : Skins.all()) {
            for (String z : Zones.ALL) assertTrue(s.name + " " + z, SCREEN.contains(s.zone(z)));
            for (Box b : s.keepClear()) assertTrue(s.name + " " + b, SCREEN.contains(b));
            assertTrue(SCREEN.contains(s.lcd));
            if (s.eq != null) assertTrue(SCREEN.contains(s.eq));
        }
    }

    @Test
    public void lcdAndVisualizerZonesSitInsideTheLcd() {
        for (Skin s : Skins.all()) {
            assertTrue(s.name, s.lcd.contains(s.zone(Zones.LCD)));
            // A stack with its own equaliser window shows the visualizer there.
            assertTrue(s.name, (s.eq != null ? s.eq : s.lcd).contains(s.zone(Zones.VISUALIZER)));
            assertTrue(s.name, s.lcd.contains(s.lcdText));
        }
    }

    @Test
    public void controlsDontOverlapEachOther() {
        for (Skin s : Skins.all()) {
            java.util.List<Box> all = s.keepClear();
            all.remove(s.lcdText);
            for (int i = 0; i < all.size(); i++) {
                for (int j = i + 1; j < all.size(); j++) {
                    assertFalse(s.name + " " + all.get(i) + " / " + all.get(j), all.get(i).intersects(all.get(j)));
                }
            }
        }
    }

    @Test
    public void everySkinHasTheBasicControls() {
        for (Skin s : Skins.all()) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Skin.Control c : s.controls) ids.add(c.id);
            for (String need : new String[]{Skin.POWER, Skin.SOURCE, Skin.PLAY, Skin.PREV, Skin.NEXT, Skin.MENU, Skin.DISPLAY}) {
                assertTrue(s.name + " lacks " + need, ids.contains(need));
            }
            assertTrue(s.name + " has no volume control", s.knob != null || (ids.contains(Skin.VOL_UP) && ids.contains(Skin.VOL_DOWN)));
        }
    }

    /** A single-DIN radio keeps its real shape, its controls on it, and its screen clear of it. */
    @Test
    public void singleDinRadiosAreSlimWithAScreenAbove() {
        int singles = 0;
        for (Skin s : Skins.all()) {
            if (s.din == 2) {
                assertTrue(s.name, s.screen == null);
                continue;
            }
            singles++;
            float aspect = s.body.width() * 16 / (s.body.height() * 9);
            assertTrue(s.name + " is " + aspect + " to 1", aspect > 3.2f && aspect < 3.8f);
            assertNotNull(s.name, s.screen);
            assertTrue(s.name, s.screen.bottom < s.body.top);
            for (Box b : s.keepClear()) assertTrue(s.name + " " + b, s.body.contains(b));
            for (String z : s.faceplateZones()) assertTrue(s.name + " " + z, s.body.contains(s.zone(z)));
            assertTrue(s.name, s.body.contains(s.lcd));
        }
        assertEquals(5, singles);
    }

    @Test
    public void singleDinComesFirstInTheList() {
        boolean doubles = false;
        for (Skin s : Skins.all()) {
            if (s.din == 2) doubles = true;
            else assertFalse(s.name + " comes after a double-DIN skin", doubles);
        }
        assertEquals(Skins.CLASSIC, Skins.get("no such skin").id);
    }

    @Test
    public void presetsSplitIntoSixButtons() {
        Skin s = Skins.get(Skins.CLASSIC);
        assertEquals(6, s.presetBoxes().size());
        assertEquals(0, Skins.get(Skins.MODERN).presetBoxes().size());
    }

    @Test
    public void unknownSkinFallsBackToClassic() {
        assertEquals(Skins.CLASSIC, Skins.get("nope").id);
    }

    @Test
    public void singleDinFillsAWideScreenAndSitsUnderAScreenOnAnOrdinaryOne() {
        for (Skin s : Skins.all()) {
            if (s.din != 1) {
                assertSame(s, Skins.fit(s, 1920, 720));
                continue;
            }
            Skin wide = Skins.fit(s, 1920, 720);
            assertTrue(wide.wide);
            assertEquals(s.id, wide.id);
            assertNull(wide.screen);
            assertEquals(1f, wide.body.height(), 0.001f);
            assertTrue(wide.art.endsWith("-wide"));
            assertSame(wide, Skins.fit(s, 1280, 480));
            assertSame(s, Skins.fit(wide, 1920, 1080));
            assertSame(s, Skins.fit(s, 1024, 600));
            for (Box b : wide.keepClear()) assertTrue(wide.id + " " + b, b.left >= 0 && b.top >= 0 && b.right <= 1 && b.bottom <= 1);
        }
    }

    /** Every key, display and zone of a stack is on one of its units, and the units don't overlap. */
    @Test
    public void stacksKeepEverythingOnAUnit() {
        for (Skin s : Skins.all()) {
            if (s.units.isEmpty()) continue;
            java.util.List<Box> all = s.keepClear();
            all.add(s.lcd);
            if (s.eq != null) all.add(s.eq);
            if (s.slot != null) all.add(s.slot);
            if (s.changer != null) all.add(s.changer);
            for (String z : s.faceplateZones()) all.add(s.zone(z));
            for (Box b : all) {
                boolean on = false;
                for (Box u : s.units) on |= u.contains(b);
                assertTrue(s.name + " " + b + " is off its units", on);
            }
            for (int i = 0; i + 1 < s.units.size(); i++) assertTrue(s.name, s.units.get(i).bottom < s.units.get(i + 1).top);
        }
    }

    @Test
    public void barColoursAreWhole() {
        for (Skin s : Skins.all()) {
            assertTrue(s.name, s.barSegs >= 4);
            if (s.barInks == null) continue;
            assertTrue(s.name, s.barInks.length > 0 && s.barInks.length <= s.barSegs);
            for (int c : s.barInks) assertEquals(s.name, 0xFF, c >>> 24);
        }
    }
}
