package com.hexvane.cozytalesranching.species;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Wild and adopted babies of a look keep it (variants agent's 0.5.0 request). */
class VariantBabyRolesTest {
    private static SpeciesDefinition pig() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "pig";
        s.WildRoles = List.of("Pig", "Boar", "Pig_Wild");
        s.WildBabyRoles = List.of("Pig_Piglet", "Boar_Piglet", "Pig_Wild_Piglet");
        s.AdoptRoles = List.of("Tamed_Pig", "Tamed_Boar", "Tamed_Pig_Wild");
        s.AdoptBabyRoles = List.of("Tamed_Pig_Piglet", "Tamed_Boar_Piglet", "Tamed_Pig_Wild_Piglet");
        s.RanchRole = "CozyRanch_Pig";
        s.RanchBabyRole = "CozyRanch_Pig_Piglet";
        s.RanchRoleVariants = Map.of(
            "Boar", "CozyRanch_Pig_Boar", "Tamed_Boar", "CozyRanch_Pig_Boar",
            "Boar_Piglet", "CozyRanch_Pig_Boar", "Tamed_Boar_Piglet", "CozyRanch_Pig_Boar",
            "Pig_Wild", "CozyRanch_Pig_Wild", "Pig_Wild_Piglet", "CozyRanch_Pig_Wild", "Tamed_Pig_Wild_Piglet", "CozyRanch_Pig_Wild");
        s.RanchBabyRoleVariants = Map.of("CozyRanch_Pig_Boar", "CozyRanch_Pig_Boar_Piglet", "CozyRanch_Pig_Wild", "CozyRanch_Pig_Wild_Piglet");
        return s.normalise();
    }

    @Test
    void aWildOrAdoptedVariantBabyKeepsItsLook() {
        SpeciesDefinition pig = pig();
        assertEquals("CozyRanch_Pig_Boar_Piglet", pig.ranchRoleFor("Boar_Piglet", true));
        assertEquals("CozyRanch_Pig_Boar_Piglet", pig.ranchRoleFor("Tamed_Boar_Piglet", true));
        assertEquals("CozyRanch_Pig_Wild_Piglet", pig.ranchRoleFor("Tamed_Pig_Wild_Piglet", true));
        assertEquals("CozyRanch_Pig_Wild_Piglet", pig.ranchRoleFor("CozyRanch_Pig_Wild_Piglet", true), "already a ranch baby look");
        assertEquals("CozyRanch_Pig_Piglet", pig.ranchRoleFor("Pig_Piglet", true));
        assertEquals("CozyRanch_Pig_Piglet", pig.ranchRoleFor(null, true));
        assertEquals("CozyRanch_Pig_Boar", pig.ranchRoleFor("Boar", false));
        assertEquals("CozyRanch_Pig", pig.ranchRoleFor("Pig", false));
    }

    @Test
    void aVariantBabyGoesBackToItsWildBaby() {
        SpeciesDefinition pig = pig();
        assertEquals("Boar_Piglet", pig.wildRoleFor("CozyRanch_Pig_Boar_Piglet", true));
        assertEquals("Pig_Wild_Piglet", pig.wildRoleFor("CozyRanch_Pig_Wild_Piglet", true));
        assertEquals("Pig_Piglet", pig.wildRoleFor("CozyRanch_Pig_Piglet", true));
        assertEquals("Boar", pig.wildRoleFor("CozyRanch_Pig_Boar", false));
    }

    @Test
    void withoutWildBabiesABabyLookGoesBackAsItsWildAdult() {
        SpeciesDefinition snail = new SpeciesDefinition();
        snail.WildRoles = List.of("Snail_Frost", "Snail_Magma");
        snail.RanchRole = "CozyRanch_Snail_Frost";
        snail.RanchBabyRole = "CozyRanch_Snail_Frost_Baby";
        snail.RanchRoleVariants = Map.of("Snail_Frost", "CozyRanch_Snail_Frost", "Snail_Magma", "CozyRanch_Snail_Magma");
        snail.RanchBabyRoleVariants = Map.of("CozyRanch_Snail_Frost", "CozyRanch_Snail_Frost_Baby", "CozyRanch_Snail_Magma", "CozyRanch_Snail_Magma_Baby");
        snail.normalise();
        assertEquals("CozyRanch_Snail_Magma_Baby", snail.ranchRoleFor("Snail_Magma", true));
        assertEquals("Snail_Magma", snail.wildRoleFor("CozyRanch_Snail_Magma_Baby", true));
        assertEquals("Snail_Frost", snail.wildRoleFor("CozyRanch_Snail_Frost_Baby", true));
    }

    @Test
    void speciesWithoutBabyVariantsAreUnchanged() {
        SpeciesDefinition frog = new SpeciesDefinition();
        frog.WildRoles = List.of("Frog_Green", "Frog_Blue");
        frog.RanchRole = "CozyRanch_Frog";
        frog.RanchRoleVariants = Map.of("Frog_Blue", "CozyRanch_Frog_Blue");
        frog.normalise();
        assertEquals("CozyRanch_Frog_Blue", frog.ranchRoleFor("Frog_Blue", true), "no baby role: the colour itself");
        assertEquals("CozyRanch_Frog", frog.ranchRoleFor("Frog_Green", true));
        SpeciesDefinition cow = new SpeciesDefinition();
        cow.WildRoles = List.of("Cow");
        cow.WildBabyRoles = List.of("Cow_Calf");
        cow.RanchRole = "CozyRanch_Cow";
        cow.RanchBabyRole = "CozyRanch_Cow_Calf";
        cow.normalise();
        assertEquals("CozyRanch_Cow_Calf", cow.ranchRoleFor("Cow_Calf", true));
        assertEquals("Cow_Calf", cow.wildRoleFor("CozyRanch_Cow_Calf", true));
    }
}
