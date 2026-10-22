package com.hexvane.cozytalesranching.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentHouseRegistryTest {
    @TempDir Path dir;

    /** A debounce of an hour: nothing is written unless the test asks. */
    private PersistentHouseRegistry registry() {
        PersistentHouseRegistry r = new PersistentHouseRegistry(dir, () -> 3600);
        r.load();
        return r;
    }

    @Test
    void saveAndLoadRoundTrip() {
        PersistentHouseRegistry a = registry();
        UUID owner = UUID.randomUUID();
        HouseRecord h = a.create(owner, "cow", "CozyRanch_House_CowBarn", "default", 10, 64, -5, 2);
        h.doorX = 10.5;
        h.doorY = 64;
        h.doorZ = -7.6;
        h.capacity = 6;
        h.name = "Moo \"Manor\"";
        h.lastMorningDay = 20_123;
        h.breedingEnabled = true;
        a.setNightfallDay(h, 20_122);
        a.create(owner, "pig", "CozyRanch_House_Pigsty", "other world", 1, 2, 3, 0);
        assertEquals(0, a.saveDirty());
        a.close();

        assertTrue(Files.isRegularFile(dir.resolve("default.json")));
        assertTrue(Files.isRegularFile(dir.resolve("other_world.json")));

        PersistentHouseRegistry b = registry();
        HouseRecord back = b.at("default", 10, 64, -5).orElseThrow();
        assertEquals(h.houseId, back.houseId);
        assertEquals(owner, back.ownerId);
        assertEquals("cow", back.speciesId);
        assertEquals("CozyRanch_House_CowBarn", back.blockId);
        assertEquals(2, back.rotation);
        assertEquals(-7.6, back.doorZ, 1e-9);
        assertEquals(6, back.capacity);
        assertEquals("Moo \"Manor\"", back.name);
        assertEquals(20_123, back.lastMorningDay);
        assertTrue(back.breedingEnabled);
        assertEquals(20_122, b.nightfallDay(h.houseId));
        assertEquals(2, b.ownedBy(owner).size());
        assertEquals(1, b.inWorld("other world").size());
        b.close();
    }

    @Test
    void removeIsSavedAndClearsTheIndexes() {
        PersistentHouseRegistry a = registry();
        HouseRecord h = a.create(UUID.randomUUID(), "cow", "CozyRanch_House_CowBarn", "default", 0, 0, 0, 0);
        a.setNightfallDay(h, 5);
        a.saveDirty();
        a.remove(h.houseId);
        assertTrue(a.get(h.houseId).isEmpty());
        assertTrue(a.at("default", 0, 0, 0).isEmpty());
        assertTrue(a.inWorld("default").isEmpty());
        assertEquals(Long.MIN_VALUE, a.nightfallDay(h.houseId));
        a.close();

        PersistentHouseRegistry b = registry();
        assertEquals(0, b.size());
        b.close();
    }

    @Test
    void unreadableFileFallsBackToThePreviousCopyAndIsKept() throws Exception {
        PersistentHouseRegistry a = registry();
        HouseRecord first = a.create(UUID.randomUUID(), "cow", "CozyRanch_House_CowBarn", "default", 0, 0, 0, 0);
        a.saveDirty();
        a.create(UUID.randomUUID(), "pig", "CozyRanch_House_Pigsty", "default", 9, 0, 9, 1);
        a.saveDirty();
        a.close();
        assertTrue(Files.isRegularFile(dir.resolve("default.json.prev")));

        Files.writeString(dir.resolve("default.json"), "{ not json", StandardCharsets.UTF_8);
        PersistentHouseRegistry b = registry();
        assertEquals(1, b.size(), "the previous copy has the first house only");
        assertTrue(b.get(first.houseId).isPresent());
        b.close();
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("default.json.corrupt-")));
        }
    }

    @Test
    void closeWritesPendingChanges() {
        PersistentHouseRegistry a = registry();
        HouseRecord h = a.create(UUID.randomUUID(), "cow", "CozyRanch_House_CowBarn", "default", 0, 0, 0, 0);
        h.lastMorningDay = 77;
        a.markDirty(h);
        a.close();
        PersistentHouseRegistry b = registry();
        assertEquals(77, b.get(h.houseId).orElseThrow().lastMorningDay);
        assertFalse(Files.exists(dir.resolve("default.json.tmp")));
        b.close();
    }
}
