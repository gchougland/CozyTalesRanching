package com.hexvane.cozytalesranching.care;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.species.SpeciesOverlay;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every habitat feature against the vanilla blocks a player would put down for it. The ids are the placed block ids
 * (the item id, or a state id like {@code *X_State_Definitions_Stage2}, which the scan strips), read from the vanilla
 * item files of game 0.7.4. Fix round 6: a Dandelion and a Poppy must be flowers, a Hay Bale must be hay.
 */
class HabitatVanillaBlocksTest {
    private static HabitatConfig config;

    /** Feature id to blocks (by placed id) that must count for it, with the player's name for each. */
    private static final Map<String, List<String>> COUNTS = Map.ofEntries(
        Map.entry("flowers", List.of(
            "Plant_Flower_Common_Yellow2",   // Dandelion
            "Plant_Flower_Common_Red2",      // Poppy
            "Plant_Flower_Common_White", "Plant_Flower_Common_Blue2", "Plant_Flower_Bushy_Red", "Plant_Flower_Orchid_Pink",
            "Plant_Flower_Flax_Blue", "Plant_Flower_Tall_Yellow", "Plant_Flower_Tall_Cyan2", "Plant_Flower_Hemlock",
            "Plant_Flower_Water_White", "Plant_Sunflower_Block", "Plant_Lavender_Block",
            "*Plant_Lavender_Block_State_Definitions_StageFinal")),
        Map.entry("hay", List.of(
            "Ingredient_Hay",                // Hay Bale (the full block)
            "Plant_Hay_Bundle")),            // Bundle of Hay
        Map.entry("perch", List.of(
            "Ingredient_Hay", "Plant_Hay_Bundle", "CozyRanch_Toy_Log",
            "Wood_Softwood_Fence", "*Wood_Softwood_Fence_State_Definitions_Corner", "Wood_Hardwood_Fence_Gate",
            "Wood_Tropicalwood_Fence", "Build_White_Fence", "Metal_Iron_Fence")),
        Map.entry("lookout_post", List.of(
            "Wood_Lightwood_Fence", "*Wood_Darkwood_Fence_State_Definitions_T", "Wood_Redwood_Fence_Gate", "Build_Grey_Fence",
            "Plant_Cactus_1", "Plant_Cactus_3")),
        Map.entry("grass", List.of("Soil_Grass", "Soil_Grass_Full", "Soil_Grass_Sunny", "Plant_Grass_Lush_Short", "Plant_Grass_Sharp_Tall")),
        Map.entry("tall_grass", List.of("Plant_Grass_Lush_Tall", "Plant_Grass_Wet_Overgrown", "Plant_Grass_Sharp_Wild", "Plant_Fern_Tall")),
        Map.entry("reeds", List.of("Plant_Reeds_Marsh", "Plant_Reeds_Water", "Plant_Reeds_Winter")),
        Map.entry("lily_pads", List.of("Plant_Flower_Water_Blue", "Plant_Flower_Water_Duckweed")),
        Map.entry("dust", List.of("Soil_Dirt", "Soil_Dirt_Dry", "Soil_Dirt_Half", "Soil_Dirt_Tilled", "Soil_Pathway", "Soil_Sand",
            "Soil_Sand_White", "Soil_Sand_Red", "Soil_Gravel_Sand")),
        Map.entry("mud", List.of("Soil_Mud", "Soil_Mud_Dry")),
        Map.entry("vegetables", List.of("Plant_Crop_Carrot_Block", "*Plant_Crop_Carrot_Block_State_Definitions_Stage1",
            "*Plant_Crop_Wheat_Block_State_Definitions_StageFinal", "Plant_Crop_Potato_Block", "Plant_Crop_Pumpkin_Block",
            "Plant_Crop_Lettuce_Block_Eternal")),
        Map.entry("grain_patch", List.of("Plant_Crop_Wheat_Block", "*Plant_Crop_Wheat_Block_State_Definitions_Stage2",
            "Plant_Crop_Corn_Block", "Plant_Crop_Rice_Block")),
        Map.entry("carrot_patch", List.of("Plant_Crop_Carrot_Block", "*Plant_Crop_Carrot_Block_State_Definitions_Stage2",
            "Plant_Crop_Lettuce_Block")),
        Map.entry("leafy_greens", List.of("Plant_Crop_Lettuce_Block", "Plant_Crop_Cauliflower_Block", "Plant_Crop_Pumpkin_Block")),
        Map.entry("skrill_chilli_patch", List.of("Plant_Crop_Chilli_Block", "*Plant_Crop_Chilli_Block_State_Definitions_Stage3")),
        Map.entry("wild_berries", List.of("Plant_Crop_Berry_Block", "Plant_Crop_Berry_Winter_Block")),
        Map.entry("berry_brambles", List.of("Plant_Crop_Berry_Block", "Plant_Bush_Green", "Plant_Bush_Bramble")),
        Map.entry("woodland", List.of("Wood_Oak_Trunk", "Wood_Oak_Trunk_Full", "Wood_Birch_Trunk_Half", "Wood_Fig_Blue_Trunk",
            "Wood_Wisteria_Wild_Trunk")),
        Map.entry("tree_trunks", List.of("Wood_Oak_Trunk", "Wood_Maple_Trunk_Full", "Wood_Fig_Blue_Trunk")),
        Map.entry("forest_edge", List.of("Wood_Oak_Trunk", "Wood_Ash_Trunk_Full", "Plant_Leaves_Oak", "Plant_Leaves_Autumn")),
        Map.entry("snowy_firs", List.of("Wood_Fir_Trunk", "Wood_Fir_Trunk_Full", "Plant_Leaves_Fir_Snow", "Plant_Leaves_Snow")),
        Map.entry("browse", List.of("Plant_Leaves_Oak", "Plant_Bush_Lush")),
        Map.entry("parrot_jungle", List.of("Plant_Leaves_Jungle", "Plant_Leaves_Palm", "Wood_Jungle_Trunk", "Plant_Vine_Liana")),
        Map.entry("warm_light", List.of("Bench_Campfire", "Deco_Lantern", "Furniture_Crude_Torch", "Wood_Torch_Wall",
            "*Furniture_Crude_Torch_State_Definitions_Off", "Furniture_Village_Brazier", "Furniture_Tavern_Candle",
            "Furniture_Lumberjack_Lamp", "Furniture_Kweebec_Lantern_Ceiling")),
        Map.entry("nest_pebbles", List.of("Soil_Gravel", "Soil_Pebbles", "Soil_Pebbles_Frozen", "Rock_Stone_Cobble")),
        Map.entry("frozen_ice", List.of("Rock_Ice", "Rock_Ice_Permafrost")),
        Map.entry("snow_cover", List.of("Soil_Snow", "Soil_Snow_Half", "Plant_Grass_Snowy")),
        Map.entry("desert_sand", List.of("Soil_Sand", "Soil_Sand_Red", "Soil_Sand_White")),
        Map.entry("beach_sand", List.of("Soil_Sand", "Soil_Gravel_Sand_White")),
        Map.entry("red_earth", List.of("Soil_Sand_Red", "Soil_Clay_Red", "Soil_Dirt_Dry")),
        Map.entry("cacti", List.of("Plant_Cactus_1", "Plant_Cactus_Ball_1", "Plant_Cactus_Flat_2")),
        Map.entry("dry_scrub", List.of("Plant_Bush_Arid", "Plant_Bush_Dead", "Plant_Grass_Dry_Tall")),
        Map.entry("seaweed", List.of("Plant_Seaweed_Grass_Tall", "Plant_Seaweed_Winter_Blue")),
        Map.entry("shells", List.of("Deco_Coral_Shell", "Deco_Coral_Shell_Sanddollar")),
        Map.entry("marsh", List.of("Soil_Mud", "Soil_Grass_Wet", "Plant_Grass_Wet_Tall", "Plant_Reeds_Marsh", "Plant_Reeds_Water")),
        Map.entry("moss", List.of("Plant_Moss_Green", "Plant_Moss_Rug_Green", "Plant_Moss_Block_Green")),
        Map.entry("snail_moss", List.of("Plant_Moss_Short_Green", "Plant_Moss_Wall_Yellow")),
        Map.entry("mushrooms", List.of("Plant_Crop_Mushroom_Cap_Brown", "Plant_Crop_Mushroom_Common_Lime", "Plant_Crop_Mushroom_Glowing_Blue")),
        Map.entry("ferns", List.of("Plant_Fern", "Plant_Fern_Forest", "Plant_Fern_Tall")),
        Map.entry("rocky_ground", List.of("Rock_Stone", "Rock_Basalt", "Rock_Stone_Cobble")),
        Map.entry("bee_hive", List.of("Deco_Hive")),
        Map.entry("woodpecker_deadwood", List.of("Wood_Dry_Trunk", "Wood_Deadwood_Planks", "Wood_Deadwood_Fence", "CozyRanch_Toy_Log")));

