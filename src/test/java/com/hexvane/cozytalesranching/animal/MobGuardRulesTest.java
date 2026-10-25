package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.ProtectionRules.Source;
import org.junit.jupiter.api.Test;

/** Fix round 4, "enemies killed them": how hits on owned animals are sorted, and what each switch covers. */
class MobGuardRulesTest {
    @Test
    void liveAttackersAreSortedByWhatTheyAre() {
        assertEquals(Source.CREATURE, ProtectionRules.classify(true, true, false, false));
        assertEquals(Source.PLAYER, ProtectionRules.classify(true, true, true, false));
    }

    @Test
    void anAttackerThatIsGoneIsNotAPlayer() {
        // a skeleton's arrow or a lobber's bomb landing after the mob died: the source still names the dead mob
        assertEquals(Source.GONE, ProtectionRules.classify(true, false, false, false));
        // a burn or poison still ticking after the burner or spider that applied it was killed
        assertEquals(Source.GONE, ProtectionRules.classify(false, false, false, true));
    }

    @Test
    void nobodyIsTheWorld() {
        assertEquals(Source.WORLD, ProtectionRules.classify(false, false, false, false));
    }

    @Test
    void hitsFromGoneAttackersAreCancelledUnderEitherSwitch() {
        assertTrue(ProtectionRules.cancels(true, Source.GONE, true, true));
        assertTrue(ProtectionRules.cancels(true, Source.GONE, false, true), "mob protection alone covers a dead mob's burn");
        assertTrue(ProtectionRules.cancels(true, Source.GONE, true, false));
        assertFalse(ProtectionRules.cancels(true, Source.GONE, false, false));
        assertFalse(ProtectionRules.cancels(false, Source.GONE, true, true), "unowned animals stay vanilla");
    }

    @Test
    void mobProtectionAloneStillCoversEveryMobPath() {
        // ProtectOwnedAnimals off (players may hurt animals), ProtectFromHostileMobs on
        assertTrue(ProtectionRules.cancels(true, ProtectionRules.classify(true, true, false, false), false, true), "melee, arrow, bomb, live mob's burn");
        assertTrue(ProtectionRules.cancels(true, ProtectionRules.classify(true, false, false, false), false, true), "projectile of a dead mob");
        assertTrue(ProtectionRules.cancels(true, ProtectionRules.classify(false, false, false, true), false, true), "burn of a dead mob");
        assertFalse(ProtectionRules.cancels(true, ProtectionRules.classify(true, true, true, false), false, true), "players may hurt them");
    }
}
