package com.hexvane.cozytalesranching.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.house.HouseService.Result;
import org.junit.jupiter.api.Test;

/** Using a house: followers move in when they can, and the house page opens whenever none could (Gibson's note 14). */
class HouseUseTest {
    @Test
    void noFollowersOpensThePage() {
        HouseUse.Decision d = HouseUse.decide(0, 0, Result.OK);
        assertTrue(d.openPage());
        assertEquals(null, d.refusal());
    }

    @Test
    void aFullHouseWithAFollowerStillOpensThePageAndSaysWhy() {
        HouseUse.Decision d = HouseUse.decide(1, 0, Result.HOUSE_FULL);
        assertTrue(d.openPage(), "a full house must never lock its own page");
        assertEquals(Result.HOUSE_FULL, d.refusal());
        assertEquals(Result.NOT_OWNER, HouseUse.decide(3, 0, Result.NOT_OWNER).refusal());
    }

    @Test
    void movingInIsTheWholeGesture() {
        HouseUse.Decision d = HouseUse.decide(2, 1, Result.HOUSE_FULL);
        assertFalse(d.openPage(), "one moved in, the other found the house full: chat says both, no page");
        assertFalse(HouseUse.decide(2, 2, Result.OK).openPage());
    }
}
