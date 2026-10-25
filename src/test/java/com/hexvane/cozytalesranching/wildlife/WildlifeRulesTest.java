package com.hexvane.cozytalesranching.wildlife;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.wildlife.WildlifeConfig.SpeciesWildlife;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.Candidate;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.Cap;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.Counts;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.HouseFacts;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.LeaveStep;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WildlifeRulesTest {
    private static final long NOW = 10_000_000_000L;
    private static final WildlifeConfig CONFIG = WildlifeConfig.bundled();

    private static HouseFacts house(String species, boolean met, double score, Set<String> features, int residents, double happy,
                                    int favourite, Map<String, Integer> stored) {
        return new HouseFacts(UUID.randomUUID(), UUID.randomUUID(), species, true, met, (float) score, features, residents, happy, favourite, stored);
    }

    private static HouseFacts coop(double score, Set<String> features) {
        return house("chicken", true, score, features, 0, 0, 0, Map.of());
    }

    @Test
    void presentFeaturesAreTheWatchedOnesNotMissing() {
        assertEquals(Set.of("food", "hay"), WildlifeRules.presentFeatures(true, List.of("food", "hay", "dust"), List.of("dust")));
        assertTrue(WildlifeRules.presentFeatures(false, List.of("food"), List.of()).isEmpty());
    }

    @Test
    void goodHabitatAttractsItsOwnSpecies() {
        HouseFacts good = coop(1.0, Set.of("food", "hay", "flowers"));
        double s = WildlifeRules.score(CONFIG, "chicken", CONFIG.species("chicken"), good, 0, NOW);
        assertEquals(0.9, s, 1e-9);
        HouseFacts poor = coop(0.3, Set.of("food"));
        assertEquals(0, WildlifeRules.score(CONFIG, "chicken", CONFIG.species("chicken"), poor, 0, NOW));
        HouseFacts unmet = house("chicken", false, 1.0, Set.of("hay"), 0, 0, 0, Map.of());
        assertEquals(0, WildlifeRules.score(CONFIG, "chicken", CONFIG.species("chicken"), unmet, 0, NOW));
    }

    @Test
    void unscannedHouseAttractsNothing() {
        HouseFacts h = new HouseFacts(UUID.randomUUID(), UUID.randomUUID(), "chicken", false, true, 1f, Set.of(), 0, 0, 0, Map.of());
        assertTrue(WildlifeRules.candidates(CONFIG, h, Map.of(), id -> true, NOW).isEmpty());
    }

    @Test
    void unhappyResidentsLowerTheDraw() {
        HouseFacts happy = house("chicken", true, 1.0, Set.of("food"), 2, 1.0, 0, Map.of());
        HouseFacts sad = house("chicken", true, 1.0, Set.of("food"), 2, 0.0, 0, Map.of());
        SpeciesWildlife sw = CONFIG.species("chicken");
        assertEquals(0.9, WildlifeRules.score(CONFIG, "chicken", sw, happy, 0, NOW), 1e-9);
        assertEquals(0.45, WildlifeRules.score(CONFIG, "chicken", sw, sad, 0, NOW), 1e-9);
    }

    @Test
    void otherSpeciesNeverCome() {
        HouseFacts sty = house("pig", true, 0.8, Set.of("food", "water", "mud"), 0, 0, 0, Map.of());
        assertEquals(0, WildlifeRules.score(CONFIG, "frog", CONFIG.species("frog"), sty, 0, NOW), "fix 5: no frog at a muddy, watery sty");
        assertEquals(0, WildlifeRules.score(CONFIG, "cow", CONFIG.species("cow"), sty, 0, NOW));
        assertTrue(WildlifeRules.score(CONFIG, "pig", CONFIG.species("pig"), sty, 0, NOW) > 0);
        assertEquals(List.of("pig"), WildlifeRules.candidates(CONFIG, sty, Map.of(), id -> true, NOW).stream().map(Candidate::speciesId).toList());
    }

    @Test
    void cooldownBlocksTheSameSpecies() {
        HouseFacts good = coop(1.0, Set.of("food"));
        long fiveMinutesAgo = NOW - 5 * 60_000L;
        long anHourAgo = NOW - 60 * 60_000L;
        assertTrue(WildlifeRules.onCooldown(fiveMinutesAgo, 20, NOW));
        assertFalse(WildlifeRules.onCooldown(anHourAgo, 20, NOW));
        assertFalse(WildlifeRules.onCooldown(0, 20, NOW));
        assertTrue(WildlifeRules.candidates(CONFIG, good, Map.of("chicken", fiveMinutesAgo), id -> true, NOW).isEmpty());
        assertEquals(1, WildlifeRules.candidates(CONFIG, good, Map.of("chicken", anHourAgo), id -> true, NOW).size());
    }

    @Test
    void disabledSpeciesAreSkipped() {
        HouseFacts good = coop(1.0, Set.of("food"));
        assertTrue(WildlifeRules.candidates(CONFIG, good, Map.of(), id -> !id.equals("chicken"), NOW).isEmpty());
    }

    @Test
    void pickUsesTheStrongestDrawAsTheChance() {
        List<Candidate> weak = List.of(new Candidate("chicken", 0.2));
        Random rng = new Random(1);
        int came = 0;
        for (int i = 0; i < 10_000; i++) if (WildlifeRules.pick(weak, rng, false) != null) came++;
        assertTrue(came > 1700 && came < 2300, "about 20% of passes, got " + came);
        assertEquals("chicken", WildlifeRules.pick(weak, rng, true).speciesId());
        assertNull(WildlifeRules.pick(List.of(), rng, true));
    }

    @Test
    void pickDrawsSpeciesByWeight() {
        List<Candidate> two = List.of(new Candidate("chicken", 0.9), new Candidate("frog", 0.3));
        Random rng = new Random(2);
        Map<String, Integer> n = new HashMap<>();
        for (int i = 0; i < 12_000; i++) n.merge(WildlifeRules.pick(two, rng, true).speciesId(), 1, Integer::sum);
        assertTrue(n.get("chicken") > 8_500 && n.get("chicken") < 9_500, n.toString());
    }

    @Test
    void capsHoldInOrder() {
        assertEquals(Cap.OK, WildlifeRules.caps(CONFIG, new Counts(0, 0, 0)));
        assertEquals(Cap.HOUSE, WildlifeRules.caps(CONFIG, new Counts(CONFIG.MaxPerHouse, 1, 1)));
        assertEquals(Cap.OWNER, WildlifeRules.caps(CONFIG, new Counts(0, CONFIG.MaxPerOwner, CONFIG.MaxPerOwner)));
        assertEquals(Cap.WORLD, WildlifeRules.caps(CONFIG, new Counts(0, 0, CONFIG.MaxPerWorld)));
    }

    @Test
    void goldenChickenNeedsFlowersAndCorn() {
        SpeciesWildlife sw = CONFIG.species("chicken");
        HouseFacts plain = house("chicken", true, 1.0, Set.of("food", "flowers"), 1, 0.9, 4, Map.of());
        HouseFacts corn = house("chicken", true, 1.0, Set.of("food", "flowers"), 1, 0.9, 16, Map.of());
        HouseFacts noFlowers = house("chicken", true, 1.0, Set.of("food"), 1, 0.9, 64, Map.of());
        assertFalse(WildlifeRules.rareConditionsMet("chicken", sw.Rare, plain));
        assertEquals(List.of("favourite in troughs>=16"), WildlifeRules.rareMissing(sw.Rare, plain));
        assertTrue(WildlifeRules.rareConditionsMet("chicken", sw.Rare, corn));
        assertFalse(WildlifeRules.rareConditionsMet("chicken", sw.Rare, noFlowers));
        assertFalse(WildlifeRules.rareConditionsMet("frog", sw.Rare, corn), "only at a house of its own species");
    }

    @Test
    void otherRareConditions() {
        assertTrue(WildlifeRules.rareConditionsMet("pig", CONFIG.species("pig").Rare,
            house("pig", true, 0.7, Set.of("mud"), 1, 0.5, 0, Map.of("CozyRanch_Truffle", 3))));
        assertFalse(WildlifeRules.rareConditionsMet("pig", CONFIG.species("pig").Rare,
            house("pig", true, 0.7, Set.of("mud"), 1, 0.5, 0, Map.of("CozyRanch_Truffle", 2))));
        assertTrue(WildlifeRules.rareConditionsMet("cow", CONFIG.species("cow").Rare,
            house("cow", true, 0.7, Set.of("hay"), 2, 0.85, 0, Map.of())));
        assertFalse(WildlifeRules.rareConditionsMet("cow", CONFIG.species("cow").Rare,
            house("cow", true, 0.7, Set.of("hay"), 2, 0.6, 0, Map.of())));
        assertTrue(WildlifeRules.rareConditionsMet("sheep", CONFIG.species("sheep").Rare,
            house("sheep", true, 0.92, Set.of("grass", "flowers"), 0, 0, 0, Map.of())));
        assertFalse(WildlifeRules.rareConditionsMet("sheep", CONFIG.species("sheep").Rare,
            house("sheep", true, 0.8, Set.of("grass", "flowers"), 0, 0, 0, Map.of())));
        assertTrue(WildlifeRules.rareConditionsMet("frog", CONFIG.species("frog").Rare,
            house("frog", true, 0.8, Set.of("water", "lily_pads", "reeds"), 2, 0.5, 0, Map.of())));
        assertFalse(WildlifeRules.rareConditionsMet("frog", CONFIG.species("frog").Rare,
            house("frog", true, 0.8, Set.of("water", "lily_pads", "reeds"), 1, 0.5, 0, Map.of())));
    }

    @Test
    void rareRollRespectsItsCooldownAndChance() {
        SpeciesWildlife sw = CONFIG.species("chicken");
        HouseFacts corn = house("chicken", true, 1.0, Set.of("food", "flowers"), 1, 0.9, 16, Map.of());
        Random rng = new Random(3);
        int rare = 0;
        for (int i = 0; i < 10_000; i++) if (WildlifeRules.rollRare("chicken", sw.Rare, corn, 0, NOW, rng)) rare++;
        assertTrue(rare > 2200 && rare < 2800, "about a quarter, got " + rare);
        for (int i = 0; i < 1000; i++) assertFalse(WildlifeRules.rollRare("chicken", sw.Rare, corn, NOW - 10 * 60_000L, NOW, rng));
    }

    @Test
    void normalRoleSkipsMissingRoles() {
        SpeciesWildlife frog = CONFIG.species("frog");
        assertEquals("Frog_Blue", WildlifeRules.normalRole(frog, r -> r.equals("Frog_Blue"), new Random(4)));
        assertNull(WildlifeRules.normalRole(frog, r -> false, new Random(4)));
    }

    @Test
    void visitAndStayTimesStayInTheirRanges() {
        Random rng = new Random(5);
        for (int i = 0; i < 1000; i++) {
            long visit = WildlifeRules.nextVisitDelayMs(CONFIG, rng);
            long stay = WildlifeRules.stayMs(CONFIG, rng);
            assertTrue(visit >= CONFIG.MinVisitMinutes * 60_000 && visit <= CONFIG.MaxVisitMinutes * 60_000);
            assertTrue(stay >= CONFIG.MinStayMinutes * 60_000 && stay <= CONFIG.MaxStayMinutes * 60_000);
        }
    }

    @Test
    void leavingVisitorsGoWhenUnseen() {
        long since = NOW - 5_000;
        assertEquals(LeaveStep.WAIT, WildlifeRules.leaving(CONFIG, since, NOW, 5));
        assertEquals(LeaveStep.REMOVE, WildlifeRules.leaving(CONFIG, since, NOW, CONFIG.HiddenDistance + 1));
        long later = NOW - (long) (CONFIG.LeaveSeconds * 1000) - 1;
        assertEquals(LeaveStep.REMOVE, WildlifeRules.leaving(CONFIG, later, NOW, CONFIG.NearPlayerDistance + 1));
        assertEquals(LeaveStep.WAIT, WildlifeRules.leaving(CONFIG, later, NOW, 3));
        long muchLater = NOW - (long) (CONFIG.LeaveSeconds * 4000) - 1;
        assertEquals(LeaveStep.REMOVE, WildlifeRules.leaving(CONFIG, muchLater, NOW, 1));
    }
}
