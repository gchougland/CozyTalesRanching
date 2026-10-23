package com.hexvane.cozytalesranching.wildlife;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpotRulesTest {
    /** Flat ground at y=64 (feet at 65) within 64 blocks, with blocks and water set by hand. */
    private static final class Fake implements SpotRules.Grid {
        final Set<String> solid = new HashSet<>();
        final Set<String> leaves = new HashSet<>();
        final Set<String> water = new HashSet<>();
        int loadedRadius = 64;
        int groundY = 64;

        static String k(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            return Math.abs(x) <= loadedRadius && Math.abs(z) <= loadedRadius;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return y <= groundY && !water.contains(k(x, y, z)) || solid.contains(k(x, y, z)) || leaves.contains(k(x, y, z));
        }

        @Override
        public boolean leaves(int x, int y, int z) {
            return leaves.contains(k(x, y, z));
        }

        @Override
        public boolean fluid(int x, int y, int z) {
            return water.contains(k(x, y, z));
        }

        @Override
        public boolean water(int x, int y, int z) {
            return water.contains(k(x, y, z));
        }
    }

    @Test
    void flatGroundGivesFeetAboveIt() {
        Fake g = new Fake();
        assertEquals(65, SpotRules.feetY(g, 15, 0, 65));
        assertEquals(new SpotRules.Spot(15, 65, 0), SpotRules.judge(g, 15, 0, 65, 12, false, 3));
    }

    @Test
    void findsGroundBelowAndAboveTheHouse() {
        Fake g = new Fake();
        g.groundY = 58;
        assertEquals(59, SpotRules.feetY(g, 15, 0, 65));
        g.groundY = 69;
        assertEquals(70, SpotRules.feetY(g, 15, 0, 65));
        g.groundY = 40;
        assertEquals(Integer.MIN_VALUE, SpotRules.feetY(g, 15, 0, 65));
    }

    @Test
    void neverUnderARoof() {
        Fake g = new Fake();
        g.solid.add(Fake.k(15, 70, 0));
        assertEquals(SpotRules.Reject.ROOFED, SpotRules.judge(g, 15, 0, 65, 12, false, 3));
        g.solid.clear();
        g.leaves.add(Fake.k(15, 70, 0));
        assertTrue(SpotRules.judge(g, 15, 0, 65, 12, false, 3) instanceof SpotRules.Spot, "tree canopy is fine");
    }

    @Test
    void notOnLeavesOrInWater() {
        Fake g = new Fake();
        g.groundY = 50;
        g.leaves.add(Fake.k(15, 66, 0));
        assertEquals(Integer.MIN_VALUE, SpotRules.feetY(g, 15, 0, 65));
        Fake w = new Fake();
        w.water.add(Fake.k(15, 64, 0));
        w.water.add(Fake.k(15, 63, 0));
        assertEquals(Integer.MIN_VALUE, SpotRules.feetY(w, 15, 0, 64), "water column with water below");
    }

    @Test
    void wallsLeaveNoRoom() {
        Fake g = new Fake();
        g.solid.add(Fake.k(15, 65, 0));
        g.solid.add(Fake.k(15, 66, 0));
        g.solid.add(Fake.k(15, 67, 0));
        assertEquals(SpotRules.Reject.NO_GROUND, SpotRules.judge(g, 15, 0, 65, 12, false, 3), "not on top of a pillar");
        for (int z = -3; z <= 3; z++) for (int y = 65; y <= 67; y++) g.solid.add(Fake.k(15, y, z));
        assertEquals(SpotRules.Reject.NO_GROUND, SpotRules.judge(g, 15, 0, 65, 12, false, 3), "not on top of a wall");
    }

    @Test
    void gentleSlopesAreFine() {
        Fake g = new Fake();
        g.solid.add(Fake.k(15, 65, 0));
        g.solid.add(Fake.k(16, 65, 0));
        g.solid.add(Fake.k(15, 65, 1));
        assertEquals(new SpotRules.Spot(15, 66, 0), SpotRules.judge(g, 15, 0, 65, 12, false, 3));
    }

    @Test
    void unloadedColumnsAreRejected() {
        Fake g = new Fake();
        g.loadedRadius = 10;
        assertEquals(SpotRules.Reject.UNLOADED, SpotRules.judge(g, 15, 0, 65, 12, false, 3));
    }

    @Test
    void frogsNeedWaterNearby() {
        Fake g = new Fake();
        assertEquals(SpotRules.Reject.NO_WATER, SpotRules.judge(g, 15, 0, 65, 12, true, 3));
        g.water.add(Fake.k(17, 64, 1));
        assertTrue(SpotRules.judge(g, 15, 0, 65, 12, true, 3) instanceof SpotRules.Spot);
    }

    @Test
    void ringStaysBetweenItsRadii() {
        Random rng = new Random(6);
        for (int[] c : SpotRules.ring(100, -40, 12, 24, 500, rng)) {
            double d = Math.hypot(c[0] - 100, c[1] + 40);
            assertTrue(d >= 11.3 && d <= 24.7, "distance " + d);
        }
    }

    @Test
    void candidatesAllPass() {
        Fake g = new Fake();
        for (int x = -30; x <= 30; x++) g.solid.add(Fake.k(x, 72, 5));
        WildlifeConfig config = WildlifeConfig.bundled();
        List<SpotRules.Spot> spots = SpotRules.candidates(g, 0, 65, 0, config, false, new Random(7), new StringBuilder());
        assertFalse(spots.isEmpty());
        for (SpotRules.Spot s : spots) {
            assertEquals(65, s.y());
            assertFalse(s.z() == 5 && Math.abs(s.x()) <= 30, "under the roof row");
        }
    }
}
