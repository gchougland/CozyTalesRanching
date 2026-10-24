package com.hexvane.cozytalesranching.predator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.predator.CalmRules.BowlLook;
import com.hexvane.cozytalesranching.predator.CalmRules.FeedGate;
import com.hexvane.cozytalesranching.predator.CalmRules.Phase;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalmRulesTest {
    static SpeciesDefinition wolf() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "wolf";
        s.Predator = true;
        s.BaitItems = List.of("Food_Wildmeat_Raw", "Food_Beef_Raw");
        s.CalmMinutes = 3.0;
        return s;
    }

    @Test
    void onlyPredatorsTakeBait() {
        SpeciesDefinition cow = new SpeciesDefinition();
        cow.BaitItems = List.of("Food_Wildmeat_Raw");
        assertFalse(CalmRules.isBait(cow, "Food_Wildmeat_Raw"), "not a predator");
        assertTrue(CalmRules.isBait(wolf(), "Food_Wildmeat_Raw"));
        assertTrue(CalmRules.isBait(wolf(), "Food_Beef_Raw"));
        assertFalse(CalmRules.isBait(wolf(), "Food_Fish_Raw"), "not this species' bait");
        assertFalse(CalmRules.isBait(wolf(), null));
    }

    @Test
    void feedingNeedsCalm() {
        SpeciesDefinition cow = new SpeciesDefinition();
        assertSame(FeedGate.NORMAL, CalmRules.feedGate(cow, null));
        assertSame(FeedGate.TOO_WILD, CalmRules.feedGate(wolf(), null));
        assertSame(FeedGate.TOO_WILD, CalmRules.feedGate(wolf(), Phase.LURED), "still walking to the bowl");
        assertSame(FeedGate.TOO_WILD, CalmRules.feedGate(wolf(), Phase.SPOOKED));
        assertSame(FeedGate.CALM_PREDATOR, CalmRules.feedGate(wolf(), Phase.CALM));
    }

    @Test
    void calmLastsCalmMinutesAndFeedingTopsItUp() {
        SpeciesDefinition w = wolf();
        long t0 = 1_000_000;
        long until = CalmRules.calmUntil(t0, w);
        assertEquals(t0 + 180_000, until);
        // fed after one minute: back to a full three minutes from then
        assertEquals(t0 + 60_000 + 180_000, CalmRules.extendedUntil(until, t0 + 60_000, w));
        // feeding never shortens it
        assertEquals(until + 999_999, CalmRules.extendedUntil(until + 999_999, t0, w));
        assertFalse(CalmRules.expired(until, until - 1));
        assertTrue(CalmRules.expired(until, until));
        w.CalmMinutes = 0;
        assertEquals(180_000, CalmRules.calmMillis(w), "a bad value falls back to three minutes");
    }

    @Test
    void restlessWarningOnceNearTheEnd() {
        long calm = 180_000, until = 10_000_000;
        assertFalse(CalmRules.warnRestless(Phase.CALM, until, until - 60_000, false, calm), "a minute left: quiet");
        assertTrue(CalmRules.warnRestless(Phase.CALM, until, until - 19_000, false, calm));
        assertFalse(CalmRules.warnRestless(Phase.CALM, until, until - 19_000, true, calm), "only once");
        assertFalse(CalmRules.warnRestless(Phase.LURED, until, until - 19_000, false, calm));
        assertFalse(CalmRules.warnRestless(Phase.CALM, until, until - 19_000, false, 30_000), "too short a calm to warn");
    }

    @Test
    void startleRules() {
        assertTrue(CalmRules.sprintStartles(Phase.CALM, true, 2.0));
        assertFalse(CalmRules.sprintStartles(Phase.CALM, true, 3.0), "sprinting past at a distance");
        assertFalse(CalmRules.sprintStartles(Phase.CALM, false, 0.5), "walking or crouching up never startles");
        assertTrue(CalmRules.sprintStartles(Phase.LURED, true, 1.0));
        assertFalse(CalmRules.sprintStartles(Phase.SPOOKED, true, 1.0), "already running");
        assertTrue(CalmRules.hitStartles(Phase.CALM, true));
        assertTrue(CalmRules.hitStartles(Phase.LURED, true));
        assertFalse(CalmRules.hitStartles(Phase.CALM, false), "a mob's hit is not a startle");
        assertFalse(CalmRules.hitStartles(Phase.SPOOKED, true));
    }

    @Test
    void phasesNeverAllowCombat() {
        for (Phase p : Phase.values()) {
            for (String s : new String[] {"Combat", "Alerted"}) assertFalse(CalmRules.stateAllowed(p, s), p + " allows " + s);
        }
        assertTrue(CalmRules.stateAllowed(Phase.CALM, "Idle"));
        assertFalse(CalmRules.stateAllowed(Phase.CALM, "Search"));
        assertFalse(CalmRules.stateAllowed(Phase.CALM, "Flee"), "calm does not flee from a crouching player");
        assertTrue(CalmRules.stateAllowed(Phase.LURED, "Search"), "the walk to the bowl");
        assertFalse(CalmRules.stateAllowed(Phase.LURED, "Idle"));
        assertTrue(CalmRules.stateAllowed(Phase.SPOOKED, "Flee"));
        assertFalse(CalmRules.stateAllowed(Phase.SPOOKED, "Search"));
    }

    @Test
    void eatDistanceAndBowlLooks() {
        assertTrue(CalmRules.closeEnoughToEat(2.0, 2.0));
        assertFalse(CalmRules.closeEnoughToEat(3.0, 1.0));
        assertSame(BowlLook.MEAT, CalmRules.bowlLook("Food_Wildmeat_Raw"));
        assertSame(BowlLook.MEAT, CalmRules.bowlLook("Food_Chicken_Raw"));
        assertSame(BowlLook.FISH, CalmRules.bowlLook("Food_Fish_Raw_Rare"));
        assertSame(BowlLook.BERRIES, CalmRules.bowlLook("Plant_Fruit_Berries_Red"));
        assertEquals("Meat", BowlLook.MEAT.state);
    }

    @Test
    void stalledWalkEatsInPlaceOnlyNearTheBowl() {
        assertTrue(CalmRules.closeEnoughToEatInPlace(5, 6), "7.8 blocks: the walks seen live stalled here");
        assertFalse(CalmRules.closeEnoughToEatInPlace(9, 9), "too far: it gives up instead");
        assertTrue(CalmRules.STALL_KICKS >= 2);
    }

    @Test
    void predatorPromptsOnlyWhileCalm() {
        assertEquals("", com.hexvane.cozytalesranching.taming.TamingRules.predatorHintKey(FeedGate.TOO_WILD, com.hexvane.cozytalesranching.taming.TamingRules.FoodKind.BASIC));
        assertEquals("cozyranch_predator.hint.offer", com.hexvane.cozytalesranching.taming.TamingRules.predatorHintKey(FeedGate.CALM_PREDATOR, com.hexvane.cozytalesranching.taming.TamingRules.FoodKind.FAVORITE));
        assertEquals("cozyranch_predator.hint.calm", com.hexvane.cozytalesranching.taming.TamingRules.predatorHintKey(FeedGate.CALM_PREDATOR, com.hexvane.cozytalesranching.taming.TamingRules.FoodKind.NONE));
        assertTrue(com.hexvane.cozytalesranching.taming.TamingRules.isOurWildHint("cozyranch_predator.hint.offer"));
        assertFalse(com.hexvane.cozytalesranching.taming.TamingRules.isOurWildHint("server.interactionHints.tame"));
    }

    @Test
    void bookPhases() {
        CalmBook book = new CalmBook();
        UUID w = UUID.randomUUID();
        assertTrue(book.isEmpty());
        assertTrue(book.canLure(w, 0));
        book.lure(w, "wolf", "default", 1, 64, 2, "Food_Wildmeat_Raw", 0);
        assertSame(Phase.LURED, book.phase(w));
        assertEquals(CalmRules.LURE_SECONDS * 1000, book.get(w).untilMs);
        assertFalse(book.canLure(w, 0), "already lured");
        assertTrue(book.bowlTaken("default", 1, 64, 2));
        assertFalse(book.bowlTaken("other", 1, 64, 2));
        assertFalse(book.extend(w, 999), "only a calm one is extended");

        book.calm(w, "wolf", "default", 180_000);
        assertSame(Phase.CALM, book.phase(w));
        assertEquals(1, book.get(w).bowlX, "keeps its bowl");
        assertFalse(book.bowlTaken("default", 1, 64, 2), "eaten, the bowl is free again");
        assertTrue(book.extend(w, 240_000));
        assertEquals(240_000, book.get(w).untilMs);
        assertTrue(book.extend(w, 200_000));
        assertEquals(240_000, book.get(w).untilMs, "never shortened");

        UUID player = UUID.randomUUID();
        book.spook(w, player, 1_000);
        assertSame(Phase.SPOOKED, book.phase(w));
        assertEquals(player, book.get(w).startler);
        assertEquals(1_000 + CalmRules.SPOOK_SECONDS * 1000, book.get(w).untilMs);
        assertEquals(1, book.inWorld("default").size());
        book.remove(w);
        assertNull(book.phase(w));
        assertTrue(book.isEmpty());
    }

    @Test
    void gaveUpCoolsDown() {
        CalmBook book = new CalmBook();
        UUID w = UUID.randomUUID();
        book.lure(w, "wolf", "default", 0, 0, 0, "x", 0);
        book.gaveUp(w, 10_000);
        assertNull(book.phase(w));
        assertFalse(book.canLure(w, 10_000 + CalmRules.GAVE_UP_COOLDOWN_SECONDS * 1000 - 1));
        assertTrue(book.canLure(w, 10_000 + CalmRules.GAVE_UP_COOLDOWN_SECONDS * 1000));
    }

    @Test
    void bowlStoreHoldsOneBaitAndSurvivesASave(@TempDir Path dir) {
        Path file = dir.resolve("BaitBowls.json");
        BowlStore a = new BowlStore(file);
        assertTrue(a.fill("default", 10, 64, -3, "Food_Wildmeat_Raw"));
        assertFalse(a.fill("default", 10, 64, -3, "Food_Fish_Raw"), "one bait at a time");
        assertTrue(a.fill("default", -10, 2, 3, "Food_Fish_Raw"));
        assertTrue(a.fill("nether", 10, 64, -3, "Plant_Fruit_Berries_Red"));
        a.saveNow();
        assertTrue(Files.isRegularFile(file));
        BowlStore b = new BowlStore(file);
        b.load();
        assertEquals(3, b.size());
        assertEquals("Food_Wildmeat_Raw", b.bait("default", 10, 64, -3));
        assertEquals("Food_Fish_Raw", b.bait("default", -10, 2, 3));
        assertEquals("Plant_Fruit_Berries_Red", b.bait("nether", 10, 64, -3));
        assertEquals("Food_Wildmeat_Raw", b.take("default", 10, 64, -3));
        assertNull(b.take("default", 10, 64, -3));
        assertNull(b.bait("default", 10, 65, -3));
        assertEquals(1, b.inWorld("default").size());
        a.close();
        b.close();
    }
}
