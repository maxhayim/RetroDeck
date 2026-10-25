package com.maxhayim.retrodeck.branding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.maxhayim.retrodeck.core.Box;

import org.junit.Test;

public class PlacementMathTest {

    private static final Box ZONE = new Box(100, 50, 500, 150); // 400 x 100

    @Test
    public void keepsAspectAndFitsTheZone() {
        // A 2:1 logo in a 4:1 zone is limited by height.
        Box b = PlacementMath.layout(ZONE, 200, 100, new Placement(true, 1f, 0.5f, 0.5f), true);
        assertEquals(200, b.width(), 0.01);
        assertEquals(100, b.height(), 0.01);
        assertEquals(300, b.centerX(), 0.01);
        assertEquals(b.width() / b.height(), 2f, 0.001);
    }

    @Test
    public void scaleShrinksAroundTheCentre() {
        Box b = PlacementMath.layout(ZONE, 200, 100, new Placement(true, 0.5f, 0.5f, 0.5f), true);
        assertEquals(100, b.width(), 0.01);
        assertEquals(50, b.height(), 0.01);
        assertEquals(300, b.centerX(), 0.01);
        assertEquals(100, b.centerY(), 0.01);
    }

    @Test
    public void neverLeavesTheZone() {
        for (float x = 0; x <= 1; x += 0.25f) {
            for (float y = 0; y <= 1; y += 0.25f) {
                for (float s : new float[]{0.1f, 0.6f, 1f, 3f}) {
                    Box b = PlacementMath.layout(ZONE, 30, 300, new Placement(true, s, x, y), true);
                    assertTrue(b + " outside", ZONE.contains(b));
                }
            }
        }
    }

    @Test
    public void scaleIsClampedSoLogosCantOutgrowTheirZone() {
        Box b = PlacementMath.layout(ZONE, 400, 100, new Placement(true, 5f, 0.5f, 0.5f), true);
        assertEquals(400, b.width(), 0.01);
    }

    @Test
    public void positionMovesToTheEdgesButNoFurther() {
        Box left = PlacementMath.layout(ZONE, 100, 100, new Placement(true, 0.5f, 0f, 0.5f), true);
        assertEquals(100, left.left, 0.01);
        Box right = PlacementMath.layout(ZONE, 100, 100, new Placement(true, 0.5f, 1f, 0.5f), true);
        assertEquals(500, right.right, 0.01);
    }

    @Test
    public void stretchFillsTheZoneOnlyWhenAspectIsOff() {
        Box b = PlacementMath.layout(ZONE, 100, 100, new Placement(true, 1f, 0.5f, 0.5f), false);
        assertEquals(400, b.width(), 0.01);
        assertEquals(100, b.height(), 0.01);
    }

    @Test
    public void dragPointBecomesClampedCentre() {
        float[] c = PlacementMath.centreFromPoint(ZONE, 300, 100);
        assertEquals(0.5f, c[0], 0.001);
        assertEquals(0.5f, c[1], 0.001);
        c = PlacementMath.centreFromPoint(ZONE, -50, 999);
        assertEquals(0f, c[0], 0.001);
        assertEquals(1f, c[1], 0.001);
    }

    @Test
    public void rotatedBoundingBox() {
        float[] s = PlacementMath.rotatedSize(200, 100, 90);
        assertEquals(100, s[0], 0.01);
        assertEquals(200, s[1], 0.01);
        s = PlacementMath.rotatedSize(100, 100, 45);
        assertEquals(141.42, s[0], 0.05);
    }
}
