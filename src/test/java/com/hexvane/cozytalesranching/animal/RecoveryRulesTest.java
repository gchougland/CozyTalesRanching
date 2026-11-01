package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.AnimalRecovery.Place;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The player report: animals with a home teleported to their owner whenever the owner went far enough for the home to pause. */
class RecoveryRulesTest {
    @Test
    void aKilledAnimalWaitsOutItsCooldownAndOnlyWhileMissing() {
        AnimalRecord r = new AnimalRecord();
        assertEquals(0, r.deathCooldownLeftMs(1_000), "never killed");
        r.state = AnimalRecord.State.MISSING;
        assertEquals(0, r.deathCooldownLeftMs(1_000), "removed by other means: no cooldown");
        r.deathCooldownUntilMs = 5_000;
        assertEquals(4_000, r.deathCooldownLeftMs(1_000));
        assertEquals(0, r.deathCooldownLeftMs(5_000), "over");
        r.state = AnimalRecord.State.ACTIVE;
        assertEquals(0, r.deathCooldownLeftMs(1_000), "already back");
    }

    @Test
    void aCooldownIsSavedAndEndsWithANewIncarnation(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        AnimalStore store = new AnimalStore(dir);
        AnimalRecord r = store.create("chicken", java.util.UUID.randomUUID(), "default", false);
        r.state = AnimalRecord.State.MISSING;
        r.deathCooldownUntilMs = 5_000;
        assertFalse(RecordFields.sameExceptPosition(new AnimalRecord(), r), "a saved field");
        assertEquals(5_000, RecordFields.copy(r).deathCooldownUntilMs);
        store.bind(r, java.util.UUID.randomUUID());
        assertEquals(0, r.deathCooldownUntilMs);
    }

    @Test
    void anOrphanCannotRevive_aRecordInItsCooldown(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        AnimalStore store = new AnimalStore(dir);
        AnimalRecord r = store.create("chicken", java.util.UUID.randomUUID(), "default", false);
        r.state = AnimalRecord.State.MISSING;
        r.deathCooldownUntilMs = System.currentTimeMillis() + 60_000;
        assertEquals(null, OrphanRelink.match(store, "default", "chicken", "CozyRanch_Chicken", false, 0, 0));
        r.deathCooldownUntilMs = 0;
        assertEquals(r, OrphanRelink.match(store, "default", "chicken", "CozyRanch_Chicken", false, 0, 0));
    }

    @Test
    void anAnimalInAPausedSectionIsNotGone() {
        assertFalse(AnimalRecovery.maySuspect(false, true, false), "loaded but paused: the game parked it");
        assertFalse(AnimalRecovery.maySuspect(false, false, false), "unloaded");
        assertTrue(AnimalRecovery.maySuspect(false, true, true), "ticking and not there");
    }

    @Test
    void aFollowerLeftInAPausedSectionIsLookedFor() {
        assertTrue(AnimalRecovery.maySuspect(true, true, false), "parked, it cannot catch up with its owner");
        assertFalse(AnimalRecovery.maySuspect(true, false, false), "unloaded: it waits");
    }

    @Test
    void aResidentOnlyEverComesBackAtItsHouse() {
        assertEquals(List.of(Place.HOUSE), AnimalRecovery.recoveryOrder(false, true));
    }

    @Test
    void anAnimalToldToStayComesBackWhereItStayed() {
        assertEquals(List.of(Place.LAST_SPOT, Place.OWNER), AnimalRecovery.recoveryOrder(false, false));
    }

    @Test
    void aFollowerComesBackToItsOwner() {
        assertEquals(List.of(Place.OWNER, Place.LAST_SPOT), AnimalRecovery.recoveryOrder(true, false));
        assertEquals(List.of(Place.OWNER, Place.HOUSE), AnimalRecovery.recoveryOrder(true, true));
    }
}
