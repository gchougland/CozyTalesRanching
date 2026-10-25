package com.hexvane.cozytalesranching.wildlife;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.wildlife.WildlifeConfig.SpeciesWildlife;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.Candidate;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.HouseFacts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fix 5 note 17: a house only ever draws visitors of its own species (looks and rares alike), and every species still
 * has a way to get its first animal. Reads the bundled species list, so a new species needs no edit here.
 */
class OwnKindOnlyTest {
    private static final Path RES = Path.of("src/main/resources");
    private static final long NOW = 10_000_000_000L;
    private static final WildlifeConfig CONFIG = WildlifeConfig.bundled();
    private static final Map<String, SpeciesDefinition> SPECIES = species();

    private static Map<String, SpeciesDefinition> species() {
        Map<String, SpeciesDefinition> out = new LinkedHashMap<>();
        Gson gson = new Gson();
        for (String name : SpeciesRegistry.BUNDLED) {
            Path file = RES.resolve("Server/CozyTalesRanching/Species/" + name + ".json");
            if (!Files.isRegularFile(file)) continue;
            try {
                SpeciesDefinition s = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), SpeciesDefinition.class);
                out.put(s.Id, s);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }

    /** Everything a house of {@code houseSpecies} could wish for, plus everything any species loves. */
    private static HouseFacts lovelyHouse(String houseSpecies) {
        Set<String> all = new LinkedHashSet<>();
        for (SpeciesDefinition s : SPECIES.values()) {
            all.addAll(s.HabitatRequires);
            all.addAll(s.HabitatPrefers);
        }
        int loves = SPECIES.get(houseSpecies).HabitatPrefers.size();
        return new HouseFacts(UUID.randomUUID(), UUID.randomUUID(), houseSpecies, true, true, 1.0, all, 2, 1.0, 99,
            Map.of("CozyRanch_Truffle", 9, "CozyRanch_Seed_Pouch", 9), loves, loves);
    }

    @Test
    void noVisitorEverComesToAnotherSpeciesHouse() {
        assertTrue(SPECIES.size() > 30, "species list read");
        for (String houseSpecies : SPECIES.keySet()) {
            HouseFacts house = lovelyHouse(houseSpecies);
            for (Map.Entry<String, SpeciesWildlife> e : CONFIG.Species.entrySet()) {
                if (e.getKey().equals(houseSpecies)) continue;
                assertEquals(0, WildlifeRules.score(CONFIG, e.getKey(), e.getValue(), house, 0, NOW), e.getKey() + " at a " + houseSpecies + " house");
                assertFalse(WildlifeRules.rareConditionsMet(e.getKey(), e.getValue().Rare, house), e.getKey() + " rare at a " + houseSpecies + " house");
            }
            for (Candidate c : WildlifeRules.candidates(CONFIG, house, Map.of(), id -> true, NOW)) {
                assertEquals(houseSpecies, c.speciesId(), "only its own kind is drawn to a " + houseSpecies + " house");
            }
        }
    }

    @Test
    void frogsNeverVisitAMuddyWateryPigsty() {
        HouseFacts sty = new HouseFacts(UUID.randomUUID(), UUID.randomUUID(), "pig", true, true, 1.0,
            Set.of("food", "water", "mud", "vegetables"), 0, 0, 0, Map.of(), 3, 3);
        assertEquals(List.of("pig"), WildlifeRules.candidates(CONFIG, sty, Map.of(), id -> true, NOW).stream().map(Candidate::speciesId).toList());
        assertFalse(WildlifeRules.visitsHouse("frog", "pig"));
        assertTrue(WildlifeRules.visitsHouse("frog", "frog"));
    }

    @Test
    void everyNonPredatorIsDrawnByItsOwnEmptyHouse() {
        for (SpeciesDefinition s : SPECIES.values()) {
            if (s.Predator) continue;
            assertFalse(s.HouseBlock.isEmpty(), s.Id + " has a house");
            SpeciesWildlife sw = CONFIG.species(s.Id);
            assertTrue(sw != null && !sw.Roles.isEmpty(), s.Id + " has visitors");
            Set<String> own = new LinkedHashSet<>(s.HabitatRequires);
            own.addAll(s.HabitatPrefers);
            int loves = new LinkedHashSet<>(s.HabitatPrefers).size();
            HouseFacts empty = new HouseFacts(UUID.randomUUID(), UUID.randomUUID(), s.Id, true, true, 1.0, own, 0, 0, 0, Map.of(), loves, loves);
            List<Candidate> c = WildlifeRules.candidates(CONFIG, empty, Map.of(), id -> true, NOW);
            assertEquals(1, c.size(), s.Id + " empty house draws its own kind");
            assertTrue(c.getFirst().score() > 0.5, s.Id + " draw " + c.getFirst().score());
            if (sw.NearWater) assertTrue(s.HabitatRequires.contains("water"), s.Id + " spawns only near water, so its house needs water");
        }
    }

    @Test
    void predatorsComeFromTheWildWithBait() throws IOException {
        String spawns;
        try (Stream<Path> files = Files.walk(RES.resolve("Server/NPC/Spawn"))) {
            StringBuilder sb = new StringBuilder();
            for (Path p : files.filter(Files::isRegularFile).toList()) sb.append(Files.readString(p, StandardCharsets.UTF_8));
            spawns = sb.toString();
        }
        int predators = 0;
        for (SpeciesDefinition s : SPECIES.values()) {
            if (!s.Predator) continue;
            predators++;
            assertTrue(CONFIG.species(s.Id) == null, s.Id + " never comes as a visitor");
            assertFalse(s.BaitItems.isEmpty(), s.Id + " bait");
            assertFalse(s.WildRoles.isEmpty(), s.Id + " wild roles");
            // our own wild roles need our own world spawn; the others are vanilla creatures that spawn by themselves
            for (String r : s.WildRoles) if (r.startsWith("CozyRanchWild_")) assertTrue(spawns.contains("\"" + r + "\""), r + " spawns in the world");
        }
        assertTrue(predators >= 5);
    }
}
