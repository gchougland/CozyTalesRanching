package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.taming.TamingRules;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    /**
     * What the happiness target is built from. {@code heartyActive}: Hearty Feed keeps the food part satisfied while it
     * lasts. {@code treatMultiplier}: the Gourmet trait's {@code TreatBonusMultiplier} on the treat part.
     */
    public record Inputs(
        boolean fedRecently,
        @Nonnull FoodKind lastMeal,
        boolean treatRecently,
        boolean hasHouse,
        boolean habitatMet,
        float habitatScore,
        boolean pettedRecently,
        boolean heartyActive,
        double treatMultiplier
    ) {
        public Inputs(boolean fedRecently, @Nonnull FoodKind lastMeal, boolean treatRecently, boolean hasHouse, boolean habitatMet,
                      float habitatScore, boolean pettedRecently) {
            this(fedRecently, lastMeal, treatRecently, hasHouse, habitatMet, habitatScore, pettedRecently, false, 1.0);
        }
    }

    /** A meal from a trough: a species food, or an advanced feed ({@code feedBonus} set, eaten as preferred food). */
    public record Meal(@Nonnull String item, @Nonnull FoodKind kind, @Nullable String feedBonus) {}

    /** Advanced feed bonus ids ({@code AnimalRecord.feedBonus}). */
    public static final String BONUS_HEARTY = "hearty";
    public static final String BONUS_SWEET = "sweet";
    public static final String BONUS_BREEDERS = "breeders";
    public static final String BONUS_PREMIUM = "premium";

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

    /**
     * The meal an animal takes from what a trough holds. With no feed bonus running, an advanced feed comes first (the
     * player put it there for its effect); then favourite treat, preferred, basic; a feed while a bonus runs comes last.
     * {@code feeds} maps feed item ids to bonus ids.
     */
    @Nonnull
    public static Optional<Meal> bestMeal(@Nonnull SpeciesDefinition species, @Nonnull Collection<String> available,
                                          @Nonnull Map<String, String> feeds, boolean bonusActive) {
        Meal feed = null;
        for (Map.Entry<String, String> f : feeds.entrySet()) {
            if (available.contains(f.getKey())) {
                feed = new Meal(f.getKey(), FoodKind.PREFERRED, f.getValue());
                break;
            }
        }
        if (feed != null && !bonusActive) return Optional.of(feed);
        Optional<Choice> food = bestFood(species, available);
        if (food.isPresent()) return Optional.of(new Meal(food.get().item(), food.get().kind(), null));
        return Optional.ofNullable(feed);
    }

    /** Orders meals across troughs the same way {@link #bestMeal} orders them within one. Higher is better. */
    public static int mealRank(@Nonnull Meal meal, boolean bonusActive) {
        if (meal.feedBonus() != null) return bonusActive ? 1 : 10;
        return switch (meal.kind()) {
            case NONE -> 0;
            case BASIC -> 2;
            case PREFERRED -> 3;
            case FAVORITE -> 4;
        };
    }

    public static boolean bonusActive(@Nullable String bonus, long untilMs, long nowMs) {
        return bonus != null && nowMs < untilMs;
    }

    /** Efficient Eater: a trough meal uses up its item only when {@code roll} (0..1) is below {@code foodUse}. */
    public static boolean usesItem(double foodUse, double roll) {
        return roll < Math.clamp(foodUse, 0.0, 1.0);
    }

    /** Friendship multiplier from traits (Affectionate) and Sweet Feed while it runs. */
    public static float friendshipMultiplier(double traitMultiplier, boolean sweetActive, float sweetMultiplier) {
        double m = Math.max(0.0, traitMultiplier) * (sweetActive ? Math.max(1f, sweetMultiplier) : 1f);
        return (float) m;
    }

    /**
     * Breeder's Feed: the new {@code lastBredAtMs}, moved back by {@code cut} of the cooldown but never further than
     * "cooled down now". An animal that never bred, or has already cooled down, is unchanged.
     */
    public static long breedersCut(long lastBredAtMs, long nowMs, long cooldownMs, double cut) {
        if (lastBredAtMs <= 0 || cooldownMs <= 0) return lastBredAtMs;
        long ready = nowMs - cooldownMs;
        if (lastBredAtMs <= ready) return lastBredAtMs;
        long moved = lastBredAtMs - Math.round(Math.clamp(cut, 0.0, 1.0) * cooldownMs);
        return Math.max(Math.max(ready, 1L), moved);
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
        boolean fedNow = in.fedRecently() || in.heartyActive();
        float fed = fedNow ? FED : 0f;
        boolean good = in.lastMeal() == FoodKind.PREFERRED || in.lastMeal() == FoodKind.FAVORITE;
        float preferred = fedNow && good ? PREFERRED : 0f;
        float house = in.hasHouse() ? HOUSE : 0f;
        float met = in.hasHouse() && in.habitatMet() ? HABITAT_MET : 0f;
        float score = in.hasHouse() ? HABITAT_SCORE * Math.clamp(in.habitatScore(), 0f, 1f) : 0f;
        float petted = in.pettedRecently() ? PETTED : 0f;
        float treat = in.treatRecently() ? (float) (TREAT * Math.max(0.0, in.treatMultiplier())) : 0f;
        float sum = BASE + fed + preferred + house + met + score + petted + treat;
        return new Breakdown(BASE, fed, preferred, house, met, score, petted, treat, Math.clamp(sum, FLOOR, 1f));
    }

    /** Moves {@code current} toward {@code target} exponentially over {@code dtMs}; never below FLOOR. */
    public static float drift(float current, float target, long dtMs) {
        return drift(current, target, dtMs, 1.0, FLOOR);
    }

    /**
     * As {@link #drift(float, float, long)}, with the Hardy trait: falling is {@code declineMultiplier} times as fast
     * (0.5 takes twice as long) and happiness never drifts below {@code floor}.
     */
    public static float drift(float current, float target, long dtMs, double declineMultiplier, float floor) {
        float low = Math.clamp(floor, FLOOR, 1f);
        float from = Math.clamp(Float.isNaN(current) ? low : current, 0f, 1f);
        float to = Math.clamp(target, low, 1f);
        long dt = Math.clamp(dtMs, 0L, MAX_STEP_MS);
        double decline = declineMultiplier > 0 ? declineMultiplier : 1.0;
        double tau = to >= from ? RISE_TAU_MS : FALL_TAU_MS / decline;
        double k = 1.0 - Math.exp(-dt / tau);
        float next = (float) (from + (to - from) * k);
        return Math.clamp(next, low, 1f);
    }
}
