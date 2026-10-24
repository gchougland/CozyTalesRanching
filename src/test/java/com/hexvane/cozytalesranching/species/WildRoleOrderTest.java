package com.hexvane.cozytalesranching.species;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A look with a walking and a flying wild role is released as the one listed first (birds-b's 0.6.0 request). */
class WildRoleOrderTest {
    private static SpeciesDefinition songbird() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "songbird";
        s.WildRoles = List.of("CozyRanchWild_Songbird_Sparrow", "CozyRanchWild_Songbird_Bluebird", "Sparrow", "Bluebird");
        s.RanchRole = "CozyRanch_Songbird_Sparrow";
        s.RanchBabyRole = "CozyRanch_Songbird_Sparrow_Chick";
        s.RanchRoleVariants = Map.of(
            "Bluebird", "CozyRanch_Songbird_Bluebird", "Sparrow", "CozyRanch_Songbird_Sparrow",
            "CozyRanchWild_Songbird_Bluebird", "CozyRanch_Songbird_Bluebird",
            "CozyRanchWild_Songbird_Sparrow", "CozyRanch_Songbird_Sparrow");
        s.RanchBabyRoleVariants = Map.of("CozyRanch_Songbird_Bluebird", "CozyRanch_Songbird_Bluebird_Chick");
        return s.normalise();
    }

    @Test
    void aLookGoesBackAsItsFirstListedWildRole() {
        SpeciesDefinition bird = songbird();
        for (int i = 0; i < 20; i++) {
            assertEquals("CozyRanchWild_Songbird_Bluebird", bird.wildRoleFor("CozyRanch_Songbird_Bluebird", false));
            assertEquals("CozyRanchWild_Songbird_Sparrow", bird.wildRoleFor("CozyRanch_Songbird_Sparrow", false));
            assertEquals("CozyRanchWild_Songbird_Bluebird", bird.wildRoleFor("CozyRanch_Songbird_Bluebird_Chick", true));
        }
    }
}
