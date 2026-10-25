package com.hexvane.cozytalesranching.wildlife;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.wildlife.WildlifeConfig.SpeciesWildlife;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.HouseFacts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Fix 4 notes 13 and 14: visitors need more than half of the house's loves, and snails do not go to rabbit hutches (fix 5: no species visits another's house). */
class LovesRuleTest {
    private static final long NOW = 10_000_000_000L;
    private static final WildlifeConfig CONFIG = WildlifeConfig.bundled();

    private static HouseFacts house(String species, Set<String> features, int lovesMet, int lovesTotal) {
        return new HouseFacts(UUID.randomUUID(), UUID.randomUUID(), species, true, true, 1.0, features, 2, 1.0, 9, Map.of("CozyRanch_Seed_Pouch", 9),
            lovesMet, lovesTotal);
    }

    @Test
    void moreThanHalfIsNeeded() {
        assertFalse(WildlifeRules.lovesMet(0, 3));
        assertFalse(WildlifeRules.lovesMet(1, 3));
        assertTrue(WildlifeRules.lovesMet(2, 3));
        assertFalse(WildlifeRules.lovesMet(2, 4), "exactly half is not more than half");
        assertTrue(WildlifeRules.lovesMet(3, 4));
        assertFalse(WildlifeRules.lovesMet(0, 1));
        assertTrue(WildlifeRules.lovesMet(1, 1));
        assertTrue(WildlifeRules.lovesMet(0, 0), "a species that loves nothing in particular is not held back");
    }

    @Test
    void lovesAreCountedOnce() {
        assertEquals(2, WildlifeRules.countLovesMet(List.of("flowers", "water", "flowers", "shade"), Set.of("flowers", "water", "food")));
    }

    @Test
    void ownSpeciesStaysAwayFromAHouseWithTooFewLoves() {
        SpeciesWildlife rabbit = CONFIG.species("rabbit");
        Set<String> all = Set.of("food", "open_grass", "carrot_patch", "tall_grass", "flowers");
        assertEquals(0, WildlifeRules.score(CONFIG, "rabbit", rabbit, house("rabbit", all, 1, 3), 0, NOW));
        assertTrue(WildlifeRules.score(CONFIG, "rabbit", rabbit, house("rabbit", all, 2, 3), 0, NOW) > 0);
    }

    @Test
    void otherSpeciesNeverComeEvenWithEveryLoveMet() {
        SpeciesWildlife frog = CONFIG.species("frog");
        Set<String> sty = Set.of("food", "water", "mud", "vegetables");
        assertEquals(0, WildlifeRules.score(CONFIG, "frog", frog, house("pig", sty, 2, 4), 0, NOW));
        assertEquals(0, WildlifeRules.score(CONFIG, "frog", frog, house("pig", sty, 3, 3), 0, NOW), "fix 5: a frog never visits a pigsty");
    }

    @Test
    void raresNeedTheLovesToo() {
        SpeciesWildlife rabbit = CONFIG.species("rabbit");
        Set<String> all = Set.of("food", "open_grass", "carrot_patch", "tall_grass", "flowers");
        assertTrue(WildlifeRules.rareConditionsMet("rabbit", rabbit.Rare, house("rabbit", all, 3, 3)));
        assertFalse(WildlifeRules.rareConditionsMet("rabbit", rabbit.Rare, house("rabbit", all, 1, 3)));
        assertTrue(WildlifeRules.rareMissing(rabbit.Rare, house("rabbit", all, 1, 3)).getFirst().startsWith("loves"));
    }

    @Test
    void everySpeciesObeysTheRule() {
        Set<String> everything = Set.of("food", "water", "flowers", "grass", "hay", "mud", "shade", "open_space");
        for (Map.Entry<String, SpeciesWildlife> e : CONFIG.Species.entrySet()) {
            for (String houseSpecies : List.of(e.getKey(), "frog", "pig", "cow", "sheep", "chicken", "rabbit")) {
                assertEquals(0, WildlifeRules.score(CONFIG, e.getKey(), e.getValue(), house(houseSpecies, everything, 1, 3), 0, NOW),
                    e.getKey() + " at a " + houseSpecies + " house with 1 of 3 loves");
            }
        }
    }

    @Test
    void snailsDoNotVisitRabbitHutches() {
        SpeciesWildlife snail = CONFIG.species("snail");
        Set<String> lovelyHutch = Set.of("food", "open_grass", "carrot_patch", "tall_grass", "flowers", "water", "shade");
        assertEquals(0, WildlifeRules.score(CONFIG, "snail", snail, house("rabbit", lovelyHutch, 3, 3), 0, NOW));
    }
}
