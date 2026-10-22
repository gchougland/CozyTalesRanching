package com.hexvane.cozytalesranching.care;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class TroughContentsTest {
    private static final ToIntFunction<String> STACK_25 = item -> 25;

    @Test
    void oneFoodFillsAtMostFourStacks() {
        TroughContents t = new TroughContents();
        assertEquals(60, t.add("Wheat", 60, STACK_25));
        assertEquals(3, t.slots.size());
        assertEquals(40, t.add("Wheat", 64, STACK_25));
        assertEquals(4, t.slots.size());
        assertEquals(100, t.count("Wheat"));
        assertEquals(0, t.add("Wheat", 1, STACK_25));
    }

    @Test
    void topsUpTheSameFoodBeforeOpeningSlots() {
        TroughContents t = new TroughContents();
        t.add("Wheat", 10, STACK_25);
        t.add("Corn", 5, STACK_25);
        assertEquals(15, t.add("Wheat", 15, STACK_25));
        assertEquals(2, t.slots.size());
        assertEquals(25, t.count("Wheat"));
    }

    @Test
    void mixedFoodsShareTheSlots() {
        TroughContents t = new TroughContents();
        t.add("Wheat", 25, STACK_25);
        t.add("Corn", 25, STACK_25);
        t.add("Pumpkin", 3, STACK_25);
        t.add("Carrot", 3, STACK_25);
        assertEquals(0, t.add("Lettuce", 1, STACK_25), "a fifth food has no slot");
        assertEquals(22, t.add("Pumpkin", 30, STACK_25), "an existing stack still tops up");
    }

    @Test
    void takeOneUsesTheSmallestStackAndDropsEmptySlots() {
        TroughContents t = new TroughContents();
        t.add("Wheat", 26, STACK_25);
        assertTrue(t.takeOne("Wheat"));
        assertEquals(1, t.slots.size(), "the stack of one is used up first");
        assertEquals(25, t.count("Wheat"));
        assertFalse(t.takeOne("Corn"));
    }

    @Test
    void takeAllEmptiesAndReturnsEverything() {
        TroughContents t = new TroughContents();
        t.add("Wheat", 30, STACK_25);
        t.add("Corn", 2, STACK_25);
        List<TroughContents.Slot> all = t.takeAll();
        assertEquals(3, all.size());
        assertEquals(32, all.stream().mapToInt(s -> s.count).sum());
        assertTrue(t.isEmpty());
        assertTrue(t.takeAll().isEmpty());
    }

    @Test
    void ignoresNothingAndRespectsSmallStacks() {
        TroughContents t = new TroughContents();
        assertEquals(0, t.add("Wheat", 0, STACK_25));
        assertEquals(0, t.add("", 5, STACK_25));
        assertEquals(4, t.add("Jar", 9, item -> 1), "stack size 1 means four items");
    }
}
