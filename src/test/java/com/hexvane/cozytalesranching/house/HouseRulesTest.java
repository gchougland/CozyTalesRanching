package com.hexvane.cozytalesranching.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.house.HouseRules.Phase;
import com.hexvane.cozytalesranching.house.HouseService.Result;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HouseRulesTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void phaseFollowsSunsetAndSunrise() {
        assertEquals(Phase.NIGHT, HouseRules.phase(0.0));
        assertEquals(Phase.NIGHT, HouseRules.phase(0.2));
        assertEquals(Phase.DAY, HouseRules.phase(HouseRules.DAWN));
        assertEquals(Phase.DAY, HouseRules.phase(0.5));
        assertEquals(Phase.DAY, HouseRules.phase(0.76));
        assertEquals(Phase.NIGHT, HouseRules.phase(HouseRules.DUSK));
        assertEquals(Phase.NIGHT, HouseRules.phase(0.99));
    }

    @Test
    void afterMidnightIsStillThePreviousNight() {
        assertEquals(99, HouseRules.cycleDay(100, 0.1));
        assertEquals(100, HouseRules.cycleDay(100, 0.5));
        assertEquals(100, HouseRules.cycleDay(100, 0.9));
    }

    /** Steps through three days a second of day progress at a time, as the day system does, counting mornings. */
    @Test
    void morningHappensOncePerDay() {
        long last = HouseRules.cycleDay(10, 0.5);
        int mornings = 0;
        for (long day = 10; day < 13; day++) {
            for (int step = 0; step < 1000; step++) {
                double p = step / 1000.0;
                if (day == 10 && p < 0.5) continue;
                if (HouseRules.phase(p) != Phase.DAY) continue;
                long cycle = HouseRules.cycleDay(day, p);
                if (HouseRules.morningDue(last, cycle)) {
                    mornings++;
                    last = Math.max(last, cycle);
                }
            }
        }
        assertEquals(2, mornings, "placed at noon on day 10: mornings on days 11 and 12 only");
        assertEquals(12, last);
    }

    @Test
    void morningIsNotRepeatedAfterARestartOrTimeGoingBack() {
        assertTrue(HouseRules.morningDue(-1, 0));
        assertFalse(HouseRules.morningDue(12, 12));
        assertFalse(HouseRules.morningDue(12, 11));
        assertTrue(HouseRules.morningDue(12, 13));
    }

    @Test
    void arrivingAndTimingOut() {
        assertTrue(HouseRules.arrived(1, 0, 1));
        assertFalse(HouseRules.arrived(2, 0, 0));
        assertFalse(HouseRules.arrived(0, 3, 0));
        assertFalse(HouseRules.walkTimedOut(1000, 1000 + HouseRules.GO_HOME_TIMEOUT_MS - 1));
        assertTrue(HouseRules.walkTimedOut(1000, 1000 + HouseRules.GO_HOME_TIMEOUT_MS));
    }

    @Test
    void onlyTheOwnerWithManageOrManageAny() {
        assertEquals(Result.OK, HouseRules.checkManage(OWNER, OWNER, true, false));
        assertEquals(Result.NO_PERMISSION, HouseRules.checkManage(OWNER, OWNER, false, false));
        assertEquals(Result.NOT_OWNER, HouseRules.checkManage(OTHER, OWNER, true, false));
        assertEquals(Result.OK, HouseRules.checkManage(OTHER, OWNER, false, true));
        assertEquals(Result.NOT_OWNER, HouseRules.checkManage(OTHER, null, true, false));
    }

    @Test
    void assignChecksOwnerSpeciesAndCapacity() {
        assertEquals(Result.OK, HouseRules.checkAssign(Result.OK, OWNER, OWNER, "cow", "cow", false, 3, 4));
        assertEquals(Result.HOUSE_FULL, HouseRules.checkAssign(Result.OK, OWNER, OWNER, "cow", "cow", false, 4, 4));
        assertEquals(Result.OK, HouseRules.checkAssign(Result.OK, OWNER, OWNER, "cow", "cow", true, 4, 4));
        assertEquals(Result.WRONG_SPECIES, HouseRules.checkAssign(Result.OK, OWNER, OWNER, "pig", "cow", false, 0, 4));
        assertEquals(Result.NOT_OWNER, HouseRules.checkAssign(Result.OK, OTHER, OWNER, "cow", "cow", false, 0, 4));
        assertEquals(Result.NOT_OWNER, HouseRules.checkAssign(Result.OK, null, OWNER, "cow", "cow", false, 0, 4));
        assertEquals(Result.NO_PERMISSION, HouseRules.checkAssign(Result.NO_PERMISSION, OWNER, OWNER, "cow", "cow", false, 0, 4));
    }

    @Test
    void namesAreCleaned() {
        assertEquals("Moo Manor", HouseRules.cleanName("  Moo\u0007 Manor "));
        assertNull(HouseRules.cleanName("   "));
        assertNull(HouseRules.cleanName(null));
        assertEquals(HouseRules.MAX_NAME_LENGTH, HouseRules.cleanName("x".repeat(40)).length());
    }
}
