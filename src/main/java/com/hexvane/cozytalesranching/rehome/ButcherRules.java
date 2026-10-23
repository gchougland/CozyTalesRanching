package com.hexvane.cozytalesranching.rehome;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Yields and the confirmation rule for the Butcher's Block. A wild animal killed in the field rolls its vanilla
 * drop list once; a ranch adult rolls it {@link #rollFactor} times, so a well-raised animal (full happiness, five
 * hearts, bountiful) gives several times the meat and hide of a wild one and one animal goes a long way.
 */
final class ButcherRules {
    /** Rolls of the drop list for an adult with no happiness, hearts or traits counted yet. */
    static final double ADULT_ROLLS = 2.0;
    /** Happiness 0 gives this share of the rolls, happiness 1 gives {@code HAPPY_MIN + HAPPY_SPAN}. */
    static final double HAPPY_MIN = 0.75;
    static final double HAPPY_SPAN = 0.75;
    /** Each whole heart adds this share. */
    static final double PER_HEART = 0.1;
    static final int MAX_HEARTS = 5;
    /** Named animals and animals with this many hearts need a second use to be processed. */
    static final int CONFIRM_HEARTS = 4;
    static final long CONFIRM_WINDOW_MS = 10_000;

    private ButcherRules() {}

    /**
     * How many times the drop list is rolled, before rounding: {@link #ADULT_ROLLS} x happiness x hearts x the
     * {@code ProductionMultiplier} of the animal's traits (bountiful: 1.25).
     */
    static double rollFactor(float happiness, int hearts, double productionMultiplier) {
        double h = Math.clamp(happiness, 0.0, 1.0);
        int hs = Math.clamp(hearts, 0, MAX_HEARTS);
        double m = productionMultiplier > 0 ? productionMultiplier : 1.0;
        return ADULT_ROLLS * (HAPPY_MIN + HAPPY_SPAN * h) * (1.0 + PER_HEART * hs) * m;
    }

    /**
     * Whole rolls: the fraction of {@code factor} is a chance of one more. Lucky ({@code BonusProductChance}) then
     * gives each roll that chance of an extra roll.
     */
    static int rolls(double factor, double bonusChance, @Nonnull RandomGenerator rng) {
        int n = (int) Math.floor(factor);
        if (rng.nextDouble() < factor - n) n++;
        int extra = 0;
        double p = Math.clamp(bonusChance, 0.0, 1.0);
        for (int i = 0; i < n; i++) if (p > 0 && rng.nextDouble() < p) extra++;
        return Math.max(1, n + extra);
    }

    static boolean needsConfirm(@Nullable String name, int hearts) {
        return (name != null && !name.isBlank()) || hearts >= CONFIRM_HEARTS;
    }

    /**
     * Who asked to process which animal and when. The first use on a protected animal arms it; a second use on
     * the same animal by the same player within {@link #CONFIRM_WINDOW_MS} confirms. Memory only.
     */
    static final class Confirmations {
        private record Pending(UUID animalId, long atMs) {}

        private final Map<UUID, Pending> byPlayer = new HashMap<>();

        /** True if this use confirms an earlier one; otherwise arms it and returns false. */
        synchronized boolean confirmOrArm(@Nonnull UUID player, @Nonnull UUID animalId, long nowMs) {
            Pending p = byPlayer.get(player);
            if (p != null && Objects.equals(p.animalId(), animalId) && nowMs >= p.atMs() && nowMs - p.atMs() <= CONFIRM_WINDOW_MS) {
                byPlayer.remove(player);
                return true;
            }
            byPlayer.put(player, new Pending(animalId, nowMs));
            return false;
        }

        synchronized void clear(@Nonnull UUID player) {
            byPlayer.remove(player);
        }
    }
}
