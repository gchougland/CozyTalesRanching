package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnimalStoreTest {
    @TempDir Path dir;

    private static AnimalStore loaded(Path dir) {
        AnimalStore s = new AnimalStore(dir);
        s.loadAll();
        return s;
    }

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        AnimalStore s = new AnimalStore(dir);
        UUID owner = UUID.randomUUID();
        UUID house = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        AnimalRecord cow = s.create("cow", owner, "default", false);
        cow.name = "Daisy \"the\" Cow";
        cow.hearts = 3;
        cow.heartProgress = 0.25f;
        cow.happiness = 0.9f;
        cow.houseId = house;
        cow.traits.add("bountiful");
        cow.personality = "gentle";
        cow.lastX = 10.5;
        cow.lastY = 64;
        cow.lastZ = -3.25;
        s.bind(cow, entity);
        s.setRole(cow.animalId, "Tamed_Cow");
        s.touch(cow);
        AnimalRecord chick = s.create("chicken", null, "Other World", true);
        chick.adopted = true;
        chick.state = AnimalRecord.State.MISSING;
        s.touch(chick);
        AnimalRecord gone = s.create("pig", owner, "default", false);
        s.remove(gone.animalId);
        assertEquals(0, s.saveDirty());

        assertTrue(Files.isRegularFile(dir.resolve("default.json")));
        assertTrue(Files.isRegularFile(dir.resolve("Other_World.json")));

        AnimalStore back = loaded(dir);
        assertEquals(2, back.size());
        AnimalRecord c = back.get(cow.animalId);
        assertNotNull(c);
        assertEquals("Daisy \"the\" Cow", c.name);
        assertEquals(owner, c.ownerId);
        assertEquals(house, c.houseId);
        assertEquals(3, c.hearts);
        assertEquals(0.25f, c.heartProgress);
        assertEquals(0.9f, c.happiness);
        assertEquals(List.of("bountiful"), c.traits);
        assertEquals("gentle", c.personality);
        assertEquals(1, c.generation);
        assertEquals(entity, c.entityUuid);
        assertEquals(-3.25, c.lastZ);
        assertEquals("Tamed_Cow", back.role(cow.animalId));
        assertSame(c, back.byEntity(entity));
        assertEquals(List.of(c), back.ownedBy(owner));
        assertEquals(List.of(c), back.inHouse(house));

        AnimalRecord h = back.get(chick.animalId);
        assertNotNull(h);
        assertTrue(h.adopted && h.baby);
        assertNull(h.ownerId);
        assertEquals(AnimalRecord.State.MISSING, h.state);
        assertEquals("Other World", h.worldName);

        assertNull(back.get(gone.animalId));
        assertTrue(back.isTombstoned(gone.animalId));
        assertFalse(back.hasDirty(), "a clean load must not rewrite files");
    }

    @Test
    void ownerChangeMovesIndex() {
        AnimalStore s = new AnimalStore(dir);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        AnimalRecord r = s.create("cow", a, "default", false);
        r.ownerId = b;
        s.touch(r);
        assertTrue(s.ownedBy(a).isEmpty());
        assertEquals(List.of(r), s.ownedBy(b));
        r.worldName = "nether";
        s.touch(r);
        assertTrue(s.inWorld("default").isEmpty());
        assertEquals(List.of(r), s.inWorld("nether"));
    }

    @Test
    void interruptedWriteLeavesOldFileReadable() throws Exception {
        AnimalStore s = new AnimalStore(dir);
        AnimalRecord r = s.create("cow", UUID.randomUUID(), "default", false);
        s.saveDirty();
        // the server died half way through the next write: a partial temp file is all that changed
        Path tmp = dir.resolve("default.json" + AnimalSaveFile.TMP);
        Files.writeString(tmp, "{\"FormatVersion\":1,\"Records\":[{\"animalId\":\"", StandardCharsets.UTF_8);

        AnimalStore back = loaded(dir);
        assertNotNull(back.get(r.animalId));
        assertFalse(Files.exists(tmp), "the leftover temp file is discarded");

        // the next save works over it
        back.create("pig", null, "default", false);
        assertEquals(0, back.saveDirty());
        assertEquals(2, loaded(dir).size());
    }

    @Test
    void corruptMainFileFallsBackToPreviousAndIsKept() throws Exception {
        AnimalStore s = new AnimalStore(dir);
        AnimalRecord first = s.create("cow", null, "default", false);
        s.saveDirty();
        s.create("pig", null, "default", false);
        s.saveDirty();
        Path main = dir.resolve("default.json");
        Files.writeString(main, "{ not json", StandardCharsets.UTF_8);

        AnimalStore back = loaded(dir);
        assertNotNull(back.get(first.animalId), "previous good copy used");
        assertTrue(back.loadProblems().contains("default"));
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("default.json.corrupt-")), "unreadable file renamed aside, not deleted");
        }
    }

    @Test
    void staleGenerationsAreDetectedAndNeverChangeTheRecord() {
        AnimalStore s = new AnimalStore(dir);
        AnimalRecord r = s.create("cow", UUID.randomUUID(), "default", false);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(1, s.bind(r, first));
        assertEquals(AnimalStore.Verdict.CURRENT, s.judge(r.animalId, 1, first));

        assertEquals(2, s.bind(r, second));
        assertEquals(AnimalStore.Verdict.STALE, s.judge(r.animalId, 1, first), "older incarnation");
        assertEquals(AnimalStore.Verdict.STALE, s.judge(r.animalId, 2, UUID.randomUUID()), "copy with the right generation but another UUID");
        assertEquals(2, r.generation);
        assertEquals(second, r.entityUuid);
        assertSame(r, s.byEntity(second));
        assertNull(s.byEntity(first));
        assertTrue(s.isCurrent(r.animalId, second, 2));
        assertFalse(s.isCurrent(r.animalId, first, 1));

        // the record's save was lost: the entity knows a newer generation, so the record catches up
        UUID third = UUID.randomUUID();
        assertEquals(AnimalStore.Verdict.CURRENT_CAUGHT_UP, s.judge(r.animalId, 3, third));
        assertEquals(3, r.generation);
        assertEquals(third, r.entityUuid);
        assertEquals(AnimalStore.Verdict.STALE, s.judge(r.animalId, 2, second));

        s.remove(r.animalId);
        assertEquals(AnimalStore.Verdict.STALE, s.judge(r.animalId, 3, third), "removed animals stay removed");
        assertEquals(AnimalStore.Verdict.UNKNOWN, s.judge(UUID.randomUUID(), 1, UUID.randomUUID()), "never saved: rebuild, never delete");
    }

    @Test
    void adoptionMakesOneRecordPerEntityEvenIfSeenTwice() {
        AnimalStore s = new AnimalStore(dir);
        UUID entity = UUID.randomUUID();
        AnimalStore.AdoptResult a = s.adoptOrGet(entity, "cow", "default", false);
        AnimalStore.AdoptResult b = s.adoptOrGet(entity, "cow", "default", false);
        assertTrue(a.created());
        assertFalse(b.created());
        assertSame(a.record(), b.record());
        assertEquals(1, s.size());
        assertTrue(a.record().adopted);
        assertNull(a.record().ownerId);

        s.bind(a.record(), entity);
        assertFalse(s.adoptOrGet(entity, "cow", "default", false).created(), "still one record after binding");
        assertTrue(s.adoptOrGet(UUID.randomUUID(), "cow", "default", false).created());
        assertEquals(2, s.size());
    }

    @Test
    void recreatedRecordKeepsTheEntitysIdentity() {
        AnimalStore s = new AnimalStore(dir);
        UUID id = UUID.randomUUID(), entity = UUID.randomUUID();
        AnimalRecord r = s.recreate(id, 4, entity, "pig", "default", false);
        assertEquals(id, r.animalId);
        assertEquals(4, r.generation);
        assertTrue(r.adopted);
        assertEquals(AnimalStore.Verdict.CURRENT, s.judge(id, 4, entity));
    }
}