    /** Feature id to blocks that must not count for it. */
    private static final Map<String, List<String>> NEVER = Map.ofEntries(
        Map.entry("flowers", List.of("Plant_Flower_Common_Poisoned", "Plant_Flower_Orchid_Poisoned", "Plant_Sunflower_Stage_0",
            "Plant_Lavender_Stage_0", "Plant_Petals_Yellow")),
        Map.entry("hay", List.of("Plant_Crop_Wheat_Block", "Plant_Crop_Wheat_Item")),
        Map.entry("dust", List.of("Soil_Dirt_Wet", "Soil_Dirt_Burnt", "Soil_Dirt_Poisoned", "Soil_Mud")),
        Map.entry("grass", List.of("Soil_Grass_Burnt", "Soil_Grass_Burnt_Full")),
        Map.entry("vegetables", List.of("Plant_Crop_Wheat_Stage_4_Burnt", "Plant_Crop_Mushroom_Cap_Red", "Plant_Crop_Wheat_Item")),
        Map.entry("woodland", List.of("Wood_Oak_Trunk_Stairs", "Wood_Softwood_Planks")),
        Map.entry("forest_edge", List.of("Wood_Burnt_Trunk", "Plant_Leaves_Burnt", "Plant_Leaves_Poisoned", "Wood_Oak_Trunk_Stairs")),
        Map.entry("lookout_post", List.of("Plant_Cactus_Flower", "Plant_Cactus_Ball_1", "Plant_Cactus_Flat_1")),
        Map.entry("reeds", List.of("Plant_Reeds_Lava", "Plant_Reeds_Poison")),
        Map.entry("warm_light", List.of("Deco_Campfire_Off")));

