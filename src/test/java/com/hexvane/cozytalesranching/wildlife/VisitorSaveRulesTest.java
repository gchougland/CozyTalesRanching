package com.hexvane.cozytalesranching.wildlife;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fix 4 perf: a visitor walking about is not a save; it goes with the next one and at shutdown. */
class VisitorSaveRulesTest {
    @TempDir Path dir;

    @Test
    void movedOnlyWorldsWaitForARealSaveOrShutdown() {
        VisitorStore s = new VisitorStore(dir);
        VisitorStore.Visitor v = new VisitorStore.Visitor();
        v.entityUuid = UUID.randomUUID();
        v.worldName = "default";
        s.add(v);
        s.write(s.drainDirty());
        assertFalse(s.hasDirty(true));

        v.x = 12;
        s.markMoved("default");
        assertFalse(s.hasDirty(), "no save of its own");
        assertTrue(s.drainDirty().isEmpty());
        assertTrue(s.hasDirty(true), "kept for shutdown");
        var shutdown = s.drainDirty(true);
        assertEquals(1, shutdown.size());
        assertFalse(s.hasDirty(true));

        s.markMoved("default");
        s.markDirty("default");
        s.drainDirty();
        assertFalse(s.hasDirty(true), "a real save carries the positions too");
    }
}
