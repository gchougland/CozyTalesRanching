package com.hexvane.cozytalesranching.produce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fix 4 perf: progress moving every pass is saved at most every five minutes; items and shutdown save at once. */
class ProduceSaveRulesTest {
    @TempDir Path dir;

    @Test
    void progressAloneWaitsFiveMinutes() {
        AtomicLong now = new AtomicLong(1_000_000);
        ProduceStore s = new ProduceStore(dir, () -> 1);
        s.setClock(now::get);
        UUID coop = UUID.randomUUID(), hen = UUID.randomUUID();
        s.track("default", coop, 0, 64, 0);
        assertEquals(0, s.saveDirty());
        s.setLastPass("default", now.get());
        s.setProgress("default", hen, "Food_Egg", 0.25);
        assertFalse(s.hasDirty(), "a pass's progress is not a save of its own");
        assertEquals(0, s.saveDirty());
        assertFalse(Files.exists(dir.resolve("default.json.prev")), "nothing was written");
        now.addAndGet(ProduceStore.PROGRESS_SAVE_MS - 1);
        s.setProgress("default", hen, "Food_Egg", 0.5);
        assertFalse(s.hasDirty());
        now.addAndGet(1);
        assertTrue(s.hasDirty(), "five minutes after the first unsaved progress");
        assertEquals(0, s.saveDirty());
        assertFalse(s.hasDirty());
        ProduceStore back = new ProduceStore(dir, () -> 1);
        back.loadAll();
        assertEquals(0.5, back.progress("default", hen, "Food_Egg"), 1e-9);
    }

    @Test
    void anItemSavesAtOnceWithItsProgress() {
        ProduceStore s = new ProduceStore(dir, () -> 1);
        UUID coop = UUID.randomUUID(), hen = UUID.randomUUID();
        s.track("default", coop, 0, 64, 0);
        s.saveDirty();
        s.setProgress("default", hen, "Food_Egg", 0.1);
        s.add(coop, "Food_Egg", 1);
        assertTrue(s.hasDirty());
        s.saveDirty();
        ProduceStore back = new ProduceStore(dir, () -> 1);
        back.loadAll();
        assertEquals(1, back.total(coop));
        assertEquals(0.1, back.progress("default", hen, "Food_Egg"), 1e-9);
    }

    @Test
    void shutdownWritesProgressThatIsNotDueYet() {
        ProduceStore s = new ProduceStore(dir, () -> 1);
        UUID hen = UUID.randomUUID();
        s.setProgress("default", hen, "Food_Egg", 0.75);
        assertFalse(s.hasDirty());
        s.close();
        ProduceStore back = new ProduceStore(dir, () -> 1);
        back.loadAll();
        assertEquals(0.75, back.progress("default", hen, "Food_Egg"), 1e-9);
    }
}
