package com.hexvane.cozytalesranching.care;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HabitatScanTest {
    private static HabitatConfig config;

    /** A fake world: blocks, solids and fluids by cell; cells in {@code unloaded} columns read as not in memory. */
    static final class Grid implements HabitatGrid {
        final Map<List<Integer>, String> blocks = new HashMap<>();
        final Set<List<Integer>> solid = new HashSet<>();
        final Map<List<Integer>, String> fluids = new HashMap<>();
        final Set<List<Integer>> unloaded = new HashSet<>();

        /** A flat world: grass at y=63 (solid) over dirt, air above. */
        static Grid flat(int radius) {
            Grid g = new Grid();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    g.put(x, 63, z, "Soil_Grass", true);
                    g.put(x, 62, z, "Soil_Dirt", true);
                    for (int y = 55; y < 62; y++) g.put(x, y, z, "Rock_Stone", true);
                }
            }
            return g;
        }

        void put(int x, int y, int z, @Nullable String block, boolean isSolid) {
            List<Integer> k = List.of(x, y, z);
            if (block == null) blocks.remove(k);
            else blocks.put(k, block);
            if (isSolid) solid.add(k);
            else solid.remove(k);
        }

        void water(int x, int y, int z) {
            put(x, y, z, null, false);
            fluids.put(List.of(x, y, z), "Water_Source");
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            return !unloaded.contains(List.of(x, z));
        }

        @Nullable
        @Override
        public String block(int x, int y, int z) {
            return blocks.get(List.of(x, y, z));
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return solid.contains(List.of(x, y, z));
        }

        @Nullable
        @Override
        public String fluid(int x, int y, int z) {
            return fluids.get(List.of(x, y, z));
        }
    }

    @BeforeAll
    static void readRealConfig() throws Exception {
        try (InputStream in = HabitatScanTest.class.getResourceAsStream("/Server/CozyTalesRanching/Habitat.json")) {
            assertNotNull(in, "Habitat.json is bundled");
            config = HabitatConfig.read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static HabitatScan scan(Grid g, int radius) {
        HabitatScan s = new HabitatScan(config, 0, 64, 0, radius);
        int steps = 0;
        while (!s.done()) {
            s.step(g, 7);
            steps++;
        }
        assertTrue(steps > 1, "the scan is spread over several steps");
        return s;
    }

    @Test
    void everyFeatureTheSpeciesFilesUseIsMapped() throws Exception {
        Gson gson = new Gson();
        for (String name : SpeciesRegistry.BUNDLED) {
            try (InputStream in = HabitatScanTest.class.getResourceAsStream("/Server/CozyTalesRanching/Species/" + name + ".json")) {
                SpeciesDefinition s = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SpeciesDefinition.class);
                for (String f : s.HabitatRequires) assertNotNull(config.feature(f), name + " requires unmapped " + f);
                for (String f : s.HabitatPrefers) assertNotNull(config.feature(f), name + " prefers unmapped " + f);
            }
        }
    }

    @Test
    void stateSuffixIsStripped() {
        assertEquals("Plant_Crop_Wheat_Block", HabitatConfig.baseBlockId("*Plant_Crop_Wheat_Block_State_Definitions_Stage3"));
        assertEquals("Soil_Mud", HabitatConfig.baseBlockId("Soil_Mud"));
    }

    @Test
    void frogPondMeetsRequirementsAndFindsReedsAndShade() {
        Grid g = Grid.flat(6);
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) g.water(x, 63, z);
        g.put(2, 64, 2, "Plant_Flower_Water_Blue", false);
        g.put(0, 64, 3, "Plant_Reeds_Marsh", false);
        g.put(4, 64, 3, "Plant_Reeds_Water", false);
        for (int x = -4; x <= -2; x++) for (int z = -4; z <= -2; z++) g.put(x, 69, z, "Plant_Leaves_Oak", true);
        HabitatScan s = scan(g, 6);
        assertEquals(9, s.count("water"));
        assertEquals(9, s.shadeColumns());

        HabitatScan.Result r = s.evaluate(List.of("water", "lily_pads"), List.of("reeds", "tall_grass", "shade"), false);
        assertTrue(r.requirementsMet());
        assertEquals(List.of("tall_grass"), r.missing());
        assertEquals(0.5f + 0.5f * 2f / 3f, r.score(), 1e-4f);
    }

    @Test
    void noLilyPadsMeansRequirementsNotMet() {
        Grid g = Grid.flat(4);
        for (int x = 1; x <= 2; x++) for (int z = 1; z <= 2; z++) g.water(x, 63, z);
        HabitatScan.Result r = scan(g, 4).evaluate(List.of("water", "lily_pads"), List.of(), false);
        assertFalse(r.requirementsMet());
        assertEquals(List.of("lily_pads"), r.missing());
        assertEquals(0.5f * 0.5f + 0.5f, r.score(), 1e-4f);
    }

    @Test
    void troughFoodIsTheFoodRequirement() {
        Grid g = Grid.flat(3);
        HabitatScan s = scan(g, 3);
        assertFalse(s.evaluate(List.of("food"), List.of(), false).requirementsMet());
        assertTrue(s.evaluate(List.of("food"), List.of(), true).requirementsMet());
    }

    @Test
    void openFlatPastureIsOpenSpaceAndGrass() {
        HabitatScan s = scan(Grid.flat(8), 8);
        assertEquals(s.columnsScanned(), s.openColumns());
        assertEquals(Boolean.TRUE, s.present("open_space", false));
        assertEquals(Boolean.TRUE, s.present("grass", false));
        assertEquals(Boolean.FALSE, s.present("shade", false));
    }

    @Test
    void aRoofedYardIsNotOpen() {
        Grid g = Grid.flat(6);
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) g.put(x, 67, z, "Wood_Oak_Planks", true);
        HabitatScan s = scan(g, 6);
        assertEquals(0, s.openColumns());
        assertEquals(s.columnsScanned(), s.shadeColumns(), "a roof shades the ground too");
        assertEquals(Boolean.FALSE, s.present("open_space", false));
    }

    @Test
    void buriedMudDoesNotCountButExposedMudDoes() {
        Grid g = Grid.flat(4);
        for (int z = -1; z <= 1; z++) g.put(0, 62, z, "Soil_Mud", true);
        assertEquals(0, scan(g, 4).count("mud"));
        for (int z = -1; z <= 1; z++) g.put(1, 63, z, "Soil_Mud", true);
        assertEquals(3, scan(g, 4).count("mud"));
    }

    @Test
    void cropStatesAndPoisonedFlowers() {
        Grid g = Grid.flat(4);
        for (int x = -2; x <= 1; x++) g.put(x, 64, 2, "Plant_Crop_Carrot_Block", false);
        g.put(2, 64, 2, "Plant_Crop_Mushroom_Block_Red", false);
        g.put(0, 64, -2, "Plant_Flower_Common_Poisoned", false);
        HabitatScan s = scan(g, 4);
        assertEquals(4, s.count("vegetables"));
        assertEquals(0, s.count("flowers"));
    }

    @Test
    void columnsNotInMemoryAreSkippedNotLoaded() {
        Grid g = Grid.flat(3);
        g.put(1, 64, 0, "Plant_Hay_Bundle", false);
        g.unloaded.add(List.of(1, 0));
        HabitatScan s = scan(g, 3);
        assertEquals(1, s.columnsSkipped());
        assertEquals(0, s.count("hay"), "the hay in the unloaded column is not seen");
        assertEquals(s.totalColumns() - 1, s.columnsScanned());
    }

    @Test
    void unknownFeaturesAreIgnored() {
        HabitatScan s = scan(Grid.flat(3), 3);
        assertNull(s.present("rainbows", false));
        HabitatScan.Result r = s.evaluate(List.of("rainbows"), List.of("rainbows"), false);
        assertTrue(r.requirementsMet());
        assertTrue(r.missing().isEmpty());
        assertEquals(1f, r.score(), 1e-4f);
    }
}
