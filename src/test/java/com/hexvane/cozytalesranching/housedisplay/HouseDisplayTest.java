package com.hexvane.cozytalesranching.housedisplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HouseDisplayTest {
    private static final double EPS = 1e-6;

    /** Forward vector of an entity heading, as PhysicsMath.headingX/Z define it. */
    private static double[] forward(float heading) {
        return new double[] {-Math.sin(heading), -Math.cos(heading)};
    }

    @Test
    void rotationZeroIsCellCentrePlusModelUnits() {
        DisplayMath.Placement p = DisplayMath.place(16, 64, -32, 0, 10, 20, 30, 0);
        assertEquals(10 + 0.5 + 0.5, p.x(), EPS);
        assertEquals(20 + 2.0, p.y(), EPS);
        assertEquals(30 + 0.5 - 1.0, p.z(), EPS);
    }

    @Test
    void quarterTurnsMatchTheHitboxMapping() {
        // HouseGeometry.rotate / BlockBoundingBoxes.rotate90Y: yaw 1 sends (x, z) to (z, 1 - x) in cell space,
        // i.e. a vector (u, v) about the cell centre goes to (v, -u).
        double u = 1.25, v = 0.5; // a point 1.25 blocks east, 0.5 south of the cell centre
        double[][] expect = {{u, v}, {v, -u}, {-u, -v}, {-v, u}};
        for (int r = 0; r < 4; r++) {
            DisplayMath.Placement p = DisplayMath.place(u * 32, 0, v * 32, 0, 0, 0, 0, r);
            assertEquals(0.5 + expect[r][0], p.x(), EPS, "x at rotation " + r);
            assertEquals(0.5 + expect[r][1], p.z(), EPS, "z at rotation " + r);
        }
        // and rotation indices wrap
        DisplayMath.Placement a = DisplayMath.place(40, 0, 8, 0, 0, 0, 0, 5);
        DisplayMath.Placement b = DisplayMath.place(40, 0, 8, 0, 0, 0, 0, 1);
        assertEquals(b.x(), a.x(), EPS);
        assertEquals(b.z(), a.z(), EPS);
    }

    @Test
    void yawZeroLooksOutOfTheDoorAtEveryRotation() {
        // HouseGeometry.front: the door side is +Z, +X, -Z, -X for rotations 0..3
        double[][] door = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int r = 0; r < 4; r++) {
            double[] f = forward(DisplayMath.place(0, 0, 0, 0, 0, 0, 0, r).yawRadians());
            assertEquals(door[r][0], f[0], EPS, "forward x at rotation " + r);
            assertEquals(door[r][1], f[1], EPS, "forward z at rotation " + r);
        }
    }

    @Test
    void yawNinetyLooksAtTheHousePlusXSide() {
        double[] f = forward(DisplayMath.place(0, 0, 0, 90, 0, 0, 0, 0).yawRadians());
        assertEquals(1, f[0], EPS);
        assertEquals(0, f[1], EPS);
        // turned once, the house's +X side faces -Z
        double[] g = forward(DisplayMath.place(0, 0, 0, 90, 0, 0, 0, 1).yawRadians());
        assertEquals(0, g[0], EPS);
        assertEquals(-1, g[1], EPS);
    }

    @Test
    void scaleKeepsTheModelsOwnSize() {
        assertEquals(0.5f, DisplayMath.scale(0.5, 0.95f, 1.05f), 1e-6f);
        assertEquals(0.3f, DisplayMath.scale(0.5, 0.58f, 0.62f), 1e-6f); // a baby stays smaller
        assertEquals(0.5f, DisplayMath.scale(0.5, 0f, 0f), 1e-6f);
        assertTrue(DisplayMath.scale(0, 1f, 1f) > 0);
    }

    @Test
    void slotsKeepWhoIsSeatedAndFillLowestFreePoints() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3), d = new UUID(0, 4);
        // nothing shown, three inside, two points: the first two by order, on points 0 and 1
        assertEquals(Map.of(0, a, 1, b), DisplaySlots.plan(Map.of(), List.of(a, b, c), 2));
        // b sits on point 1; a leaves; c and d arrive: b stays, c takes point 0
        Map<Integer, UUID> current = new HashMap<>(Map.of(1, b));
        assertEquals(Map.of(0, c, 1, b), DisplaySlots.plan(current, List.of(b, c, d), 2));
        // fewer points now (reload): b on point 3 is dropped and re-seated
        assertEquals(Map.of(0, b), DisplaySlots.plan(Map.of(3, b), List.of(b), 1));
        // nobody inside: nothing shown
        assertTrue(DisplaySlots.plan(Map.of(0, a), List.of(), 3).isEmpty());
        // an animal shown twice (should never happen) keeps one point only
        Map<Integer, UUID> twice = DisplaySlots.plan(Map.of(0, a, 1, a), List.of(a), 2);
        assertEquals(1, twice.size());
    }

    @Test
    void dataIsNormalised() {
        String json = "{\"PassSeconds\":0,\"Animation\":\"\",\"Houses\":{\"X\":{\"Points\":["
            + "{\"Position\":[1,2,3],\"Yaw\":90,\"Scale\":-1},{\"Position\":[4],\"Animation\":\" \"},null,"
            + "{},{},{},{},{},{},{},{}]}}}";
        HouseDisplayData d = HouseDisplayData.read(new StringReader(json));
        assertEquals(1.0, d.PassSeconds, EPS);
        assertEquals("Idle", d.Animation);
        List<HouseDisplayData.Point> pts = d.points("X");
        assertEquals(HouseDisplayData.MAX_POINTS, pts.size());
        assertEquals(0.5, pts.get(0).Scale, EPS);
        assertEquals(4, pts.get(1).x(), EPS);
        assertEquals(0, pts.get(1).z(), EPS);
        assertEquals(null, pts.get(1).Animation);
        assertTrue(d.points("Y").isEmpty());
        d.Enabled = false;
        assertTrue(d.points("X").isEmpty());
    }

    @Test
    void bundledFileReadsAndEveryPointIsSane() throws Exception {
        try (InputStream in = HouseDisplayData.class.getResourceAsStream(HouseDisplayData.RESOURCE)) {
            HouseDisplayData d = HouseDisplayData.read(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertTrue(d.Enabled);
            assertFalse(d.Houses.isEmpty());
            for (var e : d.Houses.entrySet()) {
                assertTrue(e.getKey().startsWith("CozyRanch_House_"), e.getKey());
                assertFalse(e.getValue().Points.isEmpty(), e.getKey());
                for (HouseDisplayData.Point p : e.getValue().Points) {
                    assertEquals(3, p.Position.length, e.getKey());
                    // within the 4 x 4 x 4 house cap around the origin cell (model units)
                    assertTrue(Math.abs(p.x()) <= 64 && Math.abs(p.z()) <= 64 && p.y() >= -1 && p.y() <= 128, e.getKey());
                    assertTrue(p.Scale > 0.1 && p.Scale <= 1.0, e.getKey());
                }
            }
        }
    }
}
