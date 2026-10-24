package com.hexvane.cozytalesranching.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class FindTableTest {
    @Test
    void countsStayInsideEachRange() {
        FindTable t = new FindTable(List.of(new FindEntry("A", 5, 2, 4), new FindEntry("B", 5, 1, 1)), id -> true);
        SplittableRandom r = new SplittableRandom(3);
        boolean saw2 = false, saw4 = false;
        for (int i = 0; i < 2000; i++) {
            FindTable.Find f = t.roll(r);
            assertNotNull(f);
            if (f.itemId().equals("A")) {
                assertTrue(f.count() >= 2 && f.count() <= 4);
                saw2 |= f.count() == 2;
                saw4 |= f.count() == 4;
            } else {
                assertEquals(1, f.count());
            }
        }
        assertTrue(saw2 && saw4, "both ends of the range come up");
    }

    @Test
    void picksFollowTheWeights() {
        FindTable t = new FindTable(List.of(new FindEntry("Common", 19, 1, 1), new FindEntry("Rare", 1, 1, 1)), id -> true);
        SplittableRandom r = new SplittableRandom(5);
        int rare = 0, n = 40000;
        for (int i = 0; i < n; i++) if (t.roll(r).itemId().equals("Rare")) rare++;
        assertEquals(0.05, rare / (double) n, 0.01);
    }

    @Test
    void dropsUnknownBlankAndWeightlessEntries() {
        FindTable t = new FindTable(Arrays.asList(new FindEntry("Gone", 5, 1, 1), new FindEntry(" ", 5, 1, 1), new FindEntry("Zero", 0, 1, 1), null,
            new FindEntry("Kept", 1, 1, 1)), id -> !id.equals("Gone"));
        assertEquals(1, t.size());
        assertEquals("Kept", t.roll(new SplittableRandom(1)).itemId());
    }

    @Test
    void emptyTableRollsNothing() {
        FindTable t = new FindTable(null, id -> true);
        assertTrue(t.isEmpty());
        assertNull(t.roll(new SplittableRandom(1)));
    }

    @Test
    void clampsSillyCounts() {
        FindTable t = new FindTable(List.of(new FindEntry("A", 1, 0, 999)), id -> true);
        SplittableRandom r = new SplittableRandom(9);
        for (int i = 0; i < 500; i++) {
            int c = t.roll(r).count();
            assertTrue(c >= 1 && c <= FindTable.MAX_COUNT);
        }
        FindTable inverted = new FindTable(List.of(new FindEntry("B", 1, 5, 2)), id -> true);
        assertEquals(5, inverted.roll(r).count());
    }
}
