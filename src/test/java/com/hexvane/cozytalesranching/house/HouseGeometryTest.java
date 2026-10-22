package com.hexvane.cozytalesranching.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HouseGeometryTest {
    private static final double EPS = 1e-9;

    /** The union of each house's hitbox boxes at rotation 0 (from Server/Item/Block/Hitboxes/CozyRanch). */
    private static final Map<String, HouseGeometry.Bounds> FOOTPRINTS = Map.of(
        "ChickenCoop", new HouseGeometry.Bounds(0, 0, 0.03, 1.95, 2.75, 2),
        "Pigsty", new HouseGeometry.Bounds(-1, 0, -1, 2, 1.85, 2),
        "FrogPond", new HouseGeometry.Bounds(-1, 0, -1, 2, 1.78, 2),
        "CowBarn", new HouseGeometry.Bounds(-1.8125, 0, -2, 1.8125, 4, 2),
        "Sheepfold", new HouseGeometry.Bounds(-1, 0, -0.9375, 2, 2.78125, 2));

    /** The engine's BlockBoundingBoxes.rotate90Y on a point: (x, z) to (z, 1 - x). */
    private static double[] engineTurn(double x, double z, int turns) {
        for (int i = 0; i < turns; i++) {
            double nx = z;
            double nz = 1 - x;
            x = nx;
            z = nz;
        }
        return new double[] {x, z};
    }

    @Test
    void pigstyDoorForEachRotation() {
        HouseGeometry.Bounds b = FOOTPRINTS.get("Pigsty");
        double[][] expected = {{0.5, 2.6}, {2.6, 0.5}, {0.5, -1.6}, {-1.6, 0.5}};
        for (int yaw = 0; yaw < 4; yaw++) {
            HouseGeometry.Door d = HouseGeometry.door(HouseGeometry.rotate(b, yaw), yaw, 10, 64, -20);
            assertEquals(10 + expected[yaw][0], d.x(), EPS, "x at yaw " + yaw);
            assertEquals(64, d.y(), EPS);
            assertEquals(-20 + expected[yaw][1], d.z(), EPS, "z at yaw " + yaw);
        }
    }

    @Test
    void doorTurnsWithTheHitbox() {
        for (Map.Entry<String, HouseGeometry.Bounds> e : FOOTPRINTS.entrySet()) {
            HouseGeometry.Door d0 = HouseGeometry.door(e.getValue(), 0, 0, 0, 0);
            for (int yaw = 1; yaw < 4; yaw++) {
                HouseGeometry.Door d = HouseGeometry.door(HouseGeometry.rotate(e.getValue(), yaw), yaw, 0, 0, 0);
                double[] turned = engineTurn(d0.x(), d0.z(), yaw);
                assertEquals(turned[0], d.x(), EPS, e.getKey() + " x at yaw " + yaw);
                assertEquals(turned[1], d.z(), EPS, e.getKey() + " z at yaw " + yaw);
            }
        }
    }

    @Test
    void doorIsOutsideTheFootprintOnTheFrontSide() {
        for (Map.Entry<String, HouseGeometry.Bounds> e : FOOTPRINTS.entrySet()) {
            for (int yaw = 0; yaw < 4; yaw++) {
                HouseGeometry.Bounds r = HouseGeometry.rotate(e.getValue(), yaw);
                HouseGeometry.Door d = HouseGeometry.door(r, yaw, 0, 0, 0);
                int cx = (int) Math.floor(d.x());
                int cz = (int) Math.floor(d.z());
                assertFalse(HouseGeometry.covers(r, 0, 0, 0, cx, 0, cz), e.getKey() + " door cell inside at yaw " + yaw);
                int[] f = HouseGeometry.front(yaw);
                double midX = (r.minX() + r.maxX()) / 2;
                double midZ = (r.minZ() + r.maxZ()) / 2;
                assertTrue((d.x() - midX) * f[0] + (d.z() - midZ) * f[1] > 0, e.getKey() + " door behind at yaw " + yaw);
            }
        }
    }

    @Test
    void rotationKeepsTheSizeAndFourTurnsAreIdentity() {
        for (HouseGeometry.Bounds b : FOOTPRINTS.values()) {
            for (int yaw = 0; yaw < 4; yaw++) {
                HouseGeometry.Bounds r = HouseGeometry.rotate(b, yaw);
                double w = b.maxX() - b.minX();
                double l = b.maxZ() - b.minZ();
                if (yaw % 2 == 1) {
                    assertEquals(l, r.maxX() - r.minX(), EPS);
                    assertEquals(w, r.maxZ() - r.minZ(), EPS);
                } else {
                    assertEquals(w, r.maxX() - r.minX(), EPS);
                    assertEquals(l, r.maxZ() - r.minZ(), EPS);
                }
            }
            HouseGeometry.Bounds back = b;
            for (int i = 0; i < 4; i++) back = HouseGeometry.rotate(back, 1);
            assertEquals(b.minX(), back.minX(), EPS);
            assertEquals(b.minZ(), back.minZ(), EPS);
            assertEquals(b.maxX(), back.maxX(), EPS);
            assertEquals(b.maxZ(), back.maxZ(), EPS);
        }
    }

    @Test
    void coversTheFilledCells() {
        HouseGeometry.Bounds pig = FOOTPRINTS.get("Pigsty");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                assertTrue(HouseGeometry.covers(pig, 5, 70, 5, 5 + x, 70, 5 + z));
                assertTrue(HouseGeometry.covers(pig, 5, 70, 5, 5 + x, 71, 5 + z));
            }
        }
        assertFalse(HouseGeometry.covers(pig, 5, 70, 5, 7, 70, 5));
        assertFalse(HouseGeometry.covers(pig, 5, 70, 5, 5, 72, 5));
        assertFalse(HouseGeometry.covers(pig, 5, 70, 5, 5, 69, 5));

        HouseGeometry.Bounds coop = FOOTPRINTS.get("ChickenCoop");
        assertTrue(HouseGeometry.covers(coop, 0, 0, 0, 1, 2, 1));
        assertFalse(HouseGeometry.covers(coop, 0, 0, 0, -1, 0, 0));
        HouseGeometry.Bounds coop1 = HouseGeometry.rotate(coop, 1);
        assertTrue(HouseGeometry.covers(coop1, 0, 0, 0, 1, 0, -1));
        assertFalse(HouseGeometry.covers(coop1, 0, 0, 0, 1, 0, 1));
    }
}
