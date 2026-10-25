package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fix 4 perf: which changes start a save, and what the file looks like. */
class AnimalSaveRulesTest {
    @TempDir Path dir;

    private AnimalStore savedCow(AnimalRecord[] out) {
        AnimalStore s = new AnimalStore(dir);
        AnimalRecord cow = s.create("cow", UUID.randomUUID(), "default", false);
        cow.lastX = 1;
        cow.lastY = 64;
        cow.lastZ = 1;
        s.bind(cow, UUID.randomUUID());
        assertEquals(0, s.saveDirty());
        assertFalse(s.hasPending(true));
        out[0] = cow;
        return s;
    }

    private static AnimalRecord reload(Path dir, UUID id) {
        AnimalStore back = new AnimalStore(dir);
        back.loadAll();
        AnimalRecord r = back.get(id);
        assertNotNull(r);
        return r;
    }

    @Test
    void aPositionOnlyChangeDoesNotStartASave() throws Exception {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        String before = Files.readString(dir.resolve("default.json"));
        c[0].lastX = 40;
        c[0].lastZ = -12;
        s.touch(c[0]);
        assertFalse(s.hasPending(false), "wandering alone is not a reason to write");
        assertTrue(s.hasPending(true), "but it is kept for shutdown");
        assertEquals(0, s.saveDirty());
        assertEquals(before, Files.readString(dir.resolve("default.json")), "no write happened");
        assertEquals(1.0, reload(dir, c[0].animalId).lastX);
    }

    @Test
    void positionsRideWithTheNextRealSave() {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        c[0].lastX = 40;
        s.touch(c[0]);
        c[0].hearts = 2;
        s.touch(c[0]);
        assertTrue(s.hasPending(false));
        assertEquals(0, s.saveDirty());
        assertFalse(s.hasPending(true), "the position went with it");
        AnimalRecord back = reload(dir, c[0].animalId);
        assertEquals(40.0, back.lastX);
        assertEquals(2, back.hearts);
    }

    @Test
    void shutdownWritesPositions() {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        c[0].lastZ = 99;
        s.touch(c[0]);
        assertEquals(0, s.saveDirty(true));
        assertFalse(s.hasPending(true));
        assertEquals(99.0, reload(dir, c[0].animalId).lastZ);
    }

    @Test
    void softChangesAreWrittenWithinFiveMinutes() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(5_000_000);
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        s.setClock(now::get);
        c[0].lastX = 30;
        s.touch(c[0]);
        now.addAndGet(AnimalStore.SOFT_SAVE_MS - 1);
        c[0].lastX = 31;
        s.touch(c[0]);
        assertFalse(s.hasPending(false), "timed from the first unsaved move, not the last");
        now.addAndGet(1);
        assertTrue(s.hasPending(false));
        assertEquals(0, s.saveDirty());
        assertFalse(s.hasPending(true));
        assertEquals(31.0, reload(dir, c[0].animalId).lastX);
    }

    @Test
    void anUnloadingEntitySavesItsPosition() {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        c[0].lastX = 7;
        s.touch(c[0], true);
        assertTrue(s.hasPending(false));
    }

    @Test
    void aTouchWithNothingChangedStartsNoSave() {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        s.touch(c[0]);
        assertFalse(s.hasPending(true));
    }

    @Test
    void realChangesStillSave() {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        c[0].traits.add("bountiful");
        s.touch(c[0]);
        assertTrue(s.hasPending(false), "a list changed in place counts");
        s.saveDirty();
        c[0].houseId = UUID.randomUUID();
        s.touch(c[0]);
        assertTrue(s.hasPending(false));
        s.saveDirty();
        c[0].state = AnimalRecord.State.STORED;
        s.touch(c[0]);
        assertTrue(s.hasPending(false));
    }

    @Test
    void happinessDriftCountsOnceItAddsUp() {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        c[0].happiness -= 0.06f;
        s.touch(c[0]);
        assertFalse(s.hasPending(false), "a care pass's small drift waits");
        assertTrue(s.hasPending(true), "but shutdown writes it");
        c[0].happiness -= 0.05f;
        s.touch(c[0]);
        assertTrue(s.hasPending(false), "drift since the last save adds up to a real change");
    }

    @Test
    void filesAreCompactAndOldPrettyFilesStillLoad() throws Exception {
        AnimalRecord[] c = new AnimalRecord[1];
        savedCow(c);
        String text = Files.readString(dir.resolve("default.json"));
        assertFalse(text.contains("\n"), "no pretty printing");

        AnimalSaveFile.Data old = new AnimalSaveFile.Data();
        old.World = "old";
        AnimalRecord r = new AnimalRecord();
        r.animalId = UUID.randomUUID();
        r.speciesId = "pig";
        r.worldName = "old";
        r.name = "Wilbur";
        old.Records = List.of(r);
        Files.writeString(dir.resolve("old.json"), new GsonBuilder().setPrettyPrinting().create().toJson(old), StandardCharsets.UTF_8);
        assertEquals("Wilbur", reload(dir, r.animalId).name);
    }

    @Test
    void theSaverGetsCopiesNotTheLiveRecords() {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        c[0].traits.add("hardy");
        AnimalSaveFile.Data d = s.snapshotWorld("default");
        AnimalRecord copy = d.Records.get(0);
        assertNotSame(c[0], copy);
        assertNotSame(c[0].traits, copy.traits);
        assertEquals(c[0].traits, copy.traits);
        assertTrue(RecordFields.sameExceptPosition(c[0], copy));
        assertTrue(RecordFields.samePosition(c[0], copy));
        c[0].traits.add("sweet");
        assertEquals(1, copy.traits.size(), "later changes do not reach the copy being written");
    }

    @Test
    void previousCopyIsKeptAndACutOffSaveIsFinishedOnLoad() throws Exception {
        AnimalRecord[] c = new AnimalRecord[1];
        AnimalStore s = savedCow(c);
        c[0].hearts = 4;
        s.touch(c[0]);
        s.saveDirty();
        Path main = dir.resolve("default.json");
        Path prev = dir.resolve("default.json" + AnimalSaveFile.PREV);
        assertTrue(Files.isRegularFile(prev));
        // a crash between the two renames: main moved to .prev, the whole new file still in .tmp
        Path tmp = dir.resolve("default.json" + AnimalSaveFile.TMP);
        Files.move(main, tmp);
        assertEquals(4, reload(dir, c[0].animalId).hearts, "the forced temp file is the newest whole save");
        assertTrue(Files.isRegularFile(main));
    }
}
