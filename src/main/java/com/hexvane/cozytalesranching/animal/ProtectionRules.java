package com.hexvane.cozytalesranching.animal;

import javax.annotation.Nonnull;

/**
 * Which hits on a ranch animal are cancelled, free of engine types so it can be tested. An owned animal is
 * invulnerable: with {@code ProtectOwnedAnimals} on, nothing a player does and nothing the world does (falls, drowning,
 * fire, suffocation) hurts it; with {@code ProtectFromHostileMobs} on, no creature hurts it either. Unowned animals
 * (vanilla-tamed ones nobody claimed yet) are left as vanilla has them.
 */
public final class ProtectionRules {
    private ProtectionRules() {}

    /** Where a hit comes from. {@code WORLD} is everything without a living attacker. */
    public enum Source { PLAYER, CREATURE, WORLD }

    public static boolean cancels(boolean owned, @Nonnull Source source, boolean protectOwned, boolean protectFromMobs) {
        if (!owned) return false;
        return switch (source) {
            case PLAYER, WORLD -> protectOwned;
            case CREATURE -> protectFromMobs;
        };
    }
}
