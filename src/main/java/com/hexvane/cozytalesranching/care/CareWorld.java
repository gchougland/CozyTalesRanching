package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.AnimalFriendshipChangedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.taming.TamingRules;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * One world's care, run once a second on its world thread (queued by {@link CareSystem}, outside any tick).
 * <ul>
 *   <li>Animals: the roster (every owned animal whose record is in this world, owner online or not) is
 *       rebuilt once a minute and walked 1/60 per second, so each animal is looked at once a minute: it eats
 *       if a meal is due and a trough in range has food it likes, and its happiness drifts.</li>
 *   <li>Habitat: at most one house is scanned at a time, {@link #COLUMNS_PER_STEP} columns per second, only
 *       when its last scan is older than {@code HabitatScanIntervalSeconds} and its block is loaded. A house just
 *       placed goes first; one never scanned by its first morning is scanned whole by the morning hook.</li>
 *   <li>Troughs: every 5 s the ones whose block may not show their contents are fixed, every minute all are
 *       checked for being gone (their food is dropped).</li>
 * </ul>
 */
final class CareWorld {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    static final long ROSTER_MS = 60_000L;
    static final int STEPS_PER_ROSTER = 60;
    static final int COLUMNS_PER_STEP = 96;
    static final long TROUGH_SYNC_MS = 5_000L;
    static final long TROUGH_CHECK_ALL_MS = 60_000L;
    static final long HOUSE_PICK_MS = 5_000L;
    static final String EAT_ANIMATION = "EatTrough";
    /** Animals and troughs this far apart in height never meet. */
    static final double TROUGH_HEIGHT = 4.0;
    /** A change smaller than this is kept in memory and saved with the next real change. */
    static final float HAPPINESS_SAVE_STEP = 0.002f;

    private static final Map<String, CareWorld> WORLDS = new ConcurrentHashMap<>();

    private List<UUID> roster = List.of();
    private int rosterIndex;
    private long rosterBuiltAt = Long.MIN_VALUE / 2;
    private final Map<UUID, Long> happinessAt = new HashMap<>();
    /** Houses placed but not scanned yet, oldest first. World thread. */
    private final Set<UUID> placed = new LinkedHashSet<>();
    @Nullable private Job job;
    @Nullable private WorldHabitatGrid jobGrid;
    private long lastTroughSync;
    private long lastTroughCheckAll;
    private long lastHousePick;

    /** {@code placed}: the first scan of a house just placed. */
    private record Job(@Nonnull UUID houseId, @Nonnull HabitatScan scan, boolean placed) {}

    /** What one step knows about the world. */
    record Context(
        @Nonnull World world,
        @Nonnull CareService care,
        long nowMs,
        long gameNowMs,
        long mealIntervalMs,
        int troughRange,
        @Nonnull List<CareStore.TroughView> troughs
    ) {}

    @Nonnull
    static CareWorld of(@Nonnull World world) {
        return of(world.getName());
    }

    @Nonnull
    static CareWorld of(@Nonnull String worldName) {
        return WORLDS.computeIfAbsent(worldName, w -> new CareWorld());
    }

    @Nonnull
    static Context context(@Nonnull World world, @Nonnull CareService care, long nowMs) {
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        long gameNow = time == null ? 0L : time.getGameTime().toEpochMilli();
        long interval = CareRules.mealIntervalMs((long) world.getDaytimeDurationSeconds() + world.getNighttimeDurationSeconds());
        return new Context(world, care, nowMs, gameNow, interval, RanchConfig.get().troughRange(), care.store().troughsIn(world.getName()));
    }

    // ---- the step ----

    void step(@Nonnull World world, @Nonnull CareService care, long now) {
        Context ctx = context(world, care, now);
        if (now - rosterBuiltAt >= ROSTER_MS) {
            roster = roster(world);
            rosterIndex = 0;
            rosterBuiltAt = now;
            happinessAt.keySet().retainAll(new HashSet<>(roster));
        }
        int perStep = (roster.size() + STEPS_PER_ROSTER - 1) / STEPS_PER_ROSTER;
        for (int i = 0; i < perStep && rosterIndex < roster.size(); i++) careFor(roster.get(rosterIndex++), ctx);

        stepHabitat(world, care, now);

        if (now - lastTroughSync >= TROUGH_SYNC_MS) {
            boolean all = now - lastTroughCheckAll >= TROUGH_CHECK_ALL_MS;
            for (CareStore.TroughView t : ctx.troughs()) {
                if (all || t.stateDirty()) syncTrough(world, care, t.x(), t.y(), t.z());
            }
            lastTroughSync = now;
            if (all) lastTroughCheckAll = now;
        }
        care.scheduleSave();
    }

    /** Admin: every animal on the roster now, every house scanned at once, every trough checked. Returns animals cared for. */
    int runNow(@Nonnull World world, @Nonnull CareService care, long now) {
        roster = roster(world);
        rosterIndex = roster.size();
        rosterBuiltAt = now;
        Context ctx = context(world, care, now);
        for (UUID id : roster) careFor(id, ctx);
        job = null;
        for (HouseRecord h : Ranch.houses().inWorld(world.getName())) {
            HabitatScan scan = newScan(care, h);
            WorldHabitatGrid grid = new WorldHabitatGrid(world);
            while (!scan.done()) scan.step(grid, 4096);
            finishScan(care, world.getName(), h, scan, now);
        }
        for (CareStore.TroughView t : care.store().troughsIn(world.getName())) syncTrough(world, care, t.x(), t.y(), t.z());
        care.scheduleSave();
        return roster.size();
    }

    @Nonnull
    private static List<UUID> roster(@Nonnull World world) {
        List<UUID> ids = new ArrayList<>();
        for (AnimalRecord r : Ranch.animals().inWorld(world.getName())) {
            if (r.ownerId != null) ids.add(r.animalId);
        }
        return ids;
    }

    // ---- one animal ----

    private void careFor(@Nonnull UUID animalId, @Nonnull Context ctx) {
        AnimalRecord record = Ranch.animals().get(animalId).orElse(null);
        if (record == null || record.ownerId == null || record.state == AnimalRecord.State.MISSING) return;
        if (!ctx.world().getName().equals(record.worldName)) return;
        SpeciesDefinition species = SpeciesRegistry.byId(record.speciesId).orElse(null);
        if (species == null) return;
        try {
            Store<EntityStore> store = ctx.world().getEntityStore().getStore();
            Ref<EntityStore> entity = entity(ctx.world(), record);
            Vector3d pos = position(record, entity, store);
            boolean changed = Feeding.expire(record, ctx.nowMs());
            if (CareRules.mealDue(record.lastFedAtMs, ctx.nowMs(), ctx.mealIntervalMs())) changed |= feed(record, species, pos, entity, ctx);
            Long last = happinessAt.put(record.animalId, ctx.nowMs());
            long dt = last == null ? ROSTER_MS : ctx.nowMs() - last;
            float target = CareRules.target(inputs(record, ctx)).target();
            CareTraits traits = Feeding.traits(record, ctx.care().habitat());
            float next = CareRules.drift(record.happiness, target, dt, traits.declineMultiplier(), traits.floor());
            if (Math.abs(next - record.happiness) >= HAPPINESS_SAVE_STEP) changed = true;
            record.happiness = next;
            if (changed) Ranch.animals().markDirty(record);
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Care failed for ranch animal %s", record.animalId);
        }
    }

    /**
     * Eats the best meal it likes from any trough in range (advanced feed first when no bonus runs). The item is used up
     * unless an Efficient Eater's roll spares it. Returns true if it ate.
     */
    private static boolean feed(@Nonnull AnimalRecord record, @Nonnull SpeciesDefinition species, @Nonnull Vector3d pos,
                                @Nullable Ref<EntityStore> entity, @Nonnull Context ctx) {
        CareService care = ctx.care();
        HabitatConfig config = care.habitat();
        Map<String, String> feeds = config.feedItems();
        boolean bonus = Feeding.active(record, null, ctx.nowMs());
        CareStore.TroughView bestTrough = null;
        CareRules.Meal best = null;
        double bestDist = Double.MAX_VALUE;
        for (CareStore.TroughView t : ctx.troughs()) {
            if (t.contents().isEmpty()) continue;
            double dist = reach(pos, t, ctx.troughRange());
            if (dist < 0) continue;
            Set<String> items = new HashSet<>();
            for (TroughContents.Slot s : t.contents().slots) items.add(s.item);
            CareRules.Meal m = CareRules.bestMeal(species, items, feeds, bonus).orElse(null);
            if (m == null) continue;
            int r = CareRules.mealRank(m, bonus);
            int bestR = best == null ? -1 : CareRules.mealRank(best, bonus);
            if (r > bestR || (r == bestR && dist < bestDist)) {
                best = m;
                bestTrough = t;
                bestDist = dist;
            }
        }
        if (best == null) return false;
        String w = ctx.world().getName();
        CareTraits traits = Feeding.traits(record, config);
        boolean use = CareRules.usesItem(traits.foodUse(), ThreadLocalRandom.current().nextDouble());
        if (use && !care.store().takeOne(w, bestTrough.x(), bestTrough.y(), bestTrough.z(), best.item())) return false;
        record.lastFedAtMs = ctx.nowMs();
        care.store().recordMeal(record.animalId, w, best.kind(), ctx.nowMs());
        if (best.feedBonus() != null) {
            Feeding.apply(record, best.item(), config, ctx.nowMs());
            LOGGER.atInfo().log("Care: ranch animal %s ate %s from the trough at %d %d %d: bonus %s until %d",
                record.animalId, best.item(), bestTrough.x(), bestTrough.y(), bestTrough.z(), record.feedBonus, record.feedBonusUntilMs);
        }
        if (best.kind() == FoodKind.FAVORITE) {
            float gain = (float) (CareRules.TROUGH_TREAT_FRIENDSHIP * traits.treatMultiplier()) * Feeding.friendshipMultiplier(record, config, ctx.nowMs());
            addFriendship(record, gain);
        }
        if (care.store().trough(w, bestTrough.x(), bestTrough.y(), bestTrough.z()).map(v -> v.contents().isEmpty()).orElse(true)) {
            syncTrough(ctx.world(), care, bestTrough.x(), bestTrough.y(), bestTrough.z());
        }
        if (entity != null) playEat(entity, ctx.world().getEntityStore().getStore());
        return true;
    }

    /** Horizontal distance to the trough's centre, or -1 when out of range. */
    static double reach(@Nonnull Vector3d pos, @Nonnull CareStore.TroughView t, int range) {
        double dx = pos.x - (t.x() + 0.5);
        double dz = pos.z - (t.z() + 0.5);
        double dy = pos.y - t.y();
        double d = Math.sqrt(dx * dx + dz * dz);
        return d <= range && Math.abs(dy) <= TROUGH_HEIGHT ? d : -1;
    }

    static void addFriendship(@Nonnull AnimalRecord record, float amount) {
        int before = record.hearts;
        TamingRules.Friendship next = TamingRules.addFriendship(record.hearts, record.heartProgress, amount);
        record.hearts = next.hearts();
        record.heartProgress = next.progress();
        Ranch.animals().markDirty(record);
        if (next.hearts() != before) RanchEvents.fire(new AnimalFriendshipChangedEvent(record.view(), before, next.hearts()));
    }

    private static void playEat(@Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        ModelComponent mc = store.getComponent(animal, ModelComponent.getComponentType());
        Model model = mc != null ? mc.getModel() : null;
        if (npc != null && model != null && model.getAnimationSetMap().containsKey(EAT_ANIMATION)) {
            npc.playAnimation(animal, AnimationSlot.Emote, EAT_ANIMATION, store);
        }
    }

    @Nullable
    private static Ref<EntityStore> entity(@Nonnull World world, @Nonnull AnimalRecord record) {
        if (record.state != AnimalRecord.State.ACTIVE || record.entityUuid == null) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(record.entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    /** The live entity's position, else for an animal asleep at home its house door, else where it was last seen. */
    @Nonnull
    private static Vector3d position(@Nonnull AnimalRecord record, @Nullable Ref<EntityStore> entity, @Nonnull Store<EntityStore> store) {
        if (entity != null) {
            TransformComponent t = store.getComponent(entity, TransformComponent.getComponentType());
            if (t != null) return new Vector3d(t.getPosition());
        }
        if (record.state == AnimalRecord.State.STORED && record.houseId != null) {
            Optional<HouseRecord> h = Ranch.houses().get(record.houseId);
            if (h.isPresent()) return new Vector3d(h.get().doorX, h.get().doorY, h.get().doorZ);
        }
        return new Vector3d(record.lastX, record.lastY, record.lastZ);
    }

    /** What the happiness target of this animal is built from, now. */
    @Nonnull
    static CareRules.Inputs inputs(@Nonnull AnimalRecord record, @Nonnull Context ctx) {
        return inputs(record, ctx.care(), ctx.nowMs(), ctx.gameNowMs(), ctx.mealIntervalMs());
    }

    @Nonnull
    static CareRules.Inputs inputs(@Nonnull AnimalRecord record, @Nonnull CareService care, long nowMs, long gameNowMs, long mealIntervalMs) {
        Optional<CareStore.MealView> meal = care.store().meal(record.animalId);
        boolean fed = CareRules.fedRecently(record.lastFedAtMs, nowMs, mealIntervalMs);
        // a meal from the hand (taming writes lastFedAtMs without a kind) counts as basic food
        FoodKind kind = meal.filter(m -> m.atMs() >= record.lastFedAtMs - 5_000L).map(CareStore.MealView::kind).orElse(FoodKind.BASIC);
        boolean treat = meal.map(m -> m.treatAtMs() > 0 && nowMs - m.treatAtMs() < CareRules.FED_FOR_MEALS * mealIntervalMs).orElse(false);
        HouseRecord house = record.houseId == null ? null : Ranch.houses().get(record.houseId).orElse(null);
        return new CareRules.Inputs(
            fed,
            kind,
            treat,
            house != null,
            house != null && house.habitatRequirementsMet,
            house == null ? 0f : house.habitatScore,
            CareRules.pettedRecently(record.lastPettedAtMs, gameNowMs),
            Feeding.active(record, CareRules.BONUS_HEARTY, nowMs),
            Feeding.traits(record, care.habitat()).treatMultiplier()
        );
    }

    // ---- habitat ----

    /** A house just placed: its first scan starts at the next step, ahead of every other house. World thread. */
    void housePlaced(@Nonnull UUID houseId) {
        placed.add(houseId);
    }

    private void stepHabitat(@Nonnull World world, @Nonnull CareService care, long now) {
        String w = world.getName();
        if (job == null) startPlacedScan(world, care);
        if (job == null) {
            if (now - lastHousePick < HOUSE_PICK_MS) return;
            lastHousePick = now;
            long intervalMs = (long) (RanchConfig.get().habitatScanIntervalSeconds() * 1000);
            for (HouseRecord h : Ranch.houses().inWorld(w)) {
                if (now - h.habitatScannedAtMs < intervalMs) continue;
                if (TroughBlocks.typeAt(world, h.x, h.y, h.z) == null) continue;
                job = new Job(h.houseId, newScan(care, h), false);
                jobGrid = new WorldHabitatGrid(world);
                break;
            }
            if (job == null) return;
        }
        HouseRecord house = Ranch.houses().get(job.houseId()).orElse(null);
        if (house == null || jobGrid == null) {
            job = null;
            return;
        }
        job.scan().step(jobGrid, COLUMNS_PER_STEP);
        if (!job.scan().done()) return;
        finishScan(care, w, house, job.scan(), now);
        if (job.placed()) {
            LOGGER.atInfo().log("Care: first habitat scan of new house %s: requirements met %s, score %.2f, %d columns scanned",
                house.houseId, house.habitatRequirementsMet, house.habitatScore, job.scan().columnsScanned());
        }
        job = null;
        jobGrid = null;
    }

    /** Starts the scan of the first placed house still waiting whose block is loaded; forgets houses that are gone or scanned. */
    private void startPlacedScan(@Nonnull World world, @Nonnull CareService care) {
        for (UUID id : List.copyOf(placed)) {
            HouseRecord h = Ranch.houses().get(id).orElse(null);
            if (h == null || !world.getName().equals(h.worldName) || h.habitatScannedAtMs > 0) {
                placed.remove(id);
                continue;
            }
            if (TroughBlocks.typeAt(world, h.x, h.y, h.z) == null) continue;
            placed.remove(id);
            job = new Job(h.houseId, newScan(care, h), true);
            jobGrid = new WorldHabitatGrid(world);
            return;
        }
    }

    /**
     * Scans a house completely, now, if it was never scanned (the morning hook, before breeding decides). Columns not in
     * memory are skipped as always; if all are, the house still counts as scanned with its requirements unmet.
     * Returns true if it scanned. World thread.
     */
    boolean scanIfNeverScanned(@Nonnull World world, @Nonnull CareService care, @Nonnull HouseRecord house, long now) {
        if (house.habitatScannedAtMs > 0) return false;
        if (job != null && job.houseId().equals(house.houseId)) {
            job = null;
            jobGrid = null;
        }
        placed.remove(house.houseId);
        HabitatScan scan = newScan(care, house);
        WorldHabitatGrid grid = new WorldHabitatGrid(world);
        while (!scan.done()) scan.step(grid, 4096);
        finishScan(care, world.getName(), house, scan, now);
        return true;
    }

    @Nonnull
    private static HabitatScan newScan(@Nonnull CareService care, @Nonnull HouseRecord h) {
        return new HabitatScan(care.habitat(), h.x, h.y, h.z, RanchConfig.get().habitatScanRadius());
    }

    private static void finishScan(@Nonnull CareService care, @Nonnull String world, @Nonnull HouseRecord house, @Nonnull HabitatScan scan, long now) {
        SpeciesDefinition species = SpeciesRegistry.byId(house.speciesId).orElse(null);
        boolean food = species != null && troughFoodNear(care, world, house, species);
        scan.applyTo(house, species, food, now);
        Ranch.houses().markDirty(house);
    }

    /** A trough within the scan radius of the house holds something the species eats. */
    static boolean troughFoodNear(@Nonnull CareService care, @Nonnull String world, @Nonnull HouseRecord house, @Nonnull SpeciesDefinition species) {
        Vector3d at = new Vector3d(house.x + 0.5, house.y, house.z + 0.5);
        int radius = RanchConfig.get().habitatScanRadius();
        for (CareStore.TroughView t : care.store().troughsIn(world)) {
            if (reach(at, t, radius) < 0) continue;
            for (TroughContents.Slot s : t.contents().slots) {
                if (s.count > 0 && (TamingRules.foodKind(species, s.item) != FoodKind.NONE || care.habitat().feedBonus(s.item) != null)) return true;
            }
        }
        return false;
    }

    // ---- troughs ----

    /** Makes the block show the contents; a trough that is gone drops its food and is forgotten. World thread. */
    static void syncTrough(@Nonnull World world, @Nonnull CareService care, int x, int y, int z) {
        String w = world.getName();
        Optional<CareStore.TroughView> t = care.store().trough(w, x, y, z);
        if (t.isEmpty()) return;
        switch (TroughBlocks.presence(world, x, y, z)) {
            case UNLOADED -> { }
            case GONE -> {
                List<TroughContents.Slot> food = care.store().remove(w, x, y, z);
                if (!food.isEmpty()) TroughBlocks.drop(world.getEntityStore().getStore(), x, y, z, food);
                care.scheduleSave();
            }
            case TROUGH -> {
                if (TroughBlocks.showState(world, x, y, z, !t.get().contents().isEmpty())) care.store().stateShown(w, x, y, z);
            }
        }
    }
}
