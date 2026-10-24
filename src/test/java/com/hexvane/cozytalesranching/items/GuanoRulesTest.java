package com.hexvane.cozytalesranching.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuanoRulesTest {
    @Test
    void radiusIsClampedAndDefaulted() {
        assertEquals(1, GuanoRules.radius(-1));
        assertEquals(0, GuanoRules.radius(0));
        assertEquals(1, GuanoRules.radius(1));
        assertEquals(2, GuanoRules.radius(2));
        assertEquals(2, GuanoRules.radius(40));
    }

    @Test
    void patchIsASquareWithTheAimedBlockFirst() {
        List<int[]> p = GuanoRules.offsets(1);
        assertEquals(9, p.size());
        assertArrayEquals(new int[] {0, 0}, p.get(0));
        Set<String> seen = new HashSet<>();
        for (int[] d : p) {
            assertTrue(Math.abs(d[0]) <= 1 && Math.abs(d[1]) <= 1);
            assertTrue(seen.add(d[0] + "," + d[1]), "no cell twice");
        }
        assertEquals(1, GuanoRules.offsets(0).size());
        assertEquals(25, GuanoRules.offsets(9).size(), "capped at 5 x 5");
    }

    @Test
    void soilIsLookedForAtTheAimedHeightThenBelow() {
        assertArrayEquals(new int[] {64, 63}, GuanoRules.heights(64));
    }

    @Test
    void usedUpOnlyWhenSomethingTookIt() {
        assertFalse(GuanoRules.consumes(0));
        assertTrue(GuanoRules.consumes(1));
        assertTrue(GuanoRules.consumes(9));
    }
}
