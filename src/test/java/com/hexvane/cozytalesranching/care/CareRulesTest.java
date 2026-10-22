package com.hexvane.cozytalesranching.care;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CareRulesTest {
    private static final float EPS = 1e-4f;
    private static final long MIN = 60_000L;

    private static SpeciesDefinition cow() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "cow";
        s.BasicFoods = List.of("Wheat", "Lettuce");
        s.PreferredFoods = List.of("Corn", "Cauliflower");
        s.FavoriteTreats = List.of("Pumpkin");
        return s;
    }

    private static SpeciesDefinition sheep() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "sheep";
        s.BasicFoods = List.of("Wheat", "Lettuce");
        s.PreferredFoods = List.of("Carrot");
        s.FavoriteTreats = List.of("Lettuce");
        return s;
    }

    @Test
    void bestFoodIsTreatThenPreferredThenBasic() {
        SpeciesDefinition cow = cow();
        assertEquals(new CareRules.Choice("Pumpkin", FoodKind.FAVORITE), CareRules.bestFood(cow, Set.of("Wheat", "Corn", "Pumpkin")).orElseThrow());
        assertEquals(new CareRules.Choice("Corn", FoodKind.PREFERRED), CareRules.bestFood(cow, Set.of("Wheat", "Corn")).orElseThrow());
        assertEquals(new CareRules.Choice("Wheat", FoodKind.BASIC), CareRules.bestFood(cow, Set.of("Wheat", "Rock")).orElseThrow());
        assertTrue(CareRules.bestFood(cow, Set.of("Rock", "Crickets")).isEmpty());
    }

    @Test
    void aTreatThatIsAlsoBasicCountsAsTreat() {
        assertEquals(FoodKind.FAVORITE, CareRules.bestFood(sheep(), Set.of("Lettuce")).orElseThrow().kind());
    }

    @Test
    void onlyFoodSomeSpeciesEatsGoesIn() {
        List<SpeciesDefinition> all = List.of(cow(), sheep());
        assertTrue(CareRules.isAnyFood(all, "Carrot"));
        assertFalse(CareRules.isAnyFood(all, "Rock_Stone"));
    }

    @Test
    void mealIntervalIsOneGameDayWithinBounds() {
        assertEquals(30 * MIN, CareRules.mealIntervalMs(1800));
        assertEquals(5 * MIN, CareRules.mealIntervalMs(60));
        assertEquals(60 * MIN, CareRules.mealIntervalMs(10_000));
        assertEquals(CareRules.DEFAULT_MEAL_INTERVAL_MS, CareRules.mealIntervalMs(0));
        assertTrue(CareRules.mealDue(0, 1000, 30 * MIN));
        assertFalse(CareRules.mealDue(1000, 1000 + 29 * MIN, 30 * MIN));
        assertTrue(CareRules.mealDue(1000, 1000 + 30 * MIN, 30 * MIN));
        assertTrue(CareRules.fedRecently(1000, 1000 + 59 * MIN, 30 * MIN), "fed lasts two meal intervals");
        assertFalse(CareRules.fedRecently(1000, 1000 + 60 * MIN, 30 * MIN));
        assertFalse(CareRules.fedRecently(0, 1000, 30 * MIN));
    }

    @Test
    void pettingCountsForOneGameDay() {
        long day = CareRules.GAME_DAY_MS;
        assertTrue(CareRules.pettedRecently(5 * day, 5 * day + day / 2));
        assertFalse(CareRules.pettedRecently(5 * day, 6 * day + 1));
        assertFalse(CareRules.pettedRecently(0, day));
    }

    @Test
    void neglectedAnimalSitsAtTheFloor() {
        CareRules.Breakdown b = CareRules.target(new CareRules.Inputs(false, FoodKind.NONE, false, false, false, 0f, false));
        assertEquals(CareRules.FLOOR, b.target(), EPS);
    }

    @Test
    void fedHousedAnimalWithHabitatCanBreed() {
        CareRules.Breakdown b = CareRules.target(new CareRules.Inputs(true, FoodKind.BASIC, false, true, true, 0.5f, false));
        assertEquals(0.30f + 0.20f + 0.10f + 0.10f + 0.05f, b.target(), EPS);
        assertTrue(b.target() >= 0.7f, "the default BreedingMinHappiness is reachable with basic food");
        assertEquals(0f, b.preferred(), EPS);
    }

    @Test
    void everythingAddsUpToFull() {
        CareRules.Breakdown b = CareRules.target(new CareRules.Inputs(true, FoodKind.FAVORITE, true, true, true, 1f, true));
        assertEquals(1f, b.target(), EPS);
        assertEquals(CareRules.PREFERRED, b.preferred(), EPS);
        assertEquals(CareRules.TREAT, b.treat(), EPS);
    }

    @Test
    void habitatOnlyCountsWithAHouse() {
        CareRules.Breakdown b = CareRules.target(new CareRules.Inputs(true, FoodKind.PREFERRED, false, false, true, 1f, true));
        assertEquals(0f, b.habitatMet(), EPS);
        assertEquals(0f, b.habitatScore(), EPS);
        assertEquals(0.30f + 0.20f + 0.10f + 0.10f, b.target(), EPS);
    }

    @Test
    void driftIsSlowAndRisesFasterThanItFalls() {
        float up = CareRules.drift(0.4f, 1.0f, CareRules.RISE_TAU_MS);
        assertEquals(0.4f + 0.6f * (1 - (float) Math.exp(-1)), up, 1e-3f);
        assertTrue(CareRules.drift(0.4f, 1.0f, MIN) - 0.4f < 0.08f, "one minute moves it only a little");
        float down = CareRules.drift(1.0f, 0.4f, CareRules.RISE_TAU_MS);
        assertTrue(1.0f - down < up - 0.4f, "falling is slower than rising");
        assertEquals(0.5f, CareRules.drift(0.5f, 0.5f, 10 * MIN), EPS);
    }

    @Test
    void driftNeverGoesBelowTheFloorAndCapsLongGaps() {
        assertEquals(CareRules.FLOOR, CareRules.drift(0.1f, 0f, 0), EPS);
        float low = CareRules.drift(0.31f, 0f, 1000 * MIN);
        assertTrue(low >= CareRules.FLOOR && low < 0.31f);
        float capped = CareRules.drift(0.4f, 1.0f, 10_000 * MIN);
        assertEquals(CareRules.drift(0.4f, 1.0f, CareRules.MAX_STEP_MS), capped, EPS);
        assertTrue(capped < 0.95f, "a restart does not jump straight to the target");
        assertEquals(CareRules.FLOOR, CareRules.drift(Float.NaN, 0.3f, MIN), EPS);
    }
}
