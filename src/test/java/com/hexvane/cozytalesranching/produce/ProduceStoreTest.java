package com.hexvane.cozytalesranching.produce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProduceStoreTest {
    @TempDir
    Path dir;

    @Test
    void productsProgressAndClockSurviveARestart() {
        UUID coop = UUID.randomUUID();
        UUID hen = UUID.randomUUID();
        ProduceStore a = new ProduceStore(dir, () -> 1);
        a.setLastPass("default", 12345L);
        a.track("default", coop, 10.5, 64, -2.5);
        a.add(coop, "Food_Egg", 3);
        a.add(coop, "Ingredient_Feathers_Light", 1);
        a.add(coop, "Food_Egg", 2);
        a.setProgress("default", hen, "Food_Egg", 0.75);
        assertEquals(0, a.saveDirty());
        assertFalse(a.hasDirty());
        assertTrue(Files.isRegularFile(dir.resolve("default.json")));

        ProduceStore b = new ProduceStore(dir, () -> 1);
        b.loadAll();
        assertEquals(12345L, b.lastPass("default"));
        assertEquals(Map.of("Food_Egg", 5, "Ingredient_Feathers_Light", 1), b.stored(coop));
        assertEquals(List.of("Food_Egg", "Ingredient_Feathers_Light"), List.copyOf(b.stored(coop).keySet()));
        assertEquals(6, b.total(coop));
        assertEquals(0.75, b.progress("default", hen, "Food_Egg"), 1e-9);
        assertEquals(0, b.lastPass("other world"));
    }

    @Test
    void takeLeavesTheRest() {
        UUID barn = UUID.randomUUID();
        ProduceStore s = new ProduceStore(dir, () -> 1);
        s.track("default", barn, 0, 0, 0);
        s.add(barn, "*Container_Bucket_State_Filled_Milk", 3);
        assertEquals(2, s.take(barn, "*Container_Bucket_State_Filled_Milk", 2));
        assertEquals(1, s.total(barn));
        assertEquals(1, s.take(barn, "*Container_Bucket_State_Filled_Milk", 5));
        assertEquals(Map.of(), s.stored(barn));
        assertEquals(0, s.take(barn, "Food_Egg", 1));
    }

    @Test
    void aGoneHouseKeepsItsProductsAsAPendingDropAcrossARestart() {
        UUID coop = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        ProduceStore a = new ProduceStore(dir, () -> 1);
        a.track("default", coop, 1, 2, 3);
        a.track("default", empty, 4, 5, 6);
        a.add(coop, "Food_Egg", 4);
        assertNull(a.retire(empty));
        ProduceStore.Stock drop = a.retire(coop);
        assertNotNull(drop);
        assertEquals(Map.of(), a.stored(coop));
        assertEquals(List.of(), a.houses("default"));
        a.saveDirty();

        ProduceStore b = new ProduceStore(dir, () -> 1);
        b.loadAll();
        List<ProduceStore.Stock> pending = b.pendingDrops("default");
        assertEquals(1, pending.size());
        assertEquals(4, pending.getFirst().total());
        assertEquals(2.0, pending.getFirst().Y, 1e-9);
        b.dropped(pending.getFirst());
        assertEquals(List.of(), b.pendingDrops("default"));
    }

    @Test
    void progressOfAnimalsThatLeftIsForgotten() {
        UUID stays = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        ProduceStore s = new ProduceStore(dir, () -> 1);
        s.setProgress("default", stays, "Food_Egg", 0.5);
        s.setProgress("default", left, "Food_Egg", 0.9);
        s.keepProgress("default", stays::equals);
        assertEquals(0.5, s.progress("default", stays, "Food_Egg"), 1e-9);
        assertEquals(0, s.progress("default", left, "Food_Egg"), 1e-9);
    }

    @Test
    void aCorruptFileFallsBackToThePreviousCopy() throws Exception {
        UUID coop = UUID.randomUUID();
        ProduceStore a = new ProduceStore(dir, () -> 1);
        a.track("default", coop, 0, 0, 0);
        a.add(coop, "Food_Egg", 1);
        a.saveDirty();
        a.add(coop, "Food_Egg", 1);
        a.saveDirty();
        Files.writeString(dir.resolve("default.json"), "{ not json", StandardCharsets.UTF_8);
        ProduceStore b = new ProduceStore(dir, () -> 1);
        b.loadAll();
        assertEquals(1, b.total(coop));
    }

    @Test
    void bundledTuningAndSpeciesProductsParse() throws Exception {
        try (InputStream in = ProduceTuning.class.getResourceAsStream(ProduceTuning.RESOURCE)) {
            assertNotNull(in);
            ProduceTuning t = ProduceTuning.read(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(30, t.PassSeconds, 1e-9);
            assertEquals(64, t.HouseCap);
            assertEquals(480, t.CatchUpCapMinutes, 1e-9);
            assertEquals("Container_Bucket", t.BucketItem);
        }
        Map<String, String> firstProduct = Map.of(
            "Chicken", "Food_Egg",
            "Cow", "*Container_Bucket_State_Filled_Milk",
            "Sheep", "Ingredient_Fabric_Scrap_Wool",
            "Pig", "CozyRanch_Truffle",
            "Frog", "Plant_Flower_Water_Green");
        Gson gson = new Gson();
        for (Map.Entry<String, String> e : firstProduct.entrySet()) {
            try (InputStream in = ProduceTuning.class.getResourceAsStream("/Server/CozyTalesRanching/Species/" + e.getKey() + ".json")) {
                assertNotNull(in, e.getKey());
                SpeciesDefinition s = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SpeciesDefinition.class);
                assertFalse(s.Products.isEmpty(), e.getKey());
                assertEquals(e.getValue(), s.Products.getFirst().Item, e.getKey());
                for (SpeciesDefinition.Product p : s.Products) {
                    assertTrue(p.IntervalMinutes > 0, e.getKey());
                    assertTrue(ProduceRules.mode(p.Collect) != ProduceRules.Mode.NONE, e.getKey() + " " + p.Item);
                }
                assertEquals(e.getKey().equals("Cow") ? ProduceRules.Mode.BUCKET : ProduceRules.Mode.HOUSE, ProduceRules.mode(s.Products.getFirst().Collect));
            }
        }
    }
}
