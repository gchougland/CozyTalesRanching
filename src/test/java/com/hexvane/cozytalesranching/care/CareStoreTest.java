package com.hexvane.cozytalesranching.care;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CareStoreTest {
    private static final ToIntFunction<String> STACK_25 = item -> 25;

    @TempDir
    Path dir;

    @Test
    void troughsAndMealsSurviveARestart() {
        UUID cow = UUID.randomUUID();
        CareStore a = new CareStore(dir);
        assertEquals(30, a.add("default", 10, 64, -3, "Wheat", 30, STACK_25));
        a.add("default", 10, 64, -3, "Pumpkin", 2, STACK_25);
        a.add("Nether Realm", 1, 2, 3, "Corn", 5, STACK_25);
        a.recordMeal(cow, "default", FoodKind.FAVORITE, 1234L);
        a.recordMeal(cow, "default", FoodKind.BASIC, 5678L);
        assertTrue(a.hasDirty());
        assertEquals(0, a.saveDirty());
        assertFalse(a.hasDirty());
        assertTrue(Files.isRegularFile(dir.resolve("Nether_Realm.json")));

        CareStore b = new CareStore(dir);
        b.loadAll();
        assertEquals(2, b.troughCount());
        TroughContents t = b.trough("default", 10, 64, -3).orElseThrow().contents();
        assertEquals(30, t.count("Wheat"));
        assertEquals(2, t.count("Pumpkin"));
        assertEquals(5, b.trough("Nether Realm", 1, 2, 3).orElseThrow().contents().count("Corn"));
        CareStore.MealView meal = b.meal(cow).orElseThrow();
        assertEquals(FoodKind.BASIC, meal.kind());
        assertEquals(5678L, meal.atMs());
        assertEquals(1234L, meal.treatAtMs(), "the last treat is kept after a plain meal");
    }

    @Test
    void emptiedTroughIsForgottenOnceItsBlockShowsIt() {
        CareStore s = new CareStore(dir);
        s.add("w", 0, 0, 0, "Wheat", 1, STACK_25);
        assertTrue(s.takeOne("w", 0, 0, 0, "Wheat"));
        assertTrue(s.trough("w", 0, 0, 0).orElseThrow().stateDirty());
        assertEquals(0, s.snapshot("w", 0).Troughs.size(), "empty troughs are not saved");
        s.stateShown("w", 0, 0, 0);
        assertTrue(s.trough("w", 0, 0, 0).isEmpty());
    }

    @Test
    void removeReturnsWhatTheTroughHeld() {
        CareStore s = new CareStore(dir);
        s.add("w", 0, 0, 0, "Wheat", 40, STACK_25);
        List<TroughContents.Slot> drops = s.remove("w", 0, 0, 0);
        assertEquals(40, drops.stream().mapToInt(x -> x.count).sum());
        assertTrue(s.trough("w", 0, 0, 0).isEmpty());
        assertTrue(s.remove("w", 0, 0, 0).isEmpty(), "a second break drops nothing");
    }

    @Test
    void corruptFileFallsBackToThePreviousSave() throws Exception {
        CareStore a = new CareStore(dir);
        a.add("w", 1, 1, 1, "Wheat", 7, STACK_25);
        a.saveDirty();
        a.add("w", 1, 1, 1, "Wheat", 1, STACK_25);
        a.saveDirty();
        Path file = dir.resolve("w.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);

        CareStore b = new CareStore(dir);
        b.loadAll();
        assertEquals(7, b.trough("w", 1, 1, 1).orElseThrow().contents().count("Wheat"));
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("w.json.corrupt-")));
        }
    }
}
