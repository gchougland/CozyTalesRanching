package com.hexvane.cozytalesranching.behaviour;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The follow pass drops a clip only when nobody is timing it (fix 4 note 11: stuck asleep while following). */
class StrayClipTest {
    @Test
    void aSleepNobodyTimesIsCleared() {
        assertTrue(AnimalClips.shouldClear("Sleep", null, 0, 1_000));
    }

    @Test
    void aTimedHappyHopIsLeftToFinish() {
        assertFalse(AnimalClips.shouldClear("Happy", "Happy", 2_500, 1_000));
    }

    @Test
    void anotherClipThanTheTimedOneIsCleared() {
        assertTrue(AnimalClips.shouldClear("Sleep", "Happy", 2_500, 1_000));
    }

    @Test
    void aTimerPastItsEndNoLongerProtectsTheClip() {
        assertTrue(AnimalClips.shouldClear("Happy", "Happy", 2_500, 3_000));
    }

    @Test
    void nothingPlayingIsNothingToClear() {
        assertFalse(AnimalClips.shouldClear(null, null, 0, 0));
    }
}
