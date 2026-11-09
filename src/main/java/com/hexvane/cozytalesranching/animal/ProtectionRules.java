package com.hexvane.cozytalesranching.animal;

import javax.annotation.Nonnull;

/**
 * Which hits on a ranch animal are cancelled, free of engine types so it can be tested. An owned animal is
 * invulnerable: with {@code ProtectOwnedAnimals} on, nothing a player does and nothing the world does (falls, drowning,
 * fire, suffocation) hurts it; with {@code ProtectFromHostileMobs} on, no creature hurts it either. Unowned animals
 * (vanilla-tamed ones nobody claimed yet) are left as vanilla has them. With {@code AllowRaiding} on as well, only the
 * owner is kept from hurting their own animals: other players can kill them, so farms can be raided.
 */
public final class ProtectionRules {
    private ProtectionRules() {}

    /**
     * Where a hit comes from. {@code WORLD} is everything nobody caused (falls, drowning, lava, block fire).
     * {@code GONE} is a hit whose attacker no longer exists: an arrow or bomb still flying, or a burn or poison still
     * ticking, after the mob (or player) that started it died or despawned. It cannot be told apart, so either
     * switch protects from it. {@code OWNER} is the animal's owner, {@code PLAYER} any other player.
     */
    public enum Source { OWNER, PLAYER, CREATURE, WORLD, GONE }

    public static boolean cancels(boolean owned, @Nonnull Source source, boolean protectOwned, boolean protectFromMobs) {
        return cancels(owned, source, protectOwned, protectFromMobs, false);
    }

    /** @param allowRaiding players other than the owner may hurt owned animals (the owner still cannot) */
    public static boolean cancels(boolean owned, @Nonnull Source source, boolean protectOwned, boolean protectFromMobs, boolean allowRaiding) {
        if (!owned) return false;
        return switch (source) {
            case OWNER, WORLD -> protectOwned;
            case PLAYER -> protectOwned && !allowRaiding;
            case CREATURE -> protectFromMobs;
            case GONE -> protectOwned || protectFromMobs;
        };
    }

    /**
     * Sorts a hit by what the damage source says.
     *
     * @param hasAttacker   the source names an attacking entity (an entity, projectile or effect-owner source)
     * @param attackerValid that entity still exists
     * @param attackerIsPlayer that entity is a player
     * @param orphanEffect  the source is a status effect (burn, poison) that was applied by an entity that is gone now
     */
    @Nonnull
    public static Source classify(boolean hasAttacker, boolean attackerValid, boolean attackerIsPlayer, boolean orphanEffect) {
        return classify(hasAttacker, attackerValid, attackerIsPlayer, false, orphanEffect);
    }

    /** @param attackerIsOwner that entity is the player who owns the animal */
    @Nonnull
    public static Source classify(boolean hasAttacker, boolean attackerValid, boolean attackerIsPlayer, boolean attackerIsOwner, boolean orphanEffect) {
        if (hasAttacker) {
            if (attackerValid && attackerIsPlayer && attackerIsOwner) return Source.OWNER;
            if (!attackerValid) return Source.GONE;
            return attackerIsPlayer ? Source.PLAYER : Source.CREATURE;
        }
        return orphanEffect ? Source.GONE : Source.WORLD;
    }
}
