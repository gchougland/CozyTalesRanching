package com.hexvane.cozytalesranching.species;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpeciesRoleVariantsTest {
    private static SpeciesDefinition frog() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "frog";
        s.WildRoles = List.of("Frog_Green", "Frog_Blue", "Frog_Orange");
        s.RanchRole = "CozyRanch_Frog";
        s.RanchRoleVariants = Map.of("Frog_Blue", "CozyRanch_Frog_Blue", "Frog_Orange", "CozyRanch_Frog_Orange");
        return s.normalise();
    }

    @Test
    void tamingKeepsTheColour() {
        SpeciesDefinition frog = frog();
        assertEquals("CozyRanch_Frog_Blue", frog.ranchRoleFor("Frog_Blue", false));
        assertEquals("CozyRanch_Frog_Orange", frog.ranchRoleFor("Frog_Orange", false));
        assertEquals("CozyRanch_Frog", frog.ranchRoleFor("Frog_Green", false));
        assertEquals("CozyRanch_Frog", frog.ranchRoleFor(null, false));
    }

    @Test
    void releaseGoesBackToTheSameColour() {
        SpeciesDefinition frog = frog();
        assertEquals("Frog_Blue", frog.wildRoleFor("CozyRanch_Frog_Blue", false));
        assertEquals("Frog_Orange", frog.wildRoleFor("CozyRanch_Frog_Orange", false));
        assertEquals("Frog_Green", frog.wildRoleFor("CozyRanch_Frog", false));
    }

    @Test
    void speciesWithoutVariantsIsUnchanged() {
        SpeciesDefinition cow = new SpeciesDefinition();
        cow.WildRoles = List.of("Cow");
        cow.WildBabyRoles = List.of("Cow_Calf");
        cow.RanchRole = "CozyRanch_Cow";
        cow.RanchBabyRole = "CozyRanch_Cow_Calf";
        cow.RanchRoleVariants = null;
        cow.normalise();
        assertEquals("CozyRanch_Cow", cow.ranchRoleFor("Cow", false));
        assertEquals("CozyRanch_Cow_Calf", cow.ranchRoleFor("Cow_Calf", true));
        assertEquals("Cow_Calf", cow.wildRoleFor("CozyRanch_Cow_Calf", true));
    }
}
