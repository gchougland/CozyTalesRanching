package com.hexvane.cozytalesranching.taming;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.protocol.MovementStates;
import org.junit.jupiter.api.Test;

/** The walk-in-place rule (fix4): an owned animal the server calls walking while it stands still is set standing. */
class WalkSettleTest {

    @Test
    void walkingStillAndUnsteeredOnTheGroundSettles() {
        assertTrue(WalkSettle.stuckWalking(true, false, true, true, true, 0, 0));
        assertTrue(WalkSettle.stuckWalking(false, true, true, true, true, 0.01, -0.01));
    }

    @Test
    void anAnimalThatMovesIsLeftAlone() {
        assertFalse(WalkSettle.stuckWalking(true, false, true, true, true, 0.03, 0));
        assertFalse(WalkSettle.stuckWalking(true, false, true, true, true, 0.5, 0.5));
    }

    @Test
    void anAnimalThatIsAskedToWalkIsLeftAlone() {
        // pushing into a fence while following: steering asks for a step, the follow pass judges that
        assertFalse(WalkSettle.stuckWalking(true, false, true, false, true, 0, 0));
    }

    @Test
    void idleFallingOrNewAnimalsAreLeftAlone() {
        assertFalse(WalkSettle.stuckWalking(false, false, true, true, true, 0, 0));
        assertFalse(WalkSettle.stuckWalking(true, false, false, true, true, 0, 0));
        // first pass: no earlier position, so it has not been still for a whole pass yet
        assertFalse(WalkSettle.stuckWalking(true, false, true, true, false, 0, 0));
    }

    @Test
    void standStillWritesTheStandingStatesAndKeepsTheRest() {
        MovementStates m = new MovementStates();
        m.walking = true;
        m.running = true;
        m.jumping = true;
        m.onGround = true;
        m.crouching = true;
        WalkSettle.standStill(m);
        assertTrue(m.idle);
        assertTrue(m.horizontalIdle);
        assertFalse(m.walking);
        assertFalse(m.running);
        assertFalse(m.jumping);
        assertTrue(m.onGround);
        assertTrue(m.crouching);
    }
}
