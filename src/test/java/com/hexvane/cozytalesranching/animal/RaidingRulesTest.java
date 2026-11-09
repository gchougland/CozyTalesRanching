package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.ProtectionRules.Source;
import org.junit.jupiter.api.Test;

/** {@code AllowRaiding}: the owner still cannot hurt their own animals, other players can. */
class RaidingRulesTest {
    @Test
    void theOwnerIsToldApartFromOtherPlayers() {
        assertEquals(Source.OWNER, ProtectionRules.classify(true, true, true, true, false));
        assertEquals(Source.PLAYER, ProtectionRules.classify(true, true, true, false, false));
        // a creature is never the owner, and a gone attacker stays gone
        assertEquals(Source.CREATURE, ProtectionRules.classify(true, true, false, true, false));
        assertEquals(Source.GONE, ProtectionRules.classify(true, false, true, true, false));
    }

    @Test
    void withRaidingOnlyTheOwnerIsKeptOff() {
        assertTrue(ProtectionRules.cancels(true, Source.OWNER, true, true, true), "owner swing still blocked");
        assertFalse(ProtectionRules.cancels(true, Source.PLAYER, true, true, true), "raiders can hurt owned animals");
        assertTrue(ProtectionRules.cancels(true, Source.WORLD, true, true, true), "falls and fire stay blocked");
        assertTrue(ProtectionRules.cancels(true, Source.CREATURE, true, true, true), "mobs stay blocked");
    }

    @Test
    void withoutRaidingEveryPlayerIsKeptOff() {
        assertTrue(ProtectionRules.cancels(true, Source.OWNER, true, true, false));
        assertTrue(ProtectionRules.cancels(true, Source.PLAYER, true, true, false));
        assertTrue(ProtectionRules.cancels(true, Source.PLAYER, true, true), "old overload means no raiding");
    }

    @Test
    void raidingNeedsProtectionOn() {
        assertFalse(ProtectionRules.cancels(true, Source.OWNER, false, true, true));
        assertFalse(ProtectionRules.cancels(true, Source.PLAYER, false, true, true));
        assertFalse(ProtectionRules.cancels(false, Source.OWNER, true, true, true), "unowned animals stay vanilla");
    }
}
