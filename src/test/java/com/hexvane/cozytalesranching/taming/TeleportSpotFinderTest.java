package com.hexvane.cozytalesranching.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TeleportSpotFinderTest {
    enum Cell { AIR, SOLID, WATER, LAVA, UNLOADED }

    /** A flat stone floor at y = 63 inside a loaded square; everything else is air unless set. */
    static final class FakeGrid implements TeleportSpotFinder.BlockGrid {
        final Map<Long, Cell> cells = new HashMap<>();
        final int loadedRadius;

        FakeGrid(int loadedRadius) {
            this.loadedRadius = loadedRadius;
        }

        FakeGrid set(int x, int y, int z, Cell c) {
            cells.put(key(x, y, z), c);
            return this;
        }

        Cell at(int x, int y, int z) {
            if (Math.abs(x) > loadedRadius || Math.abs(z) > loadedRadius) return Cell.UNLOADED;
            Cell c = cells.get(key(x, y, z));
            if (c != null) return c;
            return y <= 63 ? Cell.SOLID : Cell.AIR;
        }

        @Override
        public boolean isSafeFloor(int x, int y, int z) {
            return at(x, y, z) == Cell.SOLID;
        }

        @Override
        public boolean isClear(int x, int y, int z) {
            return at(x, y, z) == Cell.AIR;
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFF) << 42 | ((long) y & 0x1FFFFF) << 21 | ((long) z & 0x1FFFFF);
        }
    }

    @Test
    void prefersStraightBehindTheOwner() {
        FakeGrid grid = new FakeGrid(32);
        Optional<TeleportSpotFinder.Spot> spot = TeleportSpotFinder.find(0.5, 64, 0.5, 0, 1, grid);
        assertTrue(spot.isPresent());
        assertEquals(64, spot.get().y());
        assertTrue(spot.get().z() < 0.5, "spot should be behind (owner faces +Z): " + spot.get());
        assertEquals(0.5, spot.get().x(), 1e-9);
    }

    @Test
    void skipsWaterLavaAndLowCeilings() {
        FakeGrid grid = new FakeGrid(32);
        // behind at distance 2.5 and 3.5 (z = -2 and -3): water, lava and a one-high gap
        grid.set(0, 64, -2, Cell.WATER).set(0, 65, -3, Cell.SOLID);
        grid.set(0, 63, -3, Cell.LAVA);
        Optional<TeleportSpotFinder.Spot> spot = TeleportSpotFinder.find(0.5, 64, 0.5, 0, 1, grid);
        assertTrue(spot.isPresent());
        TeleportSpotFinder.Spot s = spot.get();
        int x = (int) Math.floor(s.x());
        int y = (int) s.y();
        int z = (int) Math.floor(s.z());
        assertFalse(x == 0 && (z == -2 || z == -3) && y == 64, "unsafe cell chosen: " + s);
        assertTrue(TeleportSpotFinder.isStandable(grid, x, y, z));
    }

    @Test
    void stepsUpOntoALedge() {
        FakeGrid grid = new FakeGrid(32);
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 0; z++) grid.set(x, 64, z, Cell.SOLID);
        Optional<TeleportSpotFinder.Spot> spot = TeleportSpotFinder.find(0.5, 64, 0.5, 0, 1, grid);
        assertTrue(spot.isPresent());
        assertEquals(65, spot.get().y());
    }

    @Test
    void nothingLoadedMeansNoSpot() {
        FakeGrid grid = new FakeGrid(-1);
        assertTrue(TeleportSpotFinder.find(0.5, 64, 0.5, 0, 1, grid).isEmpty());
    }

    @Test
    void zeroLookDirectionStillWorks() {
        assertTrue(TeleportSpotFinder.find(0.5, 64, 0.5, 0, 0, new FakeGrid(32)).isPresent());
    }
}
