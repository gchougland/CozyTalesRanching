package com.hexvane.cozytalesranching.care;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.breeding.BreedingRules;
import com.hexvane.cozytalesranching.breeding.BreedingRules.Outcome;
import com.hexvane.cozytalesranching.breeding.TraitBook;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Toys as habitat preferences, and the rule that no house breeds before its first habitat scan. */
class ToyAndFirstScanTest {
    private static final long NOW = 1_000_000_000_000L;
    private static HabitatConfig config;
    private static final Map<String, SpeciesDefinition> SPECIES = new HashMap<>();

    @BeforeAll
    static void readRealData() throws Exception {
        try (InputStream in = ToyAndFirstScanTest.class.getResourceAsStream("/Server/CozyTalesRanching/Habitat.json")) {
            assertNotNull(in);
            config = HabitatConfig.read(com.hexvane.cozytalesranching.species.SpeciesOverlay.merged(in, "Habitat"));
        }
        Gson gson = new Gson();
        for (String name : SpeciesRegistry.BUNDLED) {
            try (InputStream in = ToyAndFirstScanTest.class.getResourceAsStream("/Server/CozyTalesRanching/Species/" + name + ".json")) {
                if (in == null) continue; // a species listed before its file is written
                SpeciesDefinition s = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SpeciesDefinition.class);
                SPECIES.put(s.Id, s);
            }
        }
    }

    private static HabitatScan scan(HabitatScanTest.Grid g, int radius) {
        HabitatScan s = new HabitatScan(config, 0, 64, 0, radius);
        while (!s.done()) s.step(g, 50);
        return s;
    }

    @Test
    void everySpeciesLikesAToyAndEveryToyIsMapped() {
        assertEquals(List.of("toy_ball"), config.SpeciesToys.get("pig"));
        assertEquals(List.of("toy_post"), config.SpeciesToys.get("cow"));
        assertEquals(List.of("toy_post"), config.SpeciesToys.get("sheep"));
        assertEquals(List.of("toy_bell"), config.SpeciesToys.get("chicken"));
        assertEquals(List.of("toy_duck", "toy_log"), config.SpeciesToys.get("frog"));
        for (SpeciesDefinition s : SPECIES.values()) {
            for (String toy : config.SpeciesToys.getOrDefault(s.Id, List.of())) {
                HabitatConfig.Feature f = config.feature(toy);
                assertNotNull(f, toy + " is a habitat feature");
                assertTrue(f.Blocks.stream().allMatch(b -> b.startsWith("CozyRanch_Toy_")), toy + " counts a toy block");
            }
        }
    }

    @Test
    void toysJoinTheSpeciesPreferences() {
        SpeciesDefinition pig = SPECIES.get("pig");
        List<String> prefers = config.prefers("pig", pig.HabitatPrefers);
        assertEquals(pig.HabitatPrefers.size() + 1, prefers.size());
        assertEquals("toy_ball", prefers.getLast());
        assertEquals(pig.HabitatPrefers, config.prefers("nobody", pig.HabitatPrefers), "a species with no toys keeps its own list");
    }

    @Test
    void aBallRaisesAPigsHabitatScoreAndSoItsHappiness() {
        SpeciesDefinition pig = SPECIES.get("pig");
        HabitatScanTest.Grid g = HabitatScanTest.Grid.flat(6);
        HouseRecord without = house("pig");
        scan(g, 6).applyTo(without, pig, true, NOW);
        assertTrue(without.habitatMissing.contains("toy_ball"));

        g.put(2, 64, 2, "CozyRanch_Toy_Ball", true);
        HabitatScan s = scan(g, 6);
        assertEquals(1, s.count("toy_ball"));
        assertEquals(0, s.count("toy_post"));
        HouseRecord with = house("pig");
        s.applyTo(with, pig, true, NOW);
        assertFalse(with.habitatMissing.contains("toy_ball"));
        assertTrue(with.habitatScore > without.habitatScore);
        int prefs = config.prefers("pig", pig.HabitatPrefers).size();
        assertEquals(0.5f / prefs, with.habitatScore - without.habitatScore, 1e-4f, "one preference more");

        float before = CareRules.target(new CareRules.Inputs(true, com.hexvane.cozytalesranching.taming.TamingRules.FoodKind.BASIC, false, true,
            without.habitatRequirementsMet, without.habitatScore, false)).target();
        float after = CareRules.target(new CareRules.Inputs(true, com.hexvane.cozytalesranching.taming.TamingRules.FoodKind.BASIC, false, true,
            with.habitatRequirementsMet, with.habitatScore, false)).target();
        assertTrue(after > before, "the toy raises the happiness target");
    }

    @Test
    void aToyAnotherSpeciesLikesDoesNothingForThisOne() {
        SpeciesDefinition pig = SPECIES.get("pig");
        HabitatScanTest.Grid g = HabitatScanTest.Grid.flat(6);
        HouseRecord plain = house("pig");
        scan(g, 6).applyTo(plain, pig, true, NOW);
        g.put(2, 64, 2, "CozyRanch_Toy_HangingBell", true);
        HouseRecord bell = house("pig");
        scan(g, 6).applyTo(bell, pig, true, NOW);
        assertEquals(plain.habitatScore, bell.habitatScore, 1e-6f);
    }

    // ---- no unscanned house breeds ----

    private static HouseRecord house(String species) {
        HouseRecord h = new HouseRecord();
        h.houseId = UUID.randomUUID();
        h.ownerId = UUID.randomUUID();
        h.speciesId = species;
        h.capacity = 6;
        h.x = 0;
        h.y = 64;
        h.z = 0;
        return h;
    }

    private static AnimalRecord adult(HouseRecord h) {
        AnimalRecord a = new AnimalRecord();
        a.animalId = UUID.randomUUID();
        a.speciesId = h.speciesId;
        a.ownerId = h.ownerId;
        a.houseId = h.houseId;
        a.state = AnimalRecord.State.STORED;
        a.hearts = 5;
        a.happiness = 1f;
        return a;
    }

    private static BreedingRules.Plan plan(HouseRecord h, SpeciesDefinition species) {
        return BreedingRules.plan(h, List.of(adult(h), adult(h)), species, new TraitBook().normalise(),
            new BreedingRules.Settings(true, 3, 0.7), true, NOW, new SplittableRandom(1));
    }

    @Test
    void aNeverScannedHouseIsScannedBeforeBreedingAndAPoorHabitatThenBlocksIt() {
        SpeciesDefinition frog = SPECIES.get("frog");
        HouseRecord h = house("frog");
        assertEquals(Outcome.BRED, plan(h, frog).outcome(), "breeding alone lets an unscanned house through");

        HabitatScanTest.Grid dry = HabitatScanTest.Grid.flat(6);
        if (h.habitatScannedAtMs == 0) scan(dry, 6).applyTo(h, frog, true, NOW);
        assertTrue(h.habitatScannedAtMs > 0);
        assertFalse(h.habitatRequirementsMet, "a frog house with no water");
        BreedingRules.Plan p = plan(h, frog);
        assertEquals(Outcome.HABITAT, p.outcome());
        assertFalse(p.habitatUnscanned());
    }

    @Test
    void aFirstScanThatSawNothingStillBlocksBreeding() {
        SpeciesDefinition cow = SPECIES.get("cow");
        HouseRecord h = house("cow");
        HabitatScanTest.Grid unloaded = HabitatScanTest.Grid.flat(3);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) unloaded.unloaded.add(List.of(x, z));
        HabitatScan s = scan(unloaded, 3);
        assertEquals(0, s.columnsScanned());
        s.applyTo(h, cow, true, NOW);
        assertEquals(NOW, h.habitatScannedAtMs);
        assertEquals(Outcome.HABITAT, plan(h, cow).outcome());
    }

    @Test
    void aGoodFirstScanLetsTheHouseBreed() {
        SpeciesDefinition cow = SPECIES.get("cow");
        HouseRecord h = house("cow");
        HabitatScan s = scan(HabitatScanTest.Grid.flat(8), 8);
        s.applyTo(h, cow, true, NOW);
        assertTrue(h.habitatRequirementsMet, "grass and trough food: " + h.habitatMissing);
        assertEquals(Outcome.BRED, plan(h, cow).outcome());
    }
}
