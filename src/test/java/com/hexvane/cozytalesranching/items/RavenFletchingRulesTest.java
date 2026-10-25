package com.hexvane.cozytalesranching.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RavenFletchingRulesTest {
    @Test
    void countsDistinctRavenPiecesOnly() {
        assertEquals(0, RavenFletchingRules.piecesWorn(List.of("Armor_Iron_Head", "Armor_Leather_Heavy_Chest")));
        assertEquals(2, RavenFletchingRules.piecesWorn(List.of("Armor_Leather_Raven_Head", "Armor_Iron_Chest", "Armor_Leather_Raven_Legs")));
        assertEquals(4, RavenFletchingRules.piecesWorn(List.copyOf(RavenFletchingRules.PIECES)));
        assertEquals(4, RavenFletchingRules.piecesWorn(List.copyOf(RavenFletchingRules.VANILLA_PIECES)));
    }

    @Test
    void oursAndVanillaMixAsOneSet() {
        assertEquals(4, RavenFletchingRules.piecesWorn(List.of(
            "CozyRanch_Armor_Raven_Head", "Armor_Leather_Raven_Chest", "CozyRanch_Armor_Raven_Hands", "Armor_Leather_Raven_Legs")));
        assertEquals(1, RavenFletchingRules.piecesWorn(List.of("CozyRanch_Armor_Raven_Head", "Armor_Leather_Raven_Head")));
        assertEquals(0.25, RavenFletchingRules.refundChance(RavenFletchingRules.piecesWorn(List.of(
            "Armor_Leather_Raven_Head", "CozyRanch_Armor_Raven_Chest", "Armor_Leather_Raven_Hands", "CozyRanch_Armor_Raven_Legs"))), 1e-9);
    }

    @Test
    void chanceGrowsPerPieceWithAFullSetBonus() {
        assertEquals(0.0, RavenFletchingRules.refundChance(0), 1e-9);
        assertEquals(0.05, RavenFletchingRules.refundChance(1), 1e-9);
        assertEquals(0.15, RavenFletchingRules.refundChance(3), 1e-9);
        assertEquals(0.25, RavenFletchingRules.refundChance(4), 1e-9);
        assertEquals(0.25, RavenFletchingRules.refundChance(9), 1e-9);
    }

    @Test
    void onlyArrowWeapons() {
        assertTrue(RavenFletchingRules.shootsArrows("Weapon_Shortbow_Iron"));
        assertTrue(RavenFletchingRules.shootsArrows("Weapon_Crossbow_Ancient_Steel"));
        assertFalse(RavenFletchingRules.shootsArrows("Weapon_Shortbow_Bomb"));
        assertFalse(RavenFletchingRules.shootsArrows("Weapon_Staff_Ice"));
        assertFalse(RavenFletchingRules.shootsArrows(null));
    }

    @Test
    void oneRollPerVolley() {
        assertTrue(RavenFletchingRules.mayRoll(1000, -1));
        assertFalse(RavenFletchingRules.mayRoll(1100, 1000));
        assertTrue(RavenFletchingRules.mayRoll(1350, 1000));
    }
}
