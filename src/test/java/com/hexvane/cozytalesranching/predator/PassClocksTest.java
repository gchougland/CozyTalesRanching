package com.hexvane.cozytalesranching.predator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Fix 4 perf: each world keeps its own predator pass clocks. */
class PassClocksTest {
    @Test
    void twoWorldsTickingInTurnEachGetTheirOwnPasses() {
        PassClocks clocks = new PassClocks();
        int lureA = 0, lureB = 0, timerA = 0, timerB = 0;
        // 12.5 s of two worlds at 8 ticks a second, interleaved on the one system instance
        for (int i = 0; i < 100; i++) {
            PassClocks.Clock a = clocks.of("a");
            a.advance(0.125f);
            if (a.takeLure()) lureA++;
            if (a.takeTimer()) timerA++;
            PassClocks.Clock b = clocks.of("b");
            b.advance(0.125f);
            if (b.takeLure()) lureB++;
            if (b.takeTimer()) timerB++;
        }
        assertEquals(lureA, lureB);
        assertEquals(timerA, timerB);
        assertEquals(6, lureA, "one lure pass every 2 s of that world's own time, not shared");
        assertEquals(12, timerA);
    }

    @Test
    void aSecondWorldDoesNotSpeedUpTheFirst() {
        PassClocks clocks = new PassClocks();
        PassClocks.Clock a = clocks.of("a");
        clocks.of("b").advance(1.9f);
        a.advance(0.5f);
        assertEquals(false, a.takeLure());
        assertEquals(true, a.takeSprint());
    }
}
