package com.hexvane.cozytalesranching.care;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FeedTest {
    private static final long MIN = 60_000L;
    private static final long NOW = 1_000_000_000_000L;
    private static final float EPS = 1e-4f;
    private static HabitatConfig config;
    private static Map<String, String> feeds;

    @BeforeAll
    static void readRealConfig() throws Exception {
        try (InputStream in = FeedTest.class.getResourceAsStream("/Server/CozyTalesRanching/Habitat.json")) {
            assertNotNull(in);
            config = HabitatConfig.read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        feeds = config.feedItems();
    }

    private static SpeciesDefinition cow() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "cow";
        s.BasicFoods = List.of("Wheat");
        s.PreferredFoods = List.of("Corn");
        s.FavoriteTreats = List.of("Pumpkin");
        return s;
    }

    private static AnimalRecord animal() {
        AnimalRecord a = new AnimalRecord();
        a.animalId = UUID.randomUUID();
        a.speciesId = "cow";
        return a;
    }

    @Test
    void theFourFeedsAreData() {
        assertEquals(Map.of(
            "CozyRanch_Feed_Hearty", "hearty",
            "CozyRanch_Feed_Sweet", "sweet",
            "CozyRanch_Feed_Breeders", "breeders",
            "CozyRanch_Feed_Premium", "premium"), feeds);
        assertEquals(120 * MIN, config.feedDurationMs("CozyRanch_Feed_Hearty"));
        assertEquals(60 * MIN, config.feedDurationMs("CozyRanch_Feed_Sweet"));
        assertNull(config.feedBonus("Plant_Crop_Wheat_Item"));
        assertEquals(0L, config.feedDurationMs("Plant_Crop_Wheat_Item"));
    }

    @Test
    void anyAnimalEatsFeedFirstWhenNoBonusRuns() {
        CareRules.Meal m = CareRules.bestMeal(cow(), Set.of("Wheat", "Pumpkin", "CozyRanch_Feed_Sweet"), feeds, false).orElseThrow();
        assertEquals("CozyRanch_Feed_Sweet", m.item());
        assertEquals("sweet", m.feedBonus());
        assertEquals(FoodKind.PREFERRED, m.kind(), "a feed counts as preferred food");
        SpeciesDefinition frog = new SpeciesDefinition();
        frog.Id = "frog";
        assertEquals("hearty", CareRules.bestMeal(frog, Set.of("CozyRanch_Feed_Hearty"), feeds, false).orElseThrow().feedBonus(),
            "a species that lists no feed still eats it");
    }

    @Test
    void whileABonusRunsFeedIsTheLastResort() {
        assertEquals("Pumpkin", CareRules.bestMeal(cow(), Set.of("Wheat", "Pumpkin", "CozyRanch_Feed_Sweet"), feeds, true).orElseThrow().item());
        assertEquals("Wheat", CareRules.bestMeal(cow(), Set.of("Wheat", "CozyRanch_Feed_Sweet"), feeds, true).orElseThrow().item());
        assertEquals("CozyRanch_Feed_Sweet", CareRules.bestMeal(cow(), Set.of("Rock", "CozyRanch_Feed_Sweet"), feeds, true).orElseThrow().item());
        CareRules.Meal feed = new CareRules.Meal("CozyRanch_Feed_Sweet", FoodKind.PREFERRED, "sweet");
        CareRules.Meal treat = new CareRules.Meal("Pumpkin", FoodKind.FAVORITE, null);
        CareRules.Meal basic = new CareRules.Meal("Wheat", FoodKind.BASIC, null);
        assertTrue(CareRules.mealRank(feed, false) > CareRules.mealRank(treat, false));
        assertTrue(CareRules.mealRank(feed, true) < CareRules.mealRank(basic, true));
    }

    @Test
    void feedingSetsTheBonusAndANewFeedReplacesIt() {
        AnimalRecord a = animal();
        Feeding.apply(a, "sweet", 60 * MIN, 60 * MIN, 0.5, NOW);
        assertEquals("sweet", a.feedBonus);
        assertEquals(NOW + 60 * MIN, a.feedBonusUntilMs);
        Feeding.apply(a, "hearty", 120 * MIN, 60 * MIN, 0.5, NOW + MIN);
        assertEquals("hearty", a.feedBonus, "one bonus at a time");
        assertEquals(NOW + 121 * MIN, a.feedBonusUntilMs);
        assertFalse(Feeding.expire(a, NOW + 120 * MIN));
        assertTrue(Feeding.expire(a, NOW + 121 * MIN));
        assertNull(a.feedBonus);
        assertEquals(0L, a.feedBonusUntilMs);
    }

    @Test
    void heartyKeepsTheFoodPartSatisfied() {
        CareRules.Inputs hungry = new CareRules.Inputs(false, FoodKind.PREFERRED, false, true, true, 1f, false, false, 1.0);
        CareRules.Inputs hearty = new CareRules.Inputs(false, FoodKind.PREFERRED, false, true, true, 1f, false, true, 1.0);
        assertEquals(0f, CareRules.target(hungry).fed(), EPS);
        assertEquals(CareRules.FED, CareRules.target(hearty).fed(), EPS);
        assertEquals(CareRules.PREFERRED, CareRules.target(hearty).preferred(), EPS, "the feed was a preferred meal");
        AnimalRecord a = animal();
        Feeding.apply(a, "hearty", 120 * MIN, 0, 0.5, NOW);
        assertTrue(Feeding.active(a, CareRules.BONUS_HEARTY, NOW + 119 * MIN));
        assertFalse(Feeding.active(a, CareRules.BONUS_HEARTY, NOW + 120 * MIN));
        assertFalse(Feeding.active(a, CareRules.BONUS_SWEET, NOW));
    }

    @Test
    void sweetRaisesFriendshipGain() {
        assertEquals(1f, CareRules.friendshipMultiplier(1.0, false, 1.5f), EPS);
        assertEquals(1.5f, CareRules.friendshipMultiplier(1.0, true, 1.5f), EPS);
        assertEquals(1.875f, CareRules.friendshipMultiplier(1.25, true, 1.5f), EPS, "sweet stacks with Affectionate");
        AnimalRecord a = animal();
        Feeding.apply(a, "sweet", 60 * MIN, 0, 0.5, System.currentTimeMillis());
        assertEquals(config.FeedEffects.SweetFriendshipMultiplier, Feeding.friendshipMultiplier(a, config, System.currentTimeMillis()), EPS);
    }

    @Test
    void breedersMovesTheCooldownOnOncePerFeed() {
        long cooldown = 60 * MIN;
        AnimalRecord a = animal();
        a.lastBredAtMs = NOW - 10 * MIN;
        Feeding.apply(a, "breeders", 60 * MIN, cooldown, 0.5, NOW);
        assertEquals("breeders", a.feedBonus);
        assertEquals(NOW - 40 * MIN, a.lastBredAtMs, "half the cooldown");
        Feeding.apply(a, "breeders", 60 * MIN, cooldown, 0.5, NOW);
        assertEquals(NOW - 60 * MIN, a.lastBredAtMs, "never past ready");
        Feeding.apply(a, "breeders", 60 * MIN, cooldown, 0.5, NOW);
        assertEquals(NOW - 60 * MIN, a.lastBredAtMs, "already ready: unchanged");

        AnimalRecord never = animal();
        Feeding.apply(never, "breeders", 60 * MIN, cooldown, 0.5, NOW);
        assertEquals(0L, never.lastBredAtMs, "an animal that never bred stays never-bred");
    }

    @Test
    void premiumOnlySetsTheBonus() {
        AnimalRecord a = animal();
        a.lastBredAtMs = NOW - 10 * MIN;
        a.lastFedAtMs = 5;
        a.happiness = 0.6f;
        Feeding.apply(a, "premium", 90 * MIN, 60 * MIN, 0.5, NOW);
        assertEquals("premium", a.feedBonus);
        assertEquals(NOW - 10 * MIN, a.lastBredAtMs);
        assertEquals(5, a.lastFedAtMs);
        assertEquals(0.6f, a.happiness, EPS);
    }
}
