package com.hexvane.cozytalesranching.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HerdingRulesTest {
    @Test
    void radiusIsClampedAndDefaulted() {
        assertEquals(32.0, HerdingRules.radius(Double.NaN));
        assertEquals(32.0, HerdingRules.radius(0));
        assertEquals(32.0, HerdingRules.radius(-5));
        assertEquals(4.0, HerdingRules.radius(1));
        assertEquals(64.0, HerdingRules.radius(500));
        assertEquals(20.0, HerdingRules.radius(20));
    }

    @Test
    void reachIsASphere() {
        assertTrue(HerdingRules.inReach(0, 0, 0, 32));
        assertTrue(HerdingRules.inReach(32, 0, 0, 32));
        assertFalse(HerdingRules.inReach(23, 0, 23, 32));
        assertTrue(HerdingRules.inReach(-20, 5, 20, 32));
        assertFalse(HerdingRules.inReach(0, 33, 0, 32));
    }

    @Test
    void cooldownBetweenBlows() {
        assertTrue(HerdingRules.ready(0, 1000, 5000), "never blown");
        assertFalse(HerdingRules.ready(10_000, 12_000, 5000));
        assertTrue(HerdingRules.ready(10_000, 15_000, 5000));
        assertTrue(HerdingRules.ready(10_000, 9_000, 5000), "a clock that went back never locks the horn");
    }
}
