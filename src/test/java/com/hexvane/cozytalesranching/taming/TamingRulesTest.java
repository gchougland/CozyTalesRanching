package com.hexvane.cozytalesranching.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import com.hexvane.cozytalesranching.taming.TamingRules.Gate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TamingRulesTest {
    private static SpeciesDefinition cow() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "cow";
        s.BasicFoods = List.of("Plant_Crop_Wheat_Item", "Plant_Crop_Lettuce_Item");
        s.PreferredFoods = List.of("Plant_Crop_Corn_Item");
        s.FavoriteTreats = List.of("Plant_Crop_Pumpkin_Item", "Plant_Crop_Lettuce_Item");
        s.FeedingsToTame = 3;
        return s;
    }

    @Test
    void foodKindPicksTheBestList() {
        SpeciesDefinition cow = cow();
        assertEquals(FoodKind.BASIC, TamingRules.foodKind(cow, "Plant_Crop_Wheat_Item"));
        assertEquals(FoodKind.PREFERRED, TamingRules.foodKind(cow, "Plant_Crop_Corn_Item"));
        assertEquals(FoodKind.FAVORITE, TamingRules.foodKind(cow, "Plant_Crop_Pumpkin_Item"));
        assertEquals(FoodKind.FAVORITE, TamingRules.foodKind(cow, "Plant_Crop_Lettuce_Item"));
        assertEquals(FoodKind.NONE, TamingRules.foodKind(cow, "Rock_Stone"));
        assertEquals(FoodKind.NONE, TamingRules.foodKind(cow, null));
    }

    @Test
    void favouritesCountDoubleTowardsTaming() {
        SpeciesDefinition cow = cow();
        TameProgress progress = new TameProgress();
        UUID animal = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        int needed = TamingRules.feedingsToTame(cow);
        int total = progress.add(animal, player, TamingRules.tamePoints(FoodKind.BASIC), 0);
        assertEquals(1, total);
        total = progress.add(animal, player, TamingRules.tamePoints(FoodKind.FAVORITE), 1000);
        assertEquals(3, total);
        assertTrue(total >= needed);
    }

    @Test
    void anotherPlayerOrALongPauseStartsOver() {
        TameProgress progress = new TameProgress();
        UUID animal = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        progress.add(animal, alice, 2, 0);
        assertEquals(1, progress.add(animal, bob, 1, 10));
        assertEquals(1, progress.add(animal, bob, 1, 10 + TamingRules.TAME_PROGRESS_TIMEOUT_MS));
        progress.prune(10 + 2 * TamingRules.TAME_PROGRESS_TIMEOUT_MS);
        assertEquals(0, progress.size());
    }

    @Test
    void feedingsToTameIsAtLeastOne() {
        SpeciesDefinition s = cow();
        s.FeedingsToTame = 0;
        assertEquals(1, TamingRules.feedingsToTame(s));
    }

    @Test
    void gateOrder() {
        assertEquals(Gate.OK, TamingRules.tameGate(false, false, true, 0, 64));
        assertEquals(Gate.BLOCKED_WORLD, TamingRules.tameGate(true, false, true, 0, 64));
        assertEquals(Gate.OK, TamingRules.tameGate(true, true, true, 0, 64));
        assertEquals(Gate.NO_PERMISSION, TamingRules.tameGate(false, false, false, 0, 64));
        assertEquals(Gate.NO_PERMISSION, TamingRules.tameGate(true, true, false, 0, 64));
        assertEquals(Gate.AT_LIMIT, TamingRules.tameGate(false, false, true, 64, 64));
        assertEquals(Gate.BLOCKED_WORLD, TamingRules.tameGate(true, false, false, 99, 64));
    }

    @Test
    void friendshipRollsIntoHeartsAndStopsAtFive() {
        TamingRules.Friendship f = TamingRules.addFriendship(0, 0.9f, 0.25f);
        assertEquals(1, f.hearts());
        assertEquals(0.15f, f.progress(), 1e-5);
        f = TamingRules.addFriendship(4, 0.9f, 0.5f);
        assertEquals(5, f.hearts());
        assertEquals(0f, f.progress());
        f = TamingRules.addFriendship(5, 0f, 3f);
        assertEquals(5, f.hearts());
        f = TamingRules.addFriendship(2, 0.5f, -1f);
        assertEquals(2, f.hearts());
        assertEquals(0.5f, f.progress());
    }

    @Test
    void pettingOncePerGameDay() {
        long day = 86_400_000L;
        assertTrue(TamingRules.canPetForFriendship(0, 5 * day));
        assertFalse(TamingRules.canPetForFriendship(5 * day + 10, 5 * day + 20_000));
        assertTrue(TamingRules.canPetForFriendship(5 * day + 10, 6 * day));
    }

    @Test
    void feedingCooldown() {
        assertTrue(TamingRules.canFeedForFriendship(0, 1));
        assertFalse(TamingRules.canFeedForFriendship(1000, 1000 + TamingRules.FEED_COOLDOWN_MS - 1));
        assertTrue(TamingRules.canFeedForFriendship(1000, 1000 + TamingRules.FEED_COOLDOWN_MS));
    }

    @Test
    void shyAnimalsOnlyStartleAtAHurriedApproach() {
        assertFalse(TamingRules.startles(1.0, true, true, 0.0));
        assertFalse(TamingRules.startles(1.0, false, false, 0.0));
        assertTrue(TamingRules.startles(0.7, false, true, 0.5));
        assertFalse(TamingRules.startles(0.7, false, true, 0.8));
    }

    @Test
    void namesAreCleaned() {
        assertEquals("Daisy May", TamingRules.cleanName("  Daisy \t  May \n", 24));
        assertEquals("abc", TamingRules.cleanName("abcdef", 3));
        assertNull(TamingRules.cleanName("   ", 24));
        assertNull(TamingRules.cleanName(null, 24));
    }
}
