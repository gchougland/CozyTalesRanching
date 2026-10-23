package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import java.util.random.RandomGenerator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Pure production rules: no engine calls, so every number is unit-tested. */
public final class ProduceRules {
    /** Trait effect keys in {@code Traits.json}. */
    public static final String PRODUCTION_MULTIPLIER = "ProductionMultiplier";
    public static final String BONUS_PRODUCT_CHANCE = "BonusProductChance";
    public static final String PREMIUM_FEED = "premium";

    /** How a species product is collected ({@code Products[].Collect}). */
    public enum Mode {
        /** Waits in the house; collected as is. */
        HOUSE,
        /** Waits in the house; each one collected turns one empty bucket from the inventory into it. */
        BUCKET,
        /** Not made by houses (vanilla harvest by hand, or unknown). */
        NONE
    }

    private ProduceRules() {}

    @Nonnull
    public static Mode mode(@Nullable String collect) {
        if (collect == null) return Mode.NONE;
        return switch (collect.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "house" -> Mode.HOUSE;
            case "bucket" -> Mode.BUCKET;
            default -> Mode.NONE;
        };
    }

    /** An adult living in a house (inside or out) produces; babies and unhoused animals do not. */
    public static boolean produces(@Nonnull AnimalRecord r) {
        return r.houseId != null && !r.baby && (r.state == AnimalRecord.State.ACTIVE || r.state == AnimalRecord.State.STORED);
    }

    /**
     * How fast an animal produces compared with the species interval: happiness scales it from
     * {@code HappinessRateAtZero} to 1, each heart adds {@code HeartRateBonus}, then traits and premium feed multiply.
     * Never below {@code MinRateFactor}, so an unhappy animal is slower but never stops.
     */
    public static double rateFactor(@Nonnull ProduceTuning t, float happiness, int hearts, double traitMultiplier, boolean premium) {
        double h = Math.clamp(Float.isNaN(happiness) ? 0.0 : happiness, 0.0, 1.0);
        double f = t.HappinessRateAtZero + (1.0 - t.HappinessRateAtZero) * h;
        f *= 1.0 + t.HeartRateBonus * Math.clamp(hearts, 0, 5);
        if (traitMultiplier > 0 && Double.isFinite(traitMultiplier)) f *= traitMultiplier;
        if (premium) f *= t.PremiumFeedMultiplier;
        return Math.max(t.MinRateFactor, f);
    }

    /** Minutes of {@code [now - minutes, now]} during which premium feed was in effect. */
    public static double premiumMinutes(@Nullable String feedBonus, long feedBonusUntilMs, long nowMs, double minutes) {
        if (!PREMIUM_FEED.equals(feedBonus) || minutes <= 0) return 0;
        double startMs = nowMs - minutes * 60_000.0;
        return Math.clamp((feedBonusUntilMs - startMs) / 60_000.0, 0.0, minutes);
    }

    /** Real minutes to credit since the last pass, capped. Zero for a first pass or a clock that went back. */
    public static double catchUpMinutes(long lastPassMs, long nowMs, double capMinutes) {
        if (lastPassMs <= 0 || nowMs <= lastPassMs) return 0;
        return Math.min((nowMs - lastPassMs) / 60_000.0, Math.max(0, capMinutes));
    }

    public static int room(int stored, int cap) {
        return Math.max(0, cap - Math.max(0, stored));
    }

    /** Items for {@code units} finished intervals: {@code Quantity} each, and per unit a {@code bonusChance} roll for one more. */
    public static int batch(int units, int quantity, double bonusChance, @Nonnull RandomGenerator rng) {
        if (units <= 0) return 0;
        int total = units * Math.max(1, quantity);
        if (bonusChance > 0) {
            for (int i = 0; i < units; i++) if (rng.nextDouble() < bonusChance) total++;
        }
        return total;
    }

    /** Result of one animal's product over a pass: items added to the house, and the interval progress left. */
    public record Step(int added, double progress) {}

    /**
     * One animal, one product, one pass. {@code progress} is the fraction of an interval already done. A full house
     * pauses production (the progress stays where it was). Items that did not fit in a nearly full house go back into
     * progress, so nothing made is lost and the house is full until collected.
     */
    @Nonnull
    public static Step step(@Nonnull ProduceTuning t, double progress, double minutes, double intervalMinutes, int quantity,
                            float happiness, int hearts, double traitMultiplier, double bonusChance, double premiumMinutes,
                            int room, @Nonnull RandomGenerator rng) {
        double p = Double.isFinite(progress) && progress > 0 ? progress : 0;
        if (room <= 0 || minutes <= 0) return new Step(0, p);
        double interval = Math.max(0.1, intervalMinutes);
        double premium = Math.clamp(premiumMinutes, 0.0, minutes);
        p += (minutes - premium) * rateFactor(t, happiness, hearts, traitMultiplier, false) / interval;
        p += premium * rateFactor(t, happiness, hearts, traitMultiplier, true) / interval;
        int units = (int) Math.min(Integer.MAX_VALUE / 4, Math.floor(p));
        p -= units;
        int made = batch(units, quantity, bonusChance, rng);
        int added = Math.min(made, room);
        if (made > added) p += (made - added) / (double) Math.max(1, quantity);
        return new Step(added, p);
    }

    /** Collecting a bucket product: how many convert with the empty buckets the player has, and how many keep waiting. */
    public record BucketPlan(int convert, int waiting) {}

    @Nonnull
    public static BucketPlan bucketPlan(int waitingInHouse, int emptyBuckets) {
        int waiting = Math.max(0, waitingInHouse);
        int convert = Math.min(waiting, Math.max(0, emptyBuckets));
        return new BucketPlan(convert, waiting - convert);
    }
}
