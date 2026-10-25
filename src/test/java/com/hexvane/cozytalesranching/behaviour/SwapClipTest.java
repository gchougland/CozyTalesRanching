package com.hexvane.cozytalesranching.behaviour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.behaviour.AnimalClips.SwapStep;
import org.junit.jupiter.api.Test;

/** A clip asked for while a role swap is pending plays on the new entity (fix4: Happy held after befriending). */
class SwapClipTest {

    @Test
    void waitsWhileTheOldEntityStillWaitsForItsSwap() {
        assertEquals(SwapStep.WAIT, AnimalClips.swapStep(true, true, true, 5));
    }

    @Test
    void playsOnceTheUuidResolvesToTheNewEntity() {
        assertEquals(SwapStep.PLAY, AnimalClips.swapStep(true, false, false, 5));
        // a new ref is the swap even if the new role happened to be asked to change again
        assertEquals(SwapStep.PLAY, AnimalClips.swapStep(true, false, true, 5));
        // same ref, but nothing pending any more (a request the engine dropped): play there
        assertEquals(SwapStep.PLAY, AnimalClips.swapStep(true, true, false, 5));
    }

    @Test
    void waitsThroughTheTickTheEntityIsBetweenRemoveAndAdd() {
        assertEquals(SwapStep.WAIT, AnimalClips.swapStep(false, false, true, 5));
    }

    @Test
    void givesUpWhenTheSwapNeverLands() {
        assertEquals(SwapStep.GIVE_UP, AnimalClips.swapStep(false, false, true, 0));
        assertEquals(SwapStep.GIVE_UP, AnimalClips.swapStep(true, true, true, 0));
    }

    @Test
    void looksForAboutThreeSeconds() {
        long ms = AnimalClips.SWAP_POLL_MS * AnimalClips.SWAP_POLL_TRIES;
        assertTrue(ms >= 2000 && ms <= 5000, "poll window " + ms + " ms");
    }
}
