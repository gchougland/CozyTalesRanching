package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdoptionRulesTest {
    @TempDir static Path data;

    @BeforeAll
    static void loadSpecies() throws Exception {
        Path species = Files.createDirectories(data.resolve("Species"));
        // a server's override that lists another mod's familiar among the frog's adopt roles
        Files.writeString(species.resolve("Frog.json"), """
            { "Id": "frog", "WildRoles": ["Frog_Green"], "AdoptRoles": ["MD_Familiar_Frog", "Tamed_Frog"], "RanchRole": "CozyRanch_Frog" }
            """, StandardCharsets.UTF_8);
        SpeciesRegistry.load(data);
    }

    @Test
    void vanillaTamedRolesAreAdopted() {
        assertTrue(AnimalEntitySystem.shouldAdopt("Tamed_Cow", false, false));
        assertTrue(AnimalEntitySystem.shouldAdopt("Tamed_Cow_Calf", false, false));
        assertTrue(AnimalEntitySystem.shouldAdopt("Tamed_Frog", false, false));
    }

    @Test
    void ignoredRolesAreNeverAdopted() {
        assertFalse(AnimalEntitySystem.shouldAdopt("MD_Familiar_Frog", false, false), "IgnoredRolePrefixes wins over species data");
        assertFalse(CompatGuard.isManageableRole("MD_Familiar_Frog"));
        assertFalse(AnimalEntitySystem.shouldAdopt("Endgame_Pet_Cow", false, false));
    }

    @Test
    void entitiesWithAnotherModsComponentAreNeverAdopted() {
        assertFalse(AnimalEntitySystem.shouldAdopt("Tamed_Cow", false, true));
        Set<String> ignored = RanchConfig.get().ignoredComponentClasses();
        assertTrue(CompatGuard.isIgnoredClassName(ignored, "endgame.plugin.components.PetOwnerComponent"));
        assertTrue(CompatGuard.isIgnoredClassName(ignored, "com.danbagh.dungeonframework.features.mounts.SummonedMountComponent"));
        assertTrue(CompatGuard.isIgnoredClassName(Set.of("PetOwnerComponent"), "some.other.mod.PetOwnerComponent"), "simple names match any package");
        assertFalse(CompatGuard.isIgnoredClassName(ignored, "com.hypixel.hytale.server.npc.entities.NPCEntity"));
    }

    @Test
    void otherRolesAreLeftAlone() {
        assertFalse(AnimalEntitySystem.shouldAdopt("Cow", false, false), "wild animals are befriended, not adopted");
        assertFalse(AnimalEntitySystem.shouldAdopt("CozyRanch_Cow", false, false), "our own role is bound by taming");
        assertFalse(AnimalEntitySystem.shouldAdopt("Tamed_Horse", false, false), "not a supported species");
        assertFalse(AnimalEntitySystem.shouldAdopt("Tamed_Cow", true, false), "already a ranch animal");
    }
}
