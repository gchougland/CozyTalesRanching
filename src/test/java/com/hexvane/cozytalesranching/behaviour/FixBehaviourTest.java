package com.hexvane.cozytalesranching.behaviour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fix wave 1: the follow progress rule and which roles walk softly. */
class FixBehaviourTest {
    @Test
    void walkingTowardTheOwnerIsProgress() {
        // 3.6 blocks closer in a second (a cow at 0.9 of its speed)
        assertEquals(0, FollowProgress.stuckSeconds(12.0, 8.4, 1, 4));
    }

    @Test
    void walkingAfterARunningOwnerIsProgress() {
        // the owner ran 5 blocks off: measured against where the owner is now, the animal still came 3.6 closer
        assertEquals(0, FollowProgress.stuckSeconds(17.0, 13.4, 1, 5));
    }

    @Test
    void standingStillFarAwayCounts() {
        assertEquals(1, FollowProgress.stuckSeconds(9.0, 9.0, 1, 0));
        assertEquals(6, FollowProgress.stuckSeconds(9.0, 9.0, 1, 5));
    }

    @Test
    void slidingAlongAFenceCounts() {
        // it moved, but only 0.2 closer: the old rule (moved less than 0.3) never fired for this
        assertEquals(3, FollowProgress.stuckSeconds(10.0, 9.8, 1, 2));
        // walking away along the fence
        assertEquals(1, FollowProgress.stuckSeconds(10.0, 10.6, 1, 0));
    }

    @Test
    void besideTheOwnerNothingIsJudged() {
        assertEquals(0, FollowProgress.stuckSeconds(4.0, 4.0, 1, 30));
        assertEquals(0, FollowProgress.stuckSeconds(5.0, FollowProgress.NEAR, 1, 30));
    }

    @Test
    void onlyOurRanchRolesWalkSoftly() {
        assertTrue(GentleStepSystem.isRanchRole("CozyRanch_Frog"));
        assertTrue(GentleStepSystem.isRanchRole("CozyRanch_Cow_Calf"));
        assertFalse(GentleStepSystem.isRanchRole("CozyRanchWild_Frog_Purple"));
        assertFalse(GentleStepSystem.isRanchRole("Frog_Green"));
        assertFalse(GentleStepSystem.isRanchRole(null));
    }
}
