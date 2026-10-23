package com.hexvane.cozytalesranching.wildlife;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisitorStoreTest {
    @TempDir
    Path dir;

    private static VisitorStore.Visitor visitor(String world) {
        VisitorStore.Visitor v = new VisitorStore.Visitor();
        v.entityUuid = UUID.randomUUID();
        v.houseId = UUID.randomUUID();
        v.ownerId = UUID.randomUUID();
        v.speciesId = "chicken";
        v.roleId = "CozyRanchWild_Chicken_Golden";
        v.rare = true;
        v.worldName = world;
        v.arrivedAtMs = 1000;
        v.leaveAtMs = 5000;
        v.x = 1.5;
        return v;
    }

    @Test
    void roundTrip() {
        VisitorStore a = new VisitorStore(dir);
        VisitorStore.Visitor v = visitor("default");
        a.add(v);
        a.house(v.houseId, "default").lastVisitBySpecies.put("chicken", 1234L);
        a.write(a.drainDirty());
        assertFalse(a.hasDirty());

        VisitorStore b = new VisitorStore(dir);
        b.load();
        assertTrue(b.isVisitor(v.entityUuid));
        VisitorStore.Visitor back = b.get(v.entityUuid).orElseThrow();
        assertEquals("CozyRanchWild_Chicken_Golden", back.roleId);
        assertTrue(back.rare);
        assertEquals(1.5, back.x);
        assertEquals(1234L, b.house(v.houseId, "default").lastVisitBySpecies.get("chicken"));
    }

    @Test
    void corruptFileFallsBackToPrevious() throws Exception {
        VisitorStore a = new VisitorStore(dir);
        VisitorStore.Visitor v = visitor("default");
        a.add(v);
        a.write(a.drainDirty());
        a.markDirty("default");
        a.write(a.drainDirty());
        Path file = a.fileFor("default");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);

        VisitorStore b = new VisitorStore(dir);
        b.load();
        assertTrue(b.isVisitor(v.entityUuid));
        try (var files = Files.list(dir.resolve("Visitors"))) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")));
        }
    }

    @Test
    void forgetAndRetainHouses() {
        VisitorStore s = new VisitorStore(dir);
        VisitorStore.Visitor v = visitor("w");
        s.add(v);
        s.forget(v.entityUuid);
        assertFalse(s.isVisitor(v.entityUuid));
        UUID keep = UUID.randomUUID(), gone = UUID.randomUUID(), other = UUID.randomUUID();
        s.house(keep, "w").nextVisitAtMs = 1;
        s.house(gone, "w").nextVisitAtMs = 2;
        s.house(other, "elsewhere").nextVisitAtMs = 3;
        s.retainHouses("w", Set.of(keep));
        assertEquals(1, s.house(keep, "w").nextVisitAtMs);
        assertEquals(0, s.house(gone, "w").nextVisitAtMs, "dropped, so it starts fresh");
        assertEquals(3, s.house(other, "elsewhere").nextVisitAtMs);
    }
}
