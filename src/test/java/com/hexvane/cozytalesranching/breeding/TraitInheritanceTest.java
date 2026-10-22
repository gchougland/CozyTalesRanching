package com.hexvane.cozytalesranching.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class TraitInheritanceTest {
    private static final int TRIALS = 50_000;

    private static TraitBook book(double inherit, double mutation) {
        TraitBook b = BreedingRulesTest.book();
        b.InheritChance = inherit;
        b.MutationChance = mutation;
        return b.normalise();
    }

    @Test
    void aTamedAnimalGetsOneKnownTraitAQuarterOfTheTime() {
        TraitBook b = book(0.5, 0);
        b.TamedTraitChance = 0.25;
        SplittableRandom rng = new SplittableRandom(6);
        int got = 0;
        for (int i = 0; i < TRIALS; i++) {
            List<String> t = BreedingRules.tamedTraits(b, rng);
            assertTrue(t.size() <= 1);
            if (!t.isEmpty()) {
                assertTrue(b.has(t.get(0)));
                got++;
            }
        }
        assertEquals(0.25, got / (double) TRIALS, 0.01);
        b.TamedTraitChance = 0;
        assertTrue(BreedingRules.tamedTraits(b, rng).isEmpty());
    }

    @Test
    void oneParentTraitPassesAboutHalfTheTime() {
        TraitBook b = book(0.5, 0);
        SplittableRandom rng = new SplittableRandom(7);
        int got = 0;
        for (int i = 0; i < TRIALS; i++) {
            if (BreedingRules.inheritTraits(List.of("fertile"), List.of(), b, rng).contains("fertile")) got++;
        }
        assertEquals(0.5, got / (double) TRIALS, 0.01);
    }

    @Test
    void aTraitBothParentsHaveGetsTwoRolls() {
        TraitBook b = book(0.5, 0);
        SplittableRandom rng = new SplittableRandom(8);
        int got = 0;
        for (int i = 0; i < TRIALS; i++) {
            List<String> t = BreedingRules.inheritTraits(List.of("fertile"), List.of("fertile"), b, rng);
            assertTrue(t.size() <= 1, "never twice");
            if (t.contains("fertile")) got++;
        }
        assertEquals(0.75, got / (double) TRIALS, 0.01);
    }

    @Test
    void neverMoreThanMaxTraits() {
        TraitBook b = book(1.0, 1.0);
        SplittableRandom rng = new SplittableRandom(9);
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < TRIALS; i++) {
            List<String> t = BreedingRules.inheritTraits(List.of("fertile", "twinborn"), List.of("swift_grower", "lucky"), b, rng);
            assertEquals(2, t.size());
            assertEquals(2, t.stream().distinct().count());
            t.forEach(id -> seen.merge(id, 1, Integer::sum));
        }
        assertEquals(5, seen.size(), "a full list still mutates by replacing one, so bountiful appears too");
    }

    @Test
    void mutationAddsARandomTraitAtItsRate() {
        TraitBook b = book(0.5, 0.08);
        SplittableRandom rng = new SplittableRandom(10);
        int mutated = 0;
        Map<String, Integer> which = new HashMap<>();
        for (int i = 0; i < TRIALS; i++) {
            List<String> t = BreedingRules.inheritTraits(List.of(), List.of(), b, rng);
            assertTrue(t.size() <= 1);
            if (!t.isEmpty()) {
                mutated++;
                which.merge(t.getFirst(), 1, Integer::sum);
            }
        }
        assertEquals(0.08, mutated / (double) TRIALS, 0.005);
        assertEquals(5, which.size());
        for (int n : which.values()) assertEquals(mutated / 5.0, n, mutated * 0.06, "uniform over the five traits");
    }

    @Test
    void unknownTraitsAreDropped() {
        TraitBook b = book(1.0, 0);
        List<String> t = BreedingRules.inheritTraits(List.of("glowing", "fertile"), List.of(), b, new SplittableRandom(1));
        assertEquals(List.of("fertile"), t);
    }

    @Test
    void sameSeedSameBaby() {
        TraitBook b = book(0.5, 0.08);
        List<String> a = List.of("fertile", "lucky");
        List<String> c = List.of("twinborn");
        assertEquals(BreedingRules.inheritTraits(a, c, b, new SplittableRandom(99)), BreedingRules.inheritTraits(a, c, b, new SplittableRandom(99)));
    }

    @Test
    void effectsCombine() {
        TraitBook b = BreedingRulesTest.book();
        assertEquals(1.0, b.multiplier(List.of(), TraitBook.GROW_UP_MULTIPLIER));
        assertEquals(0.7, b.multiplier(List.of("swift_grower", "lucky"), TraitBook.GROW_UP_MULTIPLIER), 1e-9);
        assertEquals(0.0, b.chance(List.of("fertile"), TraitBook.TWIN_CHANCE), 1e-9);
        assertEquals(0.51, b.chance(List.of("twinborn", "twinborn"), TraitBook.TWIN_CHANCE), 1e-9);
    }

    @Test
    void bundledTraitsFileHasTheDesignDocTraits() throws Exception {
        try (InputStream in = TraitRegistry.class.getResourceAsStream(TraitRegistry.RESOURCE)) {
            assertNotNull(in);
            TraitBook b = TraitRegistry.read(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(List.of("bountiful", "swift_grower", "fertile", "affectionate", "hardy", "gourmet", "twinborn", "efficient_eater", "lucky"), b.ids());
            assertEquals(2, b.MaxTraits);
            assertEquals(0.6, b.multiplier(List.of("fertile"), TraitBook.BREED_COOLDOWN_MULTIPLIER), 1e-9);
            assertEquals(0.7, b.multiplier(List.of("swift_grower"), TraitBook.GROW_UP_MULTIPLIER), 1e-9);
            assertEquals(0.3, b.chance(List.of("twinborn"), TraitBook.TWIN_CHANCE), 1e-9);
            for (TraitBook.Trait t : b.Traits) {
                assertTrue(t.NameKey.startsWith("cozyranch_breeding.trait."), t.Id);
                assertFalse(t.Effects.isEmpty(), t.Id);
            }
        }
    }
}
