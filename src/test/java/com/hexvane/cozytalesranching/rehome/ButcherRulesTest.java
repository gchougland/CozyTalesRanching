package com.hexvane.cozytalesranching.rehome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class ButcherRulesTest {
    private static final double EPS = 1e-9;
    /** Never wins a chance roll. */
    private static final RandomGenerator NO_LUCK = new RandomGenerator() {
        @Override public long nextLong() { return Long.MAX_VALUE; }
        @Override public double nextDouble() { return 0.999999; }
    };
    /** Always wins a chance roll. */
    private static final RandomGenerator ALL_LUCK = new RandomGenerator() {
        @Override public long nextLong() { return 0; }
        @Override public double nextDouble() { return 0.0; }
    };

    @Test
    void anAdultAlwaysGivesMoreThanAWildKill() {
        double unhappy = ButcherRules.rollFactor(0f, 0, 1.0);
        assertEquals(1.5, unhappy, EPS);
        assertTrue(unhappy > 1.0, "a wild kill rolls once");
    }

    @Test
    void happinessHeartsAndBountifulEachRaiseTheYield() {
        double base = ButcherRules.rollFactor(0.5f, 0, 1.0);
        assertTrue(ButcherRules.rollFactor(1f, 0, 1.0) > base);
        assertTrue(ButcherRules.rollFactor(0.5f, 3, 1.0) > base);
        assertTrue(ButcherRules.rollFactor(0.5f, 0, 1.25) > base);
        assertEquals(2.0 * 1.5 * 1.5 * 1.25, ButcherRules.rollFactor(1f, 5, 1.25), EPS);
    }

    @Test
    void outOfRangeInputsAreClamped() {
        assertEquals(ButcherRules.rollFactor(1f, 5, 1.0), ButcherRules.rollFactor(3f, 99, 1.0), EPS);
        assertEquals(ButcherRules.rollFactor(0f, 0, 1.0), ButcherRules.rollFactor(-1f, -4, 0.0), EPS);
    }

    @Test
    void wholeRollsAndTheFractionAsAChance() {
        assertEquals(2, ButcherRules.rolls(2.5, 0, NO_LUCK));
        assertEquals(3, ButcherRules.rolls(2.5, 0, ALL_LUCK));
        assertEquals(1, ButcherRules.rolls(0.2, 0, NO_LUCK), "never nothing");
    }

    @Test
    void luckyAddsAnExtraRollPerRollThatHits() {
        assertEquals(5, ButcherRules.rolls(5.0, 0.1, NO_LUCK));
        assertEquals(10, ButcherRules.rolls(5.0, 0.1, ALL_LUCK));
        SplittableRandom rng = new SplittableRandom(7);
        long total = 0;
        int trials = 20_000;
        for (int i = 0; i < trials; i++) total += ButcherRules.rolls(4.0, 0.1, rng);
        assertEquals(4.4, total / (double) trials, 0.05);
    }

    @Test
    void namedOrFourHeartAnimalsNeedConfirmation() {
        assertTrue(ButcherRules.needsConfirm("Daisy", 0));
        assertTrue(ButcherRules.needsConfirm(null, 4));
        assertTrue(ButcherRules.needsConfirm(null, 5));
        assertFalse(ButcherRules.needsConfirm(null, 3));
        assertFalse(ButcherRules.needsConfirm("  ", 3));
    }

    @Test
    void aSecondUseWithinTenSecondsConfirms() {
        ButcherRules.Confirmations c = new ButcherRules.Confirmations();
        UUID player = UUID.randomUUID();
        UUID cow = UUID.randomUUID();
        assertFalse(c.confirmOrArm(player, cow, 1_000));
        assertTrue(c.confirmOrArm(player, cow, 1_000 + ButcherRules.CONFIRM_WINDOW_MS));
        assertFalse(c.confirmOrArm(player, cow, 20_000), "a confirmation is used up");
    }

    @Test
    void aLateOrDifferentUseOnlyArmsAgain() {
        ButcherRules.Confirmations c = new ButcherRules.Confirmations();
        UUID player = UUID.randomUUID();
        UUID cow = UUID.randomUUID();
        UUID pig = UUID.randomUUID();
        assertFalse(c.confirmOrArm(player, cow, 0));
        assertFalse(c.confirmOrArm(player, cow, ButcherRules.CONFIRM_WINDOW_MS + 1), "too late");
        assertFalse(c.confirmOrArm(player, pig, ButcherRules.CONFIRM_WINDOW_MS + 2), "another animal");
        assertFalse(c.confirmOrArm(UUID.randomUUID(), pig, ButcherRules.CONFIRM_WINDOW_MS + 3), "another player");
        assertTrue(c.confirmOrArm(player, pig, ButcherRules.CONFIRM_WINDOW_MS + 4));
    }
}
