package com.hexvane.cozytalesranching.items;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Raven armour's perk, pure logic. Each Raven Leather piece worn (the mod's own {@code CozyRanch_Armor_Raven_*}, which
 * the Armour Bench makes and whose tooltip states the perk, or a vanilla {@code Armor_Leather_Raven_*}) gives a chance that an arrow shot from a bow
 * or crossbow comes back to the quiver: 5% a piece, 25% for the whole set. Vanilla armour cannot reach arrows itself
 * (its damage enhancements are read from the projectile, which wears no armour), and its ammo is taken by a
 * {@code ModifyInventory} step with no hook, so the refund is given after the shot leaves.
 */
public final class RavenFletchingRules {
    /** The four armour slots a Raven set covers. */
    public static final List<String> SLOTS = List.of("Head", "Chest", "Hands", "Legs");
    /** The mod's four Raven pieces (they parent vanilla's: same model, texture and stats, plus a description). */
    public static final Set<String> PIECES = Set.of(
        "CozyRanch_Armor_Raven_Head", "CozyRanch_Armor_Raven_Chest", "CozyRanch_Armor_Raven_Hands", "CozyRanch_Armor_Raven_Legs");
    /** Vanilla's four Raven Leather pieces (no description of their own); they carry the perk too. */
    public static final Set<String> VANILLA_PIECES = Set.of(
        "Armor_Leather_Raven_Head", "Armor_Leather_Raven_Chest", "Armor_Leather_Raven_Hands", "Armor_Leather_Raven_Legs");
    /** The only vanilla arrow; every vanilla bow and crossbow takes it. */
    public static final String ARROW = "Weapon_Arrow_Crude";
    public static final double CHANCE_PER_PIECE = 0.05;
    public static final double FULL_SET_BONUS = 0.05;
    /** One roll per player per this many ms, so a multi-arrow volley (paid for with signature charge) is one roll. */
    public static final long MIN_GAP_MS = 350;

    private RavenFletchingRules() {}

    /** How many armour slots hold a Raven piece (ours or vanilla's) among the worn item ids. */
    public static int piecesWorn(Collection<String> wornItemIds) {
        return (int) wornItemIds.stream().map(RavenFletchingRules::slotOf).filter(slot -> slot != null).distinct().count();
    }

    /** The slot a Raven piece covers, or null when the id is not a Raven piece. */
    static String slotOf(String itemId) {
        if (itemId == null || !(PIECES.contains(itemId) || VANILLA_PIECES.contains(itemId))) return null;
        return itemId.substring(itemId.lastIndexOf('_') + 1);
    }

    /** Chance (0 to 1) that one shot's arrow is given back. */
    public static double refundChance(int pieces) {
        if (pieces <= 0) return 0;
        int n = Math.min(pieces, SLOTS.size());
        return n * CHANCE_PER_PIECE + (n == SLOTS.size() ? FULL_SET_BONUS : 0);
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
