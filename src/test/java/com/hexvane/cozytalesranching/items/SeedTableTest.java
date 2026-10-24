package com.hexvane.cozytalesranching.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeedTableTest {
    private static Map<String, Integer> of(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    @Test
    void opensIntoExactlyTheRolledNumberOfSeeds() {
        SeedTable t = new SeedTable(of("Plant_Seeds_Wheat", 5, "Plant_Seeds_Carrot", 3), 3, id -> true);
        SplittableRandom r = new SplittableRandom(7);
        for (int i = 0; i < 200; i++) {
            Map<String, Integer> got = t.open(r);
            assertEquals(3, got.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(Set.of("Plant_Seeds_Wheat", "Plant_Seeds_Carrot").containsAll(got.keySet()));
        }
    }

    @Test
    void picksFollowTheWeights() {
        SeedTable t = new SeedTable(of("A", 9, "B", 1), 1, id -> true);
        SplittableRandom r = new SplittableRandom(11);
        int a = 0, n = 20000;
        for (int i = 0; i < n; i++) if ("A".equals(t.pick(r))) a++;
        assertEquals(0.9, a / (double) n, 0.02);
    }

    @Test
    void unknownBlankAndWeightlessEntriesAreSkipped() {
        Map<String, Integer> m = new HashMap<>(of("Plant_Seeds_Wheat", 4, "Not_An_Item", 50, "", 3, "Plant_Seeds_Corn", 0));
        m.put("Plant_Seeds_Rice", null);
        SeedTable t = new SeedTable(m, 4, id -> id.startsWith("Plant_Seeds_"));
        SplittableRandom r = new SplittableRandom(3);
        for (int i = 0; i < 100; i++) assertEquals(Map.of("Plant_Seeds_Wheat", 4), t.open(r));
    }

    @Test
    void anEmptyTableGivesNothing() {
        SeedTable t = new SeedTable(of("X", 3), 3, id -> false);
        assertTrue(t.isEmpty());
        assertNull(t.pick(new SplittableRandom(1)));
        assertTrue(t.open(new SplittableRandom(1)).isEmpty());
        assertTrue(new SeedTable(null, 3, id -> true).isEmpty());
    }

    @Test
    void rollsAreClamped() {
        assertEquals(SeedTable.DEFAULT_ROLLS, new SeedTable(of("A", 1), 0, id -> true).rolls());
        assertEquals(SeedTable.DEFAULT_ROLLS, new SeedTable(of("A", 1), -5, id -> true).rolls());
        assertEquals(SeedTable.MAX_ROLLS, new SeedTable(of("A", 1), 999, id -> true).rolls());
        assertEquals(1, new SeedTable(of("A", 1), 1, id -> true).rolls());
    }
}
