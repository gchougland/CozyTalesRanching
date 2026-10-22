package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.taming.TamingRules;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * The numbers of troughs, meals and happiness, free of engine types so they can be unit tested.
 * Happiness never causes harm: it only gates breeding (and later production).
 */
public final class CareRules {
    /** Happiness never drifts below this; an ignored animal is a little dull, never miserable. */
    public static final float FLOOR = 0.30f;
    public static final float BASE = 0.30f;
    public static final float FED = 0.20f;
    public static final float PREFERRED = 0.10f;
    public static final float HOUSE = 0.10f;
    public static final float HABITAT_MET = 0.10f;
    public static final float HABITAT_SCORE = 0.10f;
    public static final float PETTED = 0.10f;
    public static final float TREAT = 0.05f;

    /** Time constants of the drift toward the target: it rises faster than it falls. */
    public static final long RISE_TAU_MS = 8 * 60_000L;
    public static final long FALL_TAU_MS = 20 * 60_000L;
    /** A longer gap (restart, unloaded world) counts as this much, so happiness never jumps. */
    public static final long MAX_STEP_MS = 10 * 60_000L;

    /** Friendship from a favourite treat eaten from a trough (hand-feeding one gives 0.3). */
    public static final float TROUGH_TREAT_FRIENDSHIP = 0.1f;

    public static final long MIN_MEAL_INTERVAL_MS = 5 * 60_000L;
    public static final long MAX_MEAL_INTERVAL_MS = 60 * 60_000L;
    public static final long DEFAULT_MEAL_INTERVAL_MS = 30 * 60_000L;
    /** A meal keeps an animal "fed" for this many meal intervals, so one missed meal costs nothing. */
    public static final int FED_FOR_MEALS = 2;
    static final long GAME_DAY_MS = 86_400_000L;

    public record Choice(@Nonnull String item, @Nonnull FoodKind kind) {}

    /** What the happiness target is built from. */
    public record Inputs(
        boolean fedRecently,
        @Nonnull FoodKind lastMeal,
        boolean treatRecently,
        boolean hasHouse,
        boolean habitatMet,
        float habitatScore,
        boolean pettedRecently
    ) {}

    /** Each part of the target, for the house page; {@code target} is their sum, clamped to FLOOR..1. */
    public record Breakdown(
        float base,
        float fed,
        float preferred,
        float house,
        float habitatMet,
        float habitatScore,
        float petted,
        float treat,
        float target
    ) {}

    private CareRules() {}

    /** The best food the species likes among {@code available}: favourite treat, then preferred, then basic, in species-file order. */
    @Nonnull
    public static Optional<Choice> bestFood(@Nonnull SpeciesDefinition species, @Nonnull Collection<String> available) {
        for (List<String> list : List.of(species.FavoriteTreats, species.PreferredFoods, species.BasicFoods)) {
            for (String item : list) {
                if (available.contains(item)) return Optional.of(new Choice(item, TamingRules.foodKind(species, item)));
            }
        }
        return Optional.empty();
    }

    /** True if any species eats this item; only food goes in a trough. */
    public static boolean isAnyFood(@Nonnull Collection<SpeciesDefinition> species, @Nonnull String item) {
        for (SpeciesDefinition s : species) {
            if (TamingRules.foodKind(s, item) != FoodKind.NONE) return true;
        }
        return false;
    }

    /** One in-game day in real time (day plus night seconds), within 5 to 60 minutes. */
    public static long mealIntervalMs(long dayPlusNightSeconds) {
        if (dayPlusNightSeconds <= 0) return DEFAULT_MEAL_INTERVAL_MS;
        return Math.clamp(dayPlusNightSeconds * 1000L, MIN_MEAL_INTERVAL_MS, MAX_MEAL_INTERVAL_MS);
    }

    public static boolean mealDue(long lastFedAtMs, long nowMs, long intervalMs) {
        return lastFedAtMs <= 0 || nowMs - lastFedAtMs >= intervalMs;
    }

    public static boolean fedRecently(long lastFedAtMs, long nowMs, long intervalMs) {
        return lastFedAtMs > 0 && nowMs - lastFedAtMs < FED_FOR_MEALS * intervalMs;
    }

    /** {@code lastPettedAtMs} is on the world's game clock (taming writes it so); petted within one in-game day. */
    public static boolean pettedRecently(long lastPettedGameMs, long nowGameMs) {
        return lastPettedGameMs > 0 && Math.abs(nowGameMs - lastPettedGameMs) < GAME_DAY_MS;
    }

    @Nonnull
    public static Breakdown target(@Nonnull Inputs in) {
        float fed = in.fedRecently() ? FED : 0f;
        boolean good = in.lastMeal() == FoodKind.PREFERRED || in.lastMeal() == FoodKind.FAVORITE;
        float preferred = in.fedRecently() && good ? PREFERRED : 0f;
        float house = in.hasHouse() ? HOUSE : 0f;
        float met = in.hasHouse() && in.habitatMet() ? HABITAT_MET : 0f;
        float score = in.hasHouse() ? HABITAT_SCORE * Math.clamp(in.habitatScore(), 0f, 1f) : 0f;
        float petted = in.pettedRecently() ? PETTED : 0f;
        float treat = in.treatRecently() ? TREAT : 0f;
        float sum = BASE + fed + preferred + house + met + score + petted + treat;
        return new Breakdown(BASE, fed, preferred, house, met, score, petted, treat, Math.clamp(sum, FLOOR, 1f));
    }

    /** Moves {@code current} toward {@code target} exponentially over {@code dtMs}; never below FLOOR. */
    public static float drift(float current, float target, long dtMs) {
        float from = Math.clamp(Float.isNaN(current) ? FLOOR : current, 0f, 1f);
        float to = Math.clamp(target, FLOOR, 1f);
        long dt = Math.clamp(dtMs, 0L, MAX_STEP_MS);
        double tau = to >= from ? RISE_TAU_MS : FALL_TAU_MS;
        double k = 1.0 - Math.exp(-dt / tau);
        float next = (float) (from + (to - from) * k);
        return Math.clamp(next, FLOOR, 1f);
    }
}
