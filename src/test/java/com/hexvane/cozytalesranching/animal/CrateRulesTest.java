package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A crate holds the real animal: only its owner may take it, and no crate can ever make a second one. */
class CrateRulesTest {
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void onlyItsOwnerOrAnAdminMayTakeAnOwnedAnimal() {
        assertTrue(CrateRules.mayCapture(OWNER, OWNER, false));
        assertFalse(CrateRules.mayCapture(UUID.randomUUID(), OWNER, false));
        assertTrue(CrateRules.mayCapture(UUID.randomUUID(), OWNER, true));
        assertTrue(CrateRules.mayCapture(UUID.randomUUID(), null, false), "nobody's animal");
    }

    @Test
    void aCrateOnlyHoldsTheAnimalAtTheGenerationItNames() {
        assertTrue(CrateRules.holds(AnimalRecord.State.CRATED, 5, 5));
        assertFalse(CrateRules.holds(AnimalRecord.State.CRATED, 6, 5), "taken again: this crate is an old copy");
        assertFalse(CrateRules.holds(AnimalRecord.State.ACTIVE, 5, 5), "already out");
    }

    @Test
    void goingIntoACrateEndsTheOldEntityAndComingOutEndsTheCrate(@TempDir Path dir) {
        AnimalStore store = new AnimalStore(dir);
        AnimalRecord r = store.create("chicken", OWNER, "default", false);
        UUID entity = UUID.randomUUID();
        store.bind(r, entity);
        r.following = true;
        long inCrate = store.crate(r);
        assertEquals(AnimalRecord.State.CRATED, r.state);
        assertNull(r.entityUuid);
        assertFalse(r.following);
        assertNull(store.byEntity(entity), "the old entity no longer names it");
        assertEquals(List.of(r), store.crated());
        store.bind(r, UUID.randomUUID());
        assertFalse(CrateRules.holds(r.state, r.generation, inCrate));
        assertEquals(List.of(), store.crated());
    }

    @Test
    void eachCrateHasItsOwnTooltipKey() {
        UUID a = UUID.fromString("11111111-2222-4333-8444-555555555555");
        assertEquals("cozyranch_11111111222243338444555555555555", CrateRules.roleKey(a));
        assertEquals("server.npcRoles.cozyranch_11111111222243338444555555555555.name", CrateRules.translationKey(a));
    }

    @Test
    void theTooltipLineFillsIn() {
        assertEquals(", Lucky, Hardy", CrateRules.traitList(List.of("Lucky", "Hardy")));
        assertEquals("", CrateRules.traitList(List.of()));
        assertEquals("Clover the Chicken", CrateRules.fill("{name} the {species}", Map.of("name", "Clover", "species", "Chicken")));
    }
}
