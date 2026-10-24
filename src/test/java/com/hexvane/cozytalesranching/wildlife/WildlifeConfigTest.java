package com.hexvane.cozytalesranching.wildlife;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.cozytalesranching.wildlife.WildlifeConfig.SpeciesWildlife;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WildlifeConfigTest {
    private static final Path RES = Path.of("src/main/resources");
    /** Every bundled species that has a file: id to file name. Read from the list, so a new species needs no edit here. */
    private static final Map<String, String> SPECIES_FILES = speciesFiles();

    private static Map<String, String> speciesFiles() {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String name : com.hexvane.cozytalesranching.species.SpeciesRegistry.BUNDLED) {
            Path file = RES.resolve("Server/CozyTalesRanching/Species/" + name + ".json");
            if (!Files.isRegularFile(file)) continue;
            try {
                JsonObject species = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                // predators are never wildlife visitors (contract 0.4.0): they have no wildlife entry
                if (species.has("Predator") && species.get("Predator").getAsBoolean()) continue;
                out.put(species.get("Id").getAsString(), name + ".json");
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return out;
    }

    /** A rare variant is optional (the duck has none). */
    private static boolean hasRare(SpeciesWildlife sw) {
        return sw.Rare != null && sw.Rare.Role != null && !sw.Rare.Role.isEmpty();
    }

    @Test
    void bundledCoversEverySpecies() {
        WildlifeConfig c = WildlifeConfig.bundled();
        assertTrue(c.Species.keySet().containsAll(SPECIES_FILES.keySet()));
        for (SpeciesWildlife sw : c.Species.values()) assertFalse(sw.Roles.isEmpty());
        for (String id : SPECIES_FILES.keySet()) {
            SpeciesWildlife sw = c.species(id);
            if (hasRare(sw)) assertFalse(sw.Rare.Role.isEmpty());
        }
        assertTrue(c.species("frog").NearWater);
    }

    @Test
    void rolesMatchTheSpeciesFilesAndExist() throws Exception {
        WildlifeConfig c = WildlifeConfig.bundled();
        for (Map.Entry<String, String> e : SPECIES_FILES.entrySet()) {
            JsonObject species = JsonParser.parseString(Files.readString(RES.resolve("Server/CozyTalesRanching/Species/" + e.getValue()), StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> wild = species.getAsJsonArray("WildRoles").asList().stream().map(j -> j.getAsString()).toList();
            SpeciesWildlife sw = c.species(e.getKey());
            for (String r : sw.Roles) assertTrue(wild.contains(r), e.getKey() + " visitor role " + r + " is a wild role of the species");
            if (!hasRare(sw)) continue;
            assertTrue(wild.contains(sw.Rare.Role), "rare wild role is tameable as " + e.getKey());
            String ranch = species.getAsJsonObject("RanchRoleVariants").get(sw.Rare.Role).getAsString();
            assertTrue(Files.isRegularFile(RES.resolve("Server/NPC/Roles/CozyRanchWild/" + sw.Rare.Role + ".json")), sw.Rare.Role);
            assertTrue(Files.isRegularFile(RES.resolve("Server/NPC/Roles/CozyRanch/Rare/" + ranch + ".json")), ranch);
        }
    }

    @Test
    void everySpeciesHasHints() throws Exception {
        String lang = Files.readString(RES.resolve("Server/Languages/en-US/cozyranch_wildlife.lang"), StandardCharsets.UTF_8);
        for (String id : SPECIES_FILES.keySet()) {
            assertTrue(lang.contains("\nhint." + id + "=") || lang.startsWith("hint." + id + "="), id);
            if (hasRare(WildlifeConfig.bundled().species(id))) assertTrue(lang.contains("\nhint." + id + ".rare="), id + " rare");
        }
    }

    @Test
    void missingPartsGetDefaultsAndRangesAreSane() {
        WildlifeConfig c = WildlifeConfig.parse(new StringReader(
            "{\"MinStayMinutes\":9,\"MaxStayMinutes\":2,\"SpawnMinDistance\":30,\"SpawnMaxDistance\":10,\"Species\":{\"x\":{\"Rare\":{}}}}"));
        assertEquals(9, c.MaxStayMinutes);
        assertEquals(30, c.SpawnMaxDistance);
        assertTrue(c.species("x").Roles.isEmpty());
        assertTrue(c.species("x").Rare.Features.isEmpty());
        assertTrue(c.species("x").Rare.StoredItems.isEmpty());
    }
}
