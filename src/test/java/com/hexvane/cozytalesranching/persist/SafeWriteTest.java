package com.hexvane.cozytalesranching.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeWriteTest {
    @TempDir Path dir;

    private static String read(Path p) throws Exception {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    void previousContentIsKeptAsPrev() throws Exception {
        Path f = dir.resolve("w.json");
        SafeWrite.write(f, "{\"a\":1}");
        assertFalse(Files.exists(SafeWrite.sibling(f, SafeWrite.PREV)), "nothing to keep the first time");
        SafeWrite.write(f, "{\"a\":2}");
        assertEquals("{\"a\":2}", read(f));
        assertEquals("{\"a\":1}", read(SafeWrite.sibling(f, SafeWrite.PREV)));
        assertFalse(Files.exists(SafeWrite.sibling(f, SafeWrite.TMP)));
        // .prev is its own file: something editing the main file in place cannot spoil it
        Files.writeString(f, "{ broken", StandardCharsets.UTF_8);
        assertEquals("{\"a\":1}", read(SafeWrite.sibling(f, SafeWrite.PREV)));
    }

    @Test
    void healFinishesASaveCutOffBetweenItsRenames() throws Exception {
        Path f = dir.resolve("w.json");
        Files.writeString(SafeWrite.sibling(f, SafeWrite.PREV), "{\"a\":1}");
        Files.writeString(SafeWrite.sibling(f, SafeWrite.TMP), "{\"a\":2}");
        SafeWrite.healDir(dir);
        assertEquals("{\"a\":2}", read(f));
        assertFalse(Files.exists(SafeWrite.sibling(f, SafeWrite.TMP)));
    }

    @Test
    void aBrokenTempFileFallsBackToPrev() throws Exception {
        Path f = dir.resolve("w.json");
        Files.writeString(SafeWrite.sibling(f, SafeWrite.PREV), "{\"a\":1}");
        Files.writeString(SafeWrite.sibling(f, SafeWrite.TMP), "{\"a\":");
        SafeWrite.heal(f);
        assertEquals("{\"a\":1}", read(f));
    }

    @Test
    void healLeavesAFileThatWasNotBeingWritten() throws Exception {
        Path f = dir.resolve("w.json");
        Files.writeString(SafeWrite.sibling(f, SafeWrite.PREV), "{\"a\":1}");
        SafeWrite.heal(f);
        assertFalse(Files.exists(f), "no temp file: maybe deleted on purpose, so not brought back here");
        Files.writeString(f, "{\"a\":3}");
        Files.writeString(SafeWrite.sibling(f, SafeWrite.TMP), "{\"a\":");
        SafeWrite.heal(f);
        assertEquals("{\"a\":3}", read(f), "an existing main file is never touched");
        assertTrue(Files.exists(SafeWrite.sibling(f, SafeWrite.TMP)));
    }
}
