package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The vanilla coop playtest (fix3-coop): untagged copies relinked to MISSING records, and the recovery spot test. */
class CoopOrphanTest {
    @TempDir static Path data;
    @TempDir Path dir;

    @BeforeAll
    static void loadSpecies() throws Exception {
        Path species = Files.createDirectories(data.resolve("Species"));
        Files.writeString(species.resolve("Chicken.json"), """
            { "Id": "chicken", "WildRoles": ["Chicken"], "AdoptRoles": ["Tamed_Chicken"], "AdoptBabyRoles": ["Tamed_Chicken_Chick"],
              "RanchRole": "CozyRanch_Chicken", "RanchBabyRole": "CozyRanch_Chicken_Chick" }
            """, StandardCharsets.UTF_8);
        SpeciesRegistry.load(data);
    }

    private AnimalRecord missing(AnimalStore s, String role, double x, double z, UUID owner) {
        AnimalRecord r = s.create("chicken", owner, "Orbis", false);
        s.bind(r, UUID.randomUUID());
        r.state = AnimalRecord.State.MISSING;
        r.lastX = x;
        r.lastZ = z;
        s.setRole(r.animalId, role);
        s.touch(r);
        return r;
    }

    @Test
    void coopCopyIsRelinkedToTheNearestMissingRecordOfItsRole() {
        AnimalStore s = new AnimalStore(dir);
        UUID owner = UUID.randomUUID();
        AnimalRecord near = missing(s, "CozyRanch_Chicken", 103, 100, owner);
        missing(s, "CozyRanch_Chicken", 130, 100, owner);
        missing(s, "Tamed_Chicken", 100, 100, null);
        assertSame(near, OrphanRelink.match(s, "Orbis", "chicken", "CozyRanch_Chicken", false, 100, 100));
    }

    @Test
    void fiveCopiesTakeFiveDifferentRecords() {
        AnimalStore s = new AnimalStore(dir);
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < 5; i++) missing(s, "CozyRanch_Chicken", 100 + i, 100, owner);
        java.util.Set<UUID> taken = new java.util.HashSet<>();
        for (int i = 0; i < 5; i++) {
            AnimalRecord r = OrphanRelink.match(s, "Orbis", "chicken", "CozyRanch_Chicken", false, 102, 100);
            assertTrue(r != null && taken.add(r.animalId), "each copy gets its own record");
            s.bind(r, UUID.randomUUID()); // relinking makes it ACTIVE
        }
        assertNull(OrphanRelink.match(s, "Orbis", "chicken", "CozyRanch_Chicken", false, 102, 100), "a sixth copy is a stranger");
    }

    @Test
    void nothingFarAwayOrOfAnotherKindIsTaken() {
        AnimalStore s = new AnimalStore(dir);
        missing(s, "CozyRanch_Chicken", 100 + OrphanRelink.RADIUS + 1, 100, UUID.randomUUID());
        assertNull(OrphanRelink.match(s, "Orbis", "chicken", "CozyRanch_Chicken", false, 100, 100), "too far");
        assertNull(OrphanRelink.match(s, "Other", "chicken", "CozyRanch_Chicken", false, 100 + OrphanRelink.RADIUS + 1, 100), "other world");
        assertNull(OrphanRelink.match(s, "Orbis", "chicken", "CozyRanch_Chicken", true, 100 + OrphanRelink.RADIUS + 1, 100), "baby vs adult");
        assertNull(OrphanRelink.match(s, "Orbis", "cow", "CozyRanch_Chicken", false, 100 + OrphanRelink.RADIUS + 1, 100), "other species");
        AnimalRecord active = s.create("chicken", null, "Orbis", false);
        active.lastX = 100;
        active.lastZ = 100;
        s.setRole(active.animalId, "Tamed_Chicken");
        s.touch(active);
        assertNull(OrphanRelink.match(s, "Orbis", "chicken", "Tamed_Chicken", false, 100, 100), "only MISSING records are relinked");
    }

    @Test
    void roleMustMatchWhenKnown() {
        assertTrue(OrphanRelink.roleFits("CozyRanch_Chicken", "CozyRanch_Chicken", null));
        assertTrue(OrphanRelink.roleFits("CozyRanch_Chicken", null, "CozyRanch_Chicken"));
        assertTrue(OrphanRelink.roleFits("Tamed_Chicken", null, null), "no role known: any role of the species");
        assertFalse(OrphanRelink.roleFits("Tamed_Chicken", "CozyRanch_Chicken", null));
    }

    @Test
    void onlyUntaggedRanchAndAdoptRolesCanBeOrphans() {
        assertTrue(AnimalEntitySystem.mayBeOrphan(RoleKind.RANCH, false, "CozyRanch_Chicken"));
        assertTrue(AnimalEntitySystem.mayBeOrphan(RoleKind.ADOPT, false, "Tamed_Chicken"));
        assertTrue(AnimalEntitySystem.mayBeOrphan(RoleKind.RANCH_BABY, false, "CozyRanch_Chicken_Chick"));
        assertFalse(AnimalEntitySystem.mayBeOrphan(RoleKind.WILD, false, "Chicken"), "wild spawns never touch records");
        assertFalse(AnimalEntitySystem.mayBeOrphan(RoleKind.RANCH, true, "CozyRanch_Chicken"), "another mod's entity");
    }

    @Test
    void dryGroundIsFluidIdZeroNotNull() {
        // Fluid.EMPTY is id 0 and is what SpawningContext reports on dry ground; the old "groundFluid == null" never held.
        assertTrue(AnimalRecovery.isDryFooting(0, AnimalRecovery.NO_WATER_LEVEL));
        assertFalse(AnimalRecovery.isDryFooting(7, AnimalRecovery.NO_WATER_LEVEL), "fluid in the ground block");
        assertFalse(AnimalRecovery.isDryFooting(0, 64), "water in the span");
    }
}
