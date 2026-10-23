package com.hexvane.cozytalesranching.produce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.produce.PoopRules.Decision;
import com.hexvane.cozytalesranching.produce.PoopRules.Ground;
import com.hexvane.cozytalesranching.produce.PoopRules.Kind;
import com.hexvane.cozytalesranching.produce.PoopRules.Pile;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class PoopRulesTest {
    private static final PoopTuning T = new PoopTuning().normalise();

    @Test
    void countLivesInTheBlockState() {
        assertEquals(1, PoopRules.countOf("CozyRanch_Poop_Pile"));
        assertEquals(1, PoopRules.countOf("*CozyRanch_Poop_Pile_State_Definitions_default"));
        for (int n = 1; n <= 8; n++) assertEquals(n, PoopRules.countOf(PoopRules.typeIdFor(n)));
        assertEquals("*CozyRanch_Poop_Pile_State_Definitions_Count3", PoopRules.typeIdFor(3));
        assertEquals("CozyRanch_Poop_Pile", PoopRules.typeIdFor(0));
        assertEquals(PoopRules.typeIdFor(8), PoopRules.typeIdFor(12));
        assertEquals(0, PoopRules.countOf("Soil_Dirt"));
        assertEquals(0, PoopRules.countOf("Ingredient_Poop"));
        assertEquals(0, PoopRules.countOf("*CozyRanch_Poop_Pile_State_Definitions_Count9"));
        assertEquals(0, PoopRules.countOf("*CozyRanch_Poop_Pile_State_Definitions_Countx"));
        assertEquals(0, PoopRules.countOf(null));
    }

    @Test
    void sizeGrowsWithCount() {
        assertEquals(PoopRules.Size.SMALL, PoopRules.sizeOf(1));
        assertEquals(PoopRules.Size.MEDIUM, PoopRules.sizeOf(2));
        assertEquals(PoopRules.Size.MEDIUM, PoopRules.sizeOf(3));
        assertEquals(PoopRules.Size.LARGE, PoopRules.sizeOf(4));
        assertEquals(PoopRules.Size.LARGE, PoopRules.sizeOf(8));
    }

    @Test
    void nearbyPileGrowsInsteadOfANewOne() {
        Decision d = PoopRules.decide(10, 64, 10, List.of(new Pile(12, 64, 11, 2), new Pile(30, 64, 30, 1)), 1, T);
        assertEquals(Kind.MERGE, d.kind());
        assertEquals(new Pile(12, 64, 11, 2), d.target());
    }

    @Test
    void nearestNotFullPileWins() {
        Decision d = PoopRules.decide(0, 64, 0, List.of(new Pile(3, 64, 0, 1), new Pile(1, 64, 0, 8), new Pile(0, 64, 2, 4)), 2, T);
        assertEquals(new Pile(0, 64, 2, 4), d.target());
    }

    @Test
    void fullPilesNearbyStopNewOnes() {
        Decision d = PoopRules.decide(0, 64, 0, List.of(new Pile(1, 64, 1, 8)), 0, T);
        assertEquals(Kind.SKIP, d.kind());
        assertEquals("piles_full", d.reason());
    }

    @Test
    void pilesOutsideTheRadiusOrTwoUpDoNotCount() {
        assertEquals(Kind.PLACE, PoopRules.decide(0, 64, 0, List.of(new Pile(4, 64, 0, 1)), 0, T).kind());
        assertEquals(Kind.PLACE, PoopRules.decide(0, 64, 0, List.of(new Pile(1, 66, 0, 1)), 0, T).kind());
        assertEquals(Kind.MERGE, PoopRules.decide(0, 64, 0, List.of(new Pile(3, 65, -3, 1)), 0, T).kind());
    }

    @Test
    void aPlayersNewPileNeverMergesWithItself() {
        assertEquals(Kind.PLACE, PoopRules.decide(5, 64, 5, List.of(new Pile(5, 64, 5, 1)), -1, T, false).kind());
        Decision d = PoopRules.decide(5, 64, 5, List.of(new Pile(5, 64, 5, 1), new Pile(6, 64, 5, 2)), -1, T, false);
        assertEquals(new Pile(6, 64, 5, 2), d.target());
    }

    @Test
    void anAnimalStandingInAPileGrowsThatPile() {
        // piles have no collision, so an animal often stands in one; found live on 15541 (it read "cell_taken")
        Decision d = PoopRules.decide(5, 64, 5, List.of(new Pile(6, 64, 5, 2), new Pile(5, 64, 5, 1)), 0, T);
        assertEquals(Kind.MERGE, d.kind());
        assertEquals(new Pile(5, 64, 5, 1), d.target());
    }

    @Test
    void houseLimitStopsNewPilesButNotMerging() {
        assertEquals("house_full", PoopRules.decide(0, 64, 0, List.of(), T.MaxPilesPerHouse, T).reason());
        assertEquals(Kind.MERGE, PoopRules.decide(0, 64, 0, List.of(new Pile(1, 64, 0, 3)), T.MaxPilesPerHouse + 5, T).kind());
        assertEquals(Kind.PLACE, PoopRules.decide(0, 64, 0, List.of(), -1, T).kind());
    }

    @Test
    void groundRules() {
        List<String> ex = T.ExcludedGround;
        assertEquals("ok", PoopRules.groundCheck(new Ground("Soil_Grass", true, false, null, false, null, null), ex));
        assertEquals("cell_taken", PoopRules.groundCheck(new Ground("Soil_Grass", true, false, "Plant_Grass_Tall", false, null, null), ex));
        assertEquals("cell_taken", PoopRules.groundCheck(new Ground("Soil_Grass", true, false, null, true, null, null), ex));
        assertEquals("water", PoopRules.groundCheck(new Ground("Soil_Mud", true, false, null, false, "Water_Source", null), ex));
        assertEquals("water", PoopRules.groundCheck(new Ground(null, false, false, null, false, null, "Water"), ex));
        assertEquals("no_ground", PoopRules.groundCheck(new Ground(null, false, false, null, false, null, null), ex));
        assertEquals("no_ground", PoopRules.groundCheck(new Ground("Plant_Fern", false, false, null, false, null, null), ex));
        assertEquals("excluded_ground", PoopRules.groundCheck(new Ground("Soil_Dirt_Tilled_Farmland", true, false, null, false, null, null), ex));
        assertEquals("excluded_ground", PoopRules.groundCheck(new Ground("Soil_Pathway", true, false, null, false, null, null), ex));
        assertEquals("excluded_ground", PoopRules.groundCheck(new Ground("Wood_Oak_Planks", true, false, null, false, null, null), ex));
        assertEquals("excluded_ground", PoopRules.groundCheck(new Ground("CozyRanch_House_CowBarn", true, false, null, false, null, null), ex));
        assertEquals("on_building", PoopRules.groundCheck(new Ground("Rock_Stone", true, true, null, false, null, null), ex));
    }

    @Test
    void whoMayPoop() {
        PoopRules.Animal ok = new PoopRules.Animal(true, true, true, false, true, true, true);
        assertEquals("ok", PoopRules.mayPoop(ok, false));
        assertEquals("not_owned", PoopRules.mayPoop(new PoopRules.Animal(false, true, true, false, true, true, true), false));
        assertEquals("no_house", PoopRules.mayPoop(new PoopRules.Animal(true, false, true, false, true, true, true), false));
        assertEquals("inside", PoopRules.mayPoop(new PoopRules.Animal(true, true, false, false, true, true, true), false));
        assertEquals("baby", PoopRules.mayPoop(new PoopRules.Animal(true, true, true, true, true, true, true), false));
        assertEquals("ok", PoopRules.mayPoop(new PoopRules.Animal(true, true, true, true, true, true, true), true));
        assertEquals("species_off", PoopRules.mayPoop(new PoopRules.Animal(true, true, true, false, false, true, true), false));
        assertEquals("not_loaded", PoopRules.mayPoop(new PoopRules.Animal(true, true, true, false, true, false, true), false));
        assertEquals("night", PoopRules.mayPoop(new PoopRules.Animal(true, true, true, false, true, true, false), false));
    }

    @Test
    void chanceAndCooldown() {
        assertEquals(0.12, PoopRules.passChance(0.12, 60), 1e-12);
        // four 15 s passes add up to the per-minute chance
        double p = PoopRules.passChance(0.12, 15);
        assertEquals(0.12, 1 - Math.pow(1 - p, 4), 1e-12);
        assertEquals(0.0, PoopRules.passChance(0, 15), 0);
        assertEquals(1.0, PoopRules.passChance(1, 15), 0);
        assertTrue(PoopRules.cooledDown(0, 1000, 6));
        assertFalse(PoopRules.cooledDown(1_000_000, 1_000_000 + 5 * 60_000, 6));
        assertTrue(PoopRules.cooledDown(1_000_000, 1_000_000 + 6 * 60_000, 6));
        assertTrue(PoopRules.cooledDown(5_000_000, 1_000, 6)); // clock went back
    }

    @Test
    void doorClearance() {
        assertTrue(PoopRules.nearDoor(10, 10, 10.5, 11.5, 2));
        assertFalse(PoopRules.nearDoor(10, 14, 10.5, 11.5, 2));
    }

    @Test
    void indexKeysRoundTrip() {
        int[][] cells = {{0, 0, 0}, {-1, -1, -1}, {123456, 300, -98765}, {-30_000_000, 2047, 30_000_000}, {5, -2048, -5}};
        for (int[] c : cells) {
            long k = PoopService.key(c[0], c[1], c[2]);
            assertEquals(c[0], PoopService.keyX(k));
            assertEquals(c[1], PoopService.keyY(k));
            assertEquals(c[2], PoopService.keyZ(k));
        }
    }

    @Test
    void bundledTuningParses() throws Exception {
        try (var in = Objects.requireNonNull(PoopTuning.class.getResourceAsStream(PoopTuning.RESOURCE))) {
            PoopTuning t = PoopTuning.read(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertTrue(t.Enabled);
            assertEquals(3, t.MergeRadius);
            assertEquals(8, t.MaxCount);
            assertTrue(t.speciesOn("cow"));
            assertTrue(t.speciesOn("Cow"));
            assertFalse(t.speciesOn("frog"));
            assertTrue(t.speciesOn("unknown_species"));
            assertTrue(t.ExcludedGround.contains("Farmland"));
        }
        PoopTuning wild = PoopTuning.read(new java.io.StringReader("{\"MaxCount\": 40, \"MergeRadius\": -2, \"ChancePerMinute\": 3, \"Species\": null}"));
        assertEquals(8, wild.MaxCount);
        assertEquals(0, wild.MergeRadius);
        assertEquals(1.0, wild.ChancePerMinute, 0);
        assertTrue(wild.speciesOn("cow"));
        assertNull(wild.Species.get("cow"));
    }
}
