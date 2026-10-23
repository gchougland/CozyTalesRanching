package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * What other roles (the house page) may read from care, and one way to tell it about a meal. Trough reads
 * are safe from any thread and return copies; {@link #happinessBreakdown} reads the world clock, so call it on
 * the record's world thread.
 */
public final class CareApi {
    /** One stack in a trough. */
    public record Stack(@Nonnull String itemId, int count) {}

    /** A trough with food (or just emptied) at its origin block. */
    public record Trough(@Nonnull String world, int x, int y, int z, @Nonnull List<Stack> stacks) {}

    private CareApi() {}

    /** The stacks in the trough whose origin block is here; empty when it holds nothing or is unknown. */
    @Nonnull
    public static List<Stack> troughContents(@Nonnull String world, int x, int y, int z) {
        CareService care = CareService.get();
        if (care == null) return List.of();
        return care.store().trough(world, x, y, z).map(t -> stacks(t.contents())).orElse(List.of());
    }

    /** Troughs holding food within {@code radius} blocks (horizontally, 4 up or down) of a point. */
    @Nonnull
    public static List<Trough> troughsNear(@Nonnull String world, double x, double y, double z, int radius) {
        CareService care = CareService.get();
        if (care == null) return List.of();
        List<Trough> out = new ArrayList<>();
        Vector3d at = new Vector3d(x, y, z);
        for (CareStore.TroughView t : care.store().troughsIn(world)) {
            if (t.contents().isEmpty() || CareWorld.reach(at, t, radius) < 0) continue;
            out.add(new Trough(t.world(), t.x(), t.y(), t.z(), stacks(t.contents())));
        }
        return out;
    }

    /**
     * Each part of the animal's happiness target now ({@link CareRules.Breakdown}; the parts and their weights
     * are in {@code notes/care.md}). The current value is {@code record.happiness}; it drifts toward
     * {@code target} over minutes. Petting is judged on the game clock of the record's world when it is loaded.
     */
    @Nonnull
    public static CareRules.Breakdown happinessBreakdown(@Nonnull AnimalRecord record) {
        CareService care = CareService.get();
        long now = System.currentTimeMillis();
        long gameNow = 0L;
        long interval = CareRules.DEFAULT_MEAL_INTERVAL_MS;
        Universe universe = Universe.get();
        World world = universe == null ? null : universe.getWorld(record.worldName);
        if (world != null) {
            interval = CareRules.mealIntervalMs((long) world.getDaytimeDurationSeconds() + world.getNighttimeDurationSeconds());
            WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
            if (time != null) gameNow = time.getGameTime().toEpochMilli();
        }
        if (care == null) {
            return CareRules.target(new CareRules.Inputs(false, FoodKind.NONE, false, false, false, 0f, false));
        }
        return CareRules.target(CareWorld.inputs(record, care, now, gameNow, interval));
    }

    /** The last trough meal's kind, if the animal has eaten from a trough. */
    @Nonnull
    public static Optional<FoodKind> lastTroughMeal(@Nonnull UUID animalId) {
        CareService care = CareService.get();
        if (care == null) return Optional.empty();
        return care.store().meal(animalId).map(CareStore.MealView::kind);
    }

    /**
     * Tells care what kind of food an animal just ate by other means (hand-feeding), so happiness counts a
     * preferred meal or treat. The caller still sets {@code lastFedAtMs} itself. World thread.
     */
    public static void recordMeal(@Nonnull AnimalRecord record, @Nonnull FoodKind kind) {
        CareService care = CareService.get();
        if (care == null || kind == FoodKind.NONE) return;
        care.store().recordMeal(record.animalId, record.worldName, kind, System.currentTimeMillis());
        care.scheduleSave();
    }

    /**
     * What friendship gains of this animal are multiplied by: its traits' {@code FriendshipGainMultiplier} (Affectionate,
     * 1.25) times {@code SweetFriendshipMultiplier} (1.5) while Sweet Feed runs. 1 for a plain animal. For petting and
     * hand feeding in {@code taming}: {@code amount *= CareApi.friendshipMultiplier(record)}.
     */
    public static float friendshipMultiplier(@Nonnull AnimalRecord record) {
        CareService care = CareService.get();
        if (care == null) return 1f;
        return Feeding.friendshipMultiplier(record, care.habitat(), System.currentTimeMillis());
    }

    /** The advanced feed bonus running now ({@code hearty}, {@code sweet}, {@code breeders}, {@code premium}), if any. */
    @Nonnull
    public static Optional<String> activeFeedBonus(@Nonnull AnimalRecord record) {
        long now = System.currentTimeMillis();
        return CareRules.bonusActive(record.feedBonus, record.feedBonusUntilMs, now) ? Optional.of(record.feedBonus) : Optional.empty();
    }

    /** True while this bonus runs, e.g. {@code hasFeedBonus(record, "premium")} for production. */
    public static boolean hasFeedBonus(@Nonnull AnimalRecord record, @Nonnull String bonus) {
        return bonus.equals(activeFeedBonus(record).orElse(null));
    }

    /** The bonus id of an advanced feed item, or empty when the item is not one. */
    @Nonnull
    public static Optional<String> feedBonusOf(@Nonnull String itemId) {
        CareService care = CareService.get();
        return care == null ? Optional.empty() : Optional.ofNullable(care.habitat().feedBonus(itemId));
    }

    /** The toy feature ids (habitat preferences, e.g. {@code toy_ball}) a species likes, from {@code Habitat.json}. */
    @Nonnull
    public static List<String> toysLiked(@Nonnull String speciesId) {
        CareService care = CareService.get();
        return care == null ? List.of() : List.copyOf(care.habitat().SpeciesToys.getOrDefault(speciesId, List.of()));
    }

    /** How far animals reach troughs, in blocks ({@code TroughRange}). */
    public static int troughRange() {
        return RanchConfig.get().troughRange();
    }

    @Nonnull
    private static List<Stack> stacks(@Nonnull TroughContents c) {
        List<Stack> out = new ArrayList<>();
        for (TroughContents.Slot s : c.slots) if (s.count > 0) out.add(new Stack(s.item, s.count));
        return out;
    }
}
