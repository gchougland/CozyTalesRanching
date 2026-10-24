package com.hexvane.cozytalesranching.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.breeding.BreedingRules.Block;
import com.hexvane.cozytalesranching.breeding.BreedingRules.Outcome;
import com.hexvane.cozytalesranching.breeding.BreedingRules.Plan;
import com.hexvane.cozytalesranching.breeding.BreedingRules.Settings;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BreedingRulesTest {
    static final long MIN = 60_000L;
    static final long NOW = 1_000_000_000_000L;
    static final Settings ON = new Settings(true, 3, 0.7);

    static TraitBook book() {
        TraitBook b = new TraitBook();
        b.InheritChance = 0.5;
        b.MutationChance = 0.08;
        b.MaxTraits = 2;
        b.Traits = List.of(
            trait("fertile", Map.of(TraitBook.BREED_COOLDOWN_MULTIPLIER, 0.6)),
            trait("swift_grower", Map.of(TraitBook.GROW_UP_MULTIPLIER, 0.7)),
            trait("twinborn", Map.of(TraitBook.TWIN_CHANCE, 0.3)),
            trait("bountiful", Map.of("ProductionMultiplier", 1.25)),
            trait("lucky", Map.of("BonusProductChance", 0.1)));
        return b.normalise();
    }

    static TraitBook.Trait trait(String id, Map<String, Double> effects) {
        TraitBook.Trait t = new TraitBook.Trait();
        t.Id = id;
        t.Effects = effects;
        return t;
    }

    static SpeciesDefinition cow() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "cow";
        s.WildRoles = List.of("Cow");
        s.RanchRole = "CozyRanch_Cow";
        s.RanchBabyRole = "CozyRanch_Cow_Calf";
        s.GrowUpMinutes = 90;
        s.BreedCooldownMinutes = 60;
        return s;
    }

    static SpeciesDefinition frog() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "frog";
        s.WildRoles = List.of("Frog_Green", "Frog_Blue", "Frog_Orange");
        s.RanchRole = "CozyRanch_Frog";
        s.RanchRoleVariants = Map.of("Frog_Blue", "CozyRanch_Frog_Blue", "Frog_Orange", "CozyRanch_Frog_Orange");
        return s;
    }

    static HouseRecord house(int capacity) {
        HouseRecord h = new HouseRecord();
        h.houseId = UUID.randomUUID();
        h.ownerId = UUID.randomUUID();
        h.speciesId = "cow";
        h.capacity = capacity;
        return h;
    }

    static AnimalRecord adult(HouseRecord h, String... traits) {
        AnimalRecord a = new AnimalRecord();
        a.animalId = UUID.randomUUID();
        a.speciesId = h.speciesId;
        a.houseId = h.houseId;
        a.state = AnimalRecord.State.STORED;
        a.hearts = 3;
        a.happiness = 0.7f;
        a.traits = new ArrayList<>(List.of(traits));
        return a;
    }

    // ---- pair eligibility

    @Test
    void eachResidentRuleBlocks() {
        HouseRecord h = house(4);
        SpeciesDefinition cow = cow();
        TraitBook book = book();
        assertNull(BreedingRules.whyNot(adult(h), h, cow, book, ON, NOW));

        AnimalRecord a = adult(h);
        a.speciesId = "pig";
        assertEquals(Block.WRONG_SPECIES, BreedingRules.whyNot(a, h, cow, book, ON, NOW));
        a = adult(h);
        a.baby = true;
        assertEquals(Block.BABY, BreedingRules.whyNot(a, h, cow, book, ON, NOW));
        a = adult(h);
        a.state = AnimalRecord.State.ACTIVE;
        assertEquals(Block.NOT_INSIDE, BreedingRules.whyNot(a, h, cow, book, ON, NOW));
        a = adult(h);
        a.houseId = UUID.randomUUID();
        assertEquals(Block.OTHER_HOUSE, BreedingRules.whyNot(a, h, cow, book, ON, NOW));
        a = adult(h);
        a.hearts = 2;
        assertEquals(Block.HEARTS, BreedingRules.whyNot(a, h, cow, book, ON, NOW));
        a = adult(h);
        a.happiness = 0.69f;
        assertEquals(Block.HAPPINESS, BreedingRules.whyNot(a, h, cow, book, ON, NOW));
        a = adult(h);
        a.lastBredAtMs = NOW - 59 * MIN;
        assertEquals(Block.COOLDOWN, BreedingRules.whyNot(a, h, cow, book, ON, NOW));
    }

    // ---- cooldown

    @Test
    void cooldownIsSpeciesMinutesAndFertileShortensIt() {
        HouseRecord h = house(4);
        SpeciesDefinition cow = cow();
        TraitBook book = book();
        AnimalRecord plain = adult(h);
        AnimalRecord fertile = adult(h, "fertile");
        assertEquals(60 * MIN, BreedingRules.cooldownMs(plain, cow, book));
        assertEquals(36 * MIN, BreedingRules.cooldownMs(fertile, cow, book));

        assertTrue(BreedingRules.cooledDown(plain, cow, book, NOW), "never bred");
        plain.lastBredAtMs = NOW - 40 * MIN;
        fertile.lastBredAtMs = NOW - 40 * MIN;
        assertFalse(BreedingRules.cooledDown(plain, cow, book, NOW));
        assertTrue(BreedingRules.cooledDown(fertile, cow, book, NOW));
        plain.lastBredAtMs = NOW - 60 * MIN;
        assertTrue(BreedingRules.cooledDown(plain, cow, book, NOW));
    }

    // ---- the morning decision

    private static Plan plan(HouseRecord h, List<AnimalRecord> residents, Settings s, boolean mayBreed) {
        return BreedingRules.plan(h, residents, cow(), book(), s, mayBreed, NOW, new SplittableRandom(1));
    }

    @Test
    void houseLevelGates() {
        HouseRecord h = house(4);
        List<AnimalRecord> pair = List.of(adult(h), adult(h));
        assertEquals(Outcome.BRED, plan(h, pair, ON, true).outcome());
        assertEquals(Outcome.SERVER_DISABLED, plan(h, pair, new Settings(false, 3, 0.7), true).outcome());
        assertEquals(Outcome.NO_PERMISSION, plan(h, pair, ON, false).outcome());
        assertEquals(Outcome.UNKNOWN_SPECIES, BreedingRules.plan(h, pair, null, book(), ON, true, NOW, new SplittableRandom(1)).outcome());
        h.breedingEnabled = false;
        assertEquals(Outcome.HOUSE_DISABLED, plan(h, pair, ON, true).outcome());
        h.breedingEnabled = true;
        h.ownerId = null;
        assertEquals(Outcome.NO_OWNER, plan(h, pair, ON, true).outcome());
    }

    @Test
    void habitatNeverScannedCountsAsMet() {
        HouseRecord h = house(4);
        List<AnimalRecord> pair = List.of(adult(h), adult(h));
        h.habitatRequirementsMet = false;
        h.habitatScannedAtMs = 0;
        Plan p = plan(h, pair, ON, true);
        assertEquals(Outcome.BRED, p.outcome());
        assertTrue(p.habitatUnscanned());
        h.habitatScannedAtMs = NOW - 1000;
        assertEquals(Outcome.HABITAT, plan(h, pair, ON, true).outcome());
        h.habitatRequirementsMet = true;
        Plan met = plan(h, pair, ON, true);
        assertEquals(Outcome.BRED, met.outcome());
        assertFalse(met.habitatUnscanned());
    }

    @Test
    void capacityNeedsOneFreePlace() {
        HouseRecord h = house(2);
        assertEquals(Outcome.FULL, plan(h, List.of(adult(h), adult(h)), ON, true).outcome());
        h.capacity = 3;
        Plan p = plan(h, List.of(adult(h), adult(h)), ON, true);
        assertEquals(Outcome.BRED, p.outcome());
        assertEquals(1, p.babies());
    }

    @Test
    void needsTwoReadyAndPicksTheLongestWaiting() {
        HouseRecord h = house(6);
        AnimalRecord ready = adult(h);
        AnimalRecord sad = adult(h);
        sad.happiness = 0.2f;
        Plan p = plan(h, List.of(ready, sad), ON, true);
        assertEquals(Outcome.NO_PAIR, p.outcome());
        assertEquals(1, p.eligible());

        AnimalRecord recent = adult(h);
        recent.lastBredAtMs = NOW - 70 * MIN;
        AnimalRecord older = adult(h);
        older.lastBredAtMs = NOW - 500 * MIN;
        AnimalRecord never = adult(h);
        p = plan(h, List.of(recent, older, sad, never), ON, true);
        assertEquals(Outcome.BRED, p.outcome());
        assertEquals(3, p.eligible());
        assertSame(never, p.parentA());
        assertSame(older, p.parentB());
    }

    @Test
    void twinsOnlyWithTwinbornAndRoomForTwo() {
        int trials = 20_000;
        SplittableRandom rng = new SplittableRandom(42);
        HouseRecord h = house(4);
        int none = 0, one = 0, both = 0, cramped = 0;
        for (int i = 0; i < trials; i++) {
            List<AnimalRecord> plain = List.of(adult(h), adult(h));
            List<AnimalRecord> oneTwin = List.of(adult(h, "twinborn"), adult(h));
            List<AnimalRecord> bothTwin = List.of(adult(h, "twinborn"), adult(h, "twinborn"));
            if (BreedingRules.plan(h, plain, cow(), book(), ON, true, NOW, rng).babies() == 2) none++;
            if (BreedingRules.plan(h, oneTwin, cow(), book(), ON, true, NOW, rng).babies() == 2) one++;
            if (BreedingRules.plan(h, bothTwin, cow(), book(), ON, true, NOW, rng).babies() == 2) both++;
            HouseRecord small = house(3);
            List<AnimalRecord> smallTwins = List.of(adult(small, "twinborn"), adult(small, "twinborn"));
            if (BreedingRules.plan(small, smallTwins, cow(), book(), ON, true, NOW, rng).babies() == 2) cramped++;
        }
        assertEquals(0, none);
        assertEquals(0.30, one / (double) trials, 0.015);
        assertEquals(0.51, both / (double) trials, 0.015);
        assertEquals(0, cramped, "three places, two parents: room for one baby only");
    }

    // ---- growing up

    @Test
    void growUpTimingWithCareAndSwiftGrower() {
        SpeciesDefinition cow = cow();
        TraitBook book = book();
        AnimalRecord baby = new AnimalRecord();
        baby.speciesId = "cow";
        baby.baby = true;
        baby.happiness = 0.6f;
        baby.bornAtMs = NOW - 89 * MIN;
        assertEquals(90 * MIN, BreedingRules.growUpMs(baby, cow, book));
        assertFalse(BreedingRules.isGrown(baby, cow, book, NOW));
        baby.bornAtMs = NOW - 90 * MIN;
        assertTrue(BreedingRules.isGrown(baby, cow, book, NOW));

        baby.happiness = 0.8f;
        assertEquals(Math.round(90 * 0.85 * MIN), BreedingRules.growUpMs(baby, cow, book));
        baby.traits = new ArrayList<>(List.of("swift_grower"));
        assertEquals(Math.round(90 * 0.85 * 0.7 * MIN), BreedingRules.growUpMs(baby, cow, book));
        baby.happiness = 0.5f;
        assertEquals(Math.round(90 * 0.7 * MIN), BreedingRules.growUpMs(baby, cow, book));
        baby.bornAtMs = NOW - 64 * MIN;
        assertTrue(BreedingRules.isGrown(baby, cow, book, NOW));
    }

    @Test
    void growthClockFallsBackToTamingAndNeverRunsWithoutOne() {
        SpeciesDefinition cow = cow();
        AnimalRecord baby = new AnimalRecord();
        baby.baby = true;
        assertEquals(0, BreedingRules.growthStartMs(baby));
        assertFalse(BreedingRules.isGrown(baby, cow, book(), NOW));
        baby.tamedAtMs = NOW - 100 * MIN;
        assertTrue(BreedingRules.isGrown(baby, cow, book(), NOW));
        baby.baby = false;
        assertFalse(BreedingRules.isGrown(baby, cow, book(), NOW), "adults do not grow up again");
    }

    // ---- roles

    @Test
    void aColourVariantWithItsOwnBabyRoleHasThatBaby() {
        SpeciesDefinition rabbit = new SpeciesDefinition();
        rabbit.Id = "rabbit";
        rabbit.RanchRole = "CozyRanch_Rabbit";
        rabbit.RanchBabyRole = "CozyRanch_Rabbit_Baby";
        rabbit.RanchRoleVariants = java.util.Map.of("Bunny", "CozyRanch_Rabbit_Bunny");
        rabbit.RanchBabyRoleVariants = java.util.Map.of("CozyRanch_Rabbit_Bunny", "CozyRanch_Rabbit_Bunny_Baby");
        assertEquals("CozyRanch_Rabbit_Bunny_Baby", BreedingRules.babyRole(rabbit, "CozyRanch_Rabbit_Bunny"));
        assertEquals("CozyRanch_Rabbit_Baby", BreedingRules.babyRole(rabbit, "CozyRanch_Rabbit"));
        assertEquals("CozyRanch_Rabbit_Bunny", BreedingRules.adultRole(rabbit, "CozyRanch_Rabbit_Bunny_Baby"));
        assertEquals("CozyRanch_Rabbit", BreedingRules.adultRole(rabbit, "CozyRanch_Rabbit_Baby"));
    }

    @Test
    void babiesInheritAParentsColourAndKeepItWhenGrown() {
        SpeciesDefinition frog = frog();
        assertEquals("CozyRanch_Frog_Blue", BreedingRules.babyRole(frog, "CozyRanch_Frog_Blue"));
        assertEquals("CozyRanch_Frog_Orange", BreedingRules.babyRole(frog, "CozyRanch_Frog_Orange"));
        assertEquals("CozyRanch_Frog_Blue", BreedingRules.babyRole(frog, "Frog_Blue"), "a wild-role parent maps through ranchRoleFor");
        assertEquals("CozyRanch_Frog", BreedingRules.babyRole(frog, "CozyRanch_Frog"));
        assertEquals("CozyRanch_Frog", BreedingRules.babyRole(frog, null));
        assertEquals("CozyRanch_Frog_Blue", BreedingRules.adultRole(frog, "CozyRanch_Frog_Blue"));
        assertEquals("CozyRanch_Frog", BreedingRules.adultRole(frog, "CozyRanch_Frog"));

        SpeciesDefinition cow = cow();
        assertEquals("CozyRanch_Cow_Calf", BreedingRules.babyRole(cow, "CozyRanch_Cow"));
        assertEquals("CozyRanch_Cow_Calf", BreedingRules.babyRole(cow, null));
        assertEquals("CozyRanch_Cow", BreedingRules.adultRole(cow, "CozyRanch_Cow_Calf"));
        assertEquals("CozyRanch_Cow", BreedingRules.adultRole(cow, null));
    }
}
