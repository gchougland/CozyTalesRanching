package com.hexvane.cozytalesranching.items;

import java.util.Collection;
import java.util.Set;

/**
 * Raven armour's perk, pure logic. Each vanilla Raven Leather piece worn gives a chance that an arrow shot from a bow
 * or crossbow comes back to the quiver: 5% a piece, 25% for the whole set. Vanilla armour cannot reach arrows itself
 * (its damage enhancements are read from the projectile, which wears no armour), and its ammo is taken by a
 * {@code ModifyInventory} step with no hook, so the refund is given after the shot leaves.
 */
public final class RavenFletchingRules {
    /** The four vanilla Raven Leather pieces (their stats are vanilla's; the mod adds only recipes and this perk). */
    public static final Set<String> PIECES = Set.of(
        "Armor_Leather_Raven_Head", "Armor_Leather_Raven_Chest", "Armor_Leather_Raven_Hands", "Armor_Leather_Raven_Legs");
    /** The only vanilla arrow; every vanilla bow and crossbow takes it. */
    public static final String ARROW = "Weapon_Arrow_Crude";
    public static final double CHANCE_PER_PIECE = 0.05;
    public static final double FULL_SET_BONUS = 0.05;
    /** One roll per player per this many ms, so a multi-arrow volley (paid for with signature charge) is one roll. */
    public static final long MIN_GAP_MS = 350;

    private RavenFletchingRules() {}

    /** How many distinct Raven pieces are among the worn item ids. */
    public static int piecesWorn(Collection<String> wornItemIds) {
        return (int) wornItemIds.stream().filter(PIECES::contains).distinct().count();
    }

    /** Chance (0 to 1) that one shot's arrow is given back. */
    public static double refundChance(int pieces) {
        if (pieces <= 0) return 0;
        int n = Math.min(pieces, PIECES.size());
        return n * CHANCE_PER_PIECE + (n == PIECES.size() ? FULL_SET_BONUS : 0);
    }

    /** Whether the held weapon shoots vanilla arrows: shortbows and crossbows, not the bomb bow. */
    public static boolean shootsArrows(String heldItemId) {
        if (heldItemId == null) return false;
        if (heldItemId.contains("Bomb")) return false;
        return heldItemId.startsWith("Weapon_Shortbow") || heldItemId.startsWith("Weapon_Crossbow");
    }

    /** Whether a roll is allowed now, given the last roll's time for this player (or a negative value for none). */
    public static boolean mayRoll(long nowMs, long lastRollMs) {
        return lastRollMs < 0 || nowMs - lastRollMs >= MIN_GAP_MS;
    }
}
