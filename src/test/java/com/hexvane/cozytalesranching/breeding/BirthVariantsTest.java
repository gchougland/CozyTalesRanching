package com.hexvane.cozytalesranching.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class BirthVariantsTest {
    static final String FROST = "CozyRanch_Snail_Frost";
    static final String MAGMA = "CozyRanch_Snail_Magma";
    static final String GARDEN = "CozyRanch_Snail_Garden";
    static final String TIDE = "CozyRanch_Snail_Tide";
    static final String VOID = "CozyRanch_Snail_Void";

    private static SpeciesDefinition.BirthVariant rule(String feature, List<String> parents, String role, double chance) {
        SpeciesDefinition.BirthVariant b = new SpeciesDefinition.BirthVariant();
        b.Feature = feature;
        b.Parents = parents;
        b.Role = role;
        b.Chance = chance;
        return b;
    }

    static SpeciesDefinition snail(double voidChance, double habitatChance) {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "snail";
        s.WildRoles = List.of("Snail_Frost", "Snail_Magma");
        s.RanchRole = FROST;
        s.RanchBabyRole = FROST + "_Baby";
        s.RanchRoleVariants = Map.of("Snail_Frost", FROST, "Snail_Magma", MAGMA, GARDEN, GARDEN, TIDE, TIDE, VOID, VOID);
        s.RanchBabyRoleVariants = Map.of(FROST, FROST + "_Baby", MAGMA, MAGMA + "_Baby", GARDEN, GARDEN + "_Baby", TIDE, TIDE + "_Baby",
            VOID, VOID + "_Baby");
        s.HabitatRequires = List.of("shade");
        s.HabitatPrefers = List.of("flowers", "vegetables", "water");
        s.BirthVariants = List.of(
            rule("", List.of(FROST, MAGMA), VOID, voidChance),
            rule("flowers", List.of(), GARDEN, habitatChance),
            rule("vegetables", List.of(), GARDEN, habitatChance),
            rule("water", List.of(), TIDE, habitatChance));
        return s.normalise();
    }

    static Predicate<String> has(String... features) {
        Set<String> set = Set.of(features);
        return set::contains;
    }

    @Test
    void noRuleFiresWithoutItsConditions() {
        SpeciesDefinition s = snail(1, 1);
        assertNull(BreedingRules.birthLook(s, FROST, FROST, has(), new Random(1)));
        assertNull(BreedingRules.birthLook(s, MAGMA, MAGMA, has("shade"), new Random(1)));
    }

    @Test
    void aHabitatFeatureGivesItsLook() {
        SpeciesDefinition s = snail(1, 1);
        assertEquals(GARDEN, BreedingRules.birthLook(s, FROST, FROST, has("flowers"), new Random(1)));
        assertEquals(GARDEN, BreedingRules.birthLook(s, FROST, FROST, has("vegetables"), new Random(1)));
        assertEquals(TIDE, BreedingRules.birthLook(s, MAGMA, MAGMA, has("water"), new Random(1)));
        assertEquals(GARDEN, BreedingRules.birthLook(s, MAGMA, MAGMA, has("water", "flowers"), new Random(1)), "earlier rules win");
    }

    @Test
    void aParentPairInEitherOrderGivesItsLook() {
        SpeciesDefinition s = snail(1, 1);
        assertEquals(VOID, BreedingRules.birthLook(s, FROST, MAGMA, has(), new Random(1)));
        assertEquals(VOID, BreedingRules.birthLook(s, MAGMA, FROST, has(), new Random(1)));
        assertEquals(VOID, BreedingRules.birthLook(s, "Snail_Magma", FROST, has(), new Random(1)), "wild roles count as their look");
        assertNull(BreedingRules.birthLook(s, FROST, FROST, has(), new Random(1)));
        assertNull(BreedingRules.birthLook(s, MAGMA, null, has(), new Random(1)));
    }

    @Test
    void oneParentRuleMatchesEitherParent() {
        assertTrue(BreedingRules.parentsMatch(List.of(TIDE), FROST, TIDE));
        assertTrue(BreedingRules.parentsMatch(List.of(TIDE), TIDE, null));
        assertFalse(BreedingRules.parentsMatch(List.of(TIDE), FROST, MAGMA));
        assertTrue(BreedingRules.parentsMatch(List.of(), null, null));
    }

    @Test
    void theChanceIsRolled() {
        SpeciesDefinition s = snail(0.1, 0.5);
        Random rng = new Random(42);
        int voids = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) if (VOID.equals(BreedingRules.birthLook(s, FROST, MAGMA, has(), rng))) voids++;
        assertEquals(0.1, voids / (double) n, 0.01);
        assertNull(BreedingRules.birthLook(snail(0, 0), FROST, MAGMA, has("water"), rng), "chance 0 never fires");
    }

    @Test
    void theLookIsKeptFromBirthToAdult() {
        SpeciesDefinition s = snail(1, 1);
        String look = BreedingRules.birthLook(s, FROST, MAGMA, has(), new Random(1));
        String baby = BreedingRules.babyRole(s, look);
        assertEquals(VOID + "_Baby", baby);
        assertEquals(VOID, BreedingRules.adultRole(s, baby));
        assertEquals(TIDE + "_Baby", BreedingRules.babyRole(s, TIDE));
        assertEquals(TIDE, BreedingRules.adultRole(s, TIDE + "_Baby"));
    }

    @Test
    void featuresPresentReadsTheLastScan() {
        SpeciesDefinition s = snail(1, 1);
        HouseRecord h = new HouseRecord();
        assertFalse(BreedingRules.featuresPresent(h, s, List.of()).test("water"), "an unscanned house has nothing");
        h.habitatScannedAtMs = 5;
        h.habitatMissing = new ArrayList<>(List.of("flowers"));
        Predicate<String> p = BreedingRules.featuresPresent(h, s, List.of("toy_log"));
        assertTrue(p.test("water"));
        assertFalse(p.test("flowers"), "reported missing");
        assertTrue(p.test("toy_log"), "a liked toy counts");
        assertFalse(p.test("lily_pads"), "a feature the species does not watch is unknown, so not present");
    }

    @Test
    void speciesWithoutRulesBreedAsBefore() {
        SpeciesDefinition cow = new SpeciesDefinition();
        cow.RanchRole = "CozyRanch_Cow";
        cow.RanchBabyRole = "CozyRanch_Cow_Calf";
        cow.normalise();
        assertNull(BreedingRules.birthLook(cow, "CozyRanch_Cow", "CozyRanch_Cow", f -> true, new Random(1)));
    }
}
