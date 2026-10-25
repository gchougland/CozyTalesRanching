package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** An animal is called by its look (the role's name over its head), so a raven in the Rookery is not a crow. */
class AnimalNamesTest {
    private static final Map<String, String> ROLES = Map.of(
        "CozyRanch_Crow", "server.npcRoles.Crow.name",
        "CozyRanch_Raven", "server.npcRoles.Raven.name",
        "CozyRanch_Raven_Fledgling", "cozyranch_species.crow.baby.raven.name");
    private static final Function<String, String> NAMES = ROLES::get;

    private static SpeciesDefinition crow() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "crow";
        s.NameKey = "cozyranch_species.crow.name";
        return s;
    }

    private static AnimalRecord record(String spawnRole) {
        AnimalRecord r = new AnimalRecord();
        r.speciesId = "crow";
        r.spawnRole = spawnRole;
        return r;
    }

    @Test
    void theRoleItLastHadNamesIt() {
        assertEquals("server.npcRoles.Raven.name", AnimalNames.key(record(null), crow(), "CozyRanch_Raven", NAMES));
    }

    @Test
    void aStoredRavenIsARavenToo() {
        assertEquals("server.npcRoles.Raven.name", AnimalNames.key(record("CozyRanch_Raven"), crow(), null, NAMES));
    }

    @Test
    void theLiveRoleWinsOverTheStoredOne() {
        // a fledgling that grew up: its entity is the adult raven now, the stored role is not yet updated
        assertEquals("server.npcRoles.Raven.name", AnimalNames.key(record("CozyRanch_Raven_Fledgling"), crow(), "CozyRanch_Raven", NAMES));
    }

    @Test
    void anUnknownRoleFallsBackToTheSpecies() {
        assertEquals("cozyranch_species.crow.name", AnimalNames.key(record("Modded_Crow"), crow(), "Modded_Crow", NAMES));
        assertEquals("cozyranch_species.crow.name", AnimalNames.key(record(null), crow(), null, NAMES));
    }

    @Test
    void noSpeciesAndNoRoleIsNull() {
        assertNull(AnimalNames.key(record(null), null, null, NAMES));
    }
}