    @BeforeAll
    static void readRealConfig() throws IOException {
        try (InputStream in = HabitatVanillaBlocksTest.class.getResourceAsStream("/Server/CozyTalesRanching/Habitat.json")) {
            assertNotNull(in);
            config = HabitatConfig.read(SpeciesOverlay.merged(in, "Habitat"));
        }
    }

    private static boolean counts(String feature, String placedId) {
        HabitatConfig.Feature f = config.feature(feature);
        assertNotNull(f, "no feature " + feature);
        return f.matchesBlock(HabitatConfig.baseBlockId(placedId));
    }

    @Test
    void theBlocksAPlayerWouldPutDownCount() {
        List<String> wrong = new ArrayList<>();
        COUNTS.forEach((feature, ids) -> {
            for (String id : ids) if (!counts(feature, id)) wrong.add(feature + " misses " + id);
        });
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void lookalikesThatShouldNotCountDoNot() {
        List<String> wrong = new ArrayList<>();
        NEVER.forEach((feature, ids) -> {
            for (String id : ids) if (counts(feature, id)) wrong.add(feature + " wrongly counts " + id);
        });
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void everyPlainFeatureIsChecked() {
        Set<String> plain = new HashSet<>();
        config.Features.forEach((id, f) -> {
            if (f.Special.isEmpty() && f.Fluids.isEmpty() && !id.startsWith("toy_")) plain.add(id);
        });
        plain.removeAll(COUNTS.keySet());
        assertTrue(plain.isEmpty(), "features with no vanilla block check: " + plain);
    }

    @Test
    void dandelionsPoppiesAndHayBalesMeetTheChickenCoopsWants() {
        HabitatScanTest.Grid g = HabitatScanTest.Grid.flat(6);
        g.put(2, 64, 0, "Plant_Flower_Common_Yellow2", false);
        g.put(2, 64, 1, "Plant_Flower_Common_Red2", false);
        for (int z = -1; z <= 1; z++) g.put(-3, 64, z, "Ingredient_Hay", true);
        HabitatScan s = new HabitatScan(config, 0, 64, 0, 6);
        while (!s.done()) s.step(g, 50);
        assertEquals(2, s.count("flowers"));
        assertEquals(3, s.count("hay"));
        HabitatScan.Result r = s.evaluate(List.of("food"), List.of("hay", "grass", "flowers"), true);
        assertTrue(r.requirementsMet());
        assertFalse(r.missing().contains("flowers"), r.missing().toString());
        assertFalse(r.missing().contains("hay"), r.missing().toString());
    }

    @Test
    void moreThanSixtyFourPlainFeaturesAllCount() {
        HabitatConfig c = new HabitatConfig();
        for (int i = 0; i < 80; i++) {
            HabitatConfig.Feature f = new HabitatConfig.Feature();
            f.Blocks.add("Ingredient_Hay");
            c.Features.put("f" + i, f);
        }
        c.normalise();
        HabitatScanTest.Grid g = HabitatScanTest.Grid.flat(2);
        g.put(1, 64, 1, "Ingredient_Hay", true);
        HabitatScan s = new HabitatScan(c, 0, 64, 0, 2);
        while (!s.done()) s.step(g, 50);
        for (int i = 0; i < 80; i++) assertEquals(1, s.count("f" + i), "f" + i);
    }

    @Test
    void theScanAreaCoversTheHousesColumnsAndHeights() {
        assertTrue(HabitatScan.covers(0, 64, 0, 12, config, 12, 64, 0));
        assertFalse(HabitatScan.covers(0, 64, 0, 12, config, 9, 64, 9));
        assertTrue(HabitatScan.covers(0, 64, 0, 12, config, 0, 64 - config.ScanBelow, 0));
        assertFalse(HabitatScan.covers(0, 64, 0, 12, config, 0, 64 + config.ScanAbove + 1, 0));
    }

    /** With the game's assets on this machine, every id above is a real vanilla block item (or a state of one). */
    @Test
    void theNamedIdsAreRealVanillaBlocks() throws IOException {
        Path items = Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "Hytale-Modding", "HytaleSourceCode",
            "hytale-shared-source", "HytaleAssets", "Server", "Item", "Items");
        Assumptions.assumeTrue(Files.isDirectory(items), "vanilla assets not on this machine");
        Set<String> vanilla = new HashSet<>();
        try (Stream<Path> files = Files.walk(items)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> vanilla.add(p.getFileName().toString().replaceFirst("\\.json$", "")));
        }
        List<String> unknown = new ArrayList<>();
        for (Map<String, List<String>> m : List.of(COUNTS, NEVER)) {
            m.values().forEach(ids -> ids.forEach(id -> {
                String base = HabitatConfig.baseBlockId(id);
                if (!base.startsWith("CozyRanch_") && !vanilla.contains(base)) unknown.add(id);
            }));
        }
        assertTrue(unknown.isEmpty(), "not vanilla: " + unknown);

        List<String> dead = new ArrayList<>();
        config.Features.forEach((id, f) -> {
            for (String b : f.Blocks) if (!b.startsWith("CozyRanch_") && !vanilla.contains(b)) dead.add(id + " names " + b);
            for (String p : f.BlockPrefixes) {
                if (p.startsWith("CozyRanch_")) continue;
                if (vanilla.stream().noneMatch(v -> v.startsWith(p))) dead.add(id + " prefix " + p + " matches nothing");
            }
        });
        assertTrue(dead.isEmpty(), "habitat definitions name blocks the game does not have: " + String.join(", ", dead));
    }
}
