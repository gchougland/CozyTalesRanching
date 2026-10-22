package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseRules.Phase;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * The houses' day, once a second per world that has houses, on the world thread (a queued task, never inside
 * the tick). Walks the world's houses and their residents through the registries, never all entities, and
 * skips houses whose door chunk is not loaded (they wait; nothing is loaded).
 * <ul>
 *   <li>Day: the morning (hooks, then every STORED resident comes out at the door), then the home range:
 *       a resident that is not following wanders around the door and walks back when it strays.</li>
 *   <li>Night: residents walk to the door and go in; one that has not arrived within a minute goes in
 *       anyway, and one whose entity is not loaded goes in without walking. Then the nightfall hooks, once.</li>
 *   <li>Every 10 seconds: a house whose block is gone (broken by something that fired no event) is removed.</li>
 * </ul>
 */
final class HouseDaySystem extends TickingSystem<EntityStore> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final float PASS_SECONDS = 1f;
    private static final int BLOCK_CHECK_PASSES = 10;

    /** Debug override of the day phase per world, cleared once the real phase matches it. */
    private static final Map<String, Phase> FORCED = new ConcurrentHashMap<>();
    /** Houses whose morning runs on the next day pass even if today's already ran (debug). */
    private static final Set<UUID> FORCED_MORNING = ConcurrentHashMap.newKeySet();
    /** When each resident started walking home tonight. */
    private static final Map<UUID, Long> WALKING_SINCE = new ConcurrentHashMap<>();

    private final PersistentHouseRegistry houses;
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();
    private final Map<String, Integer> passes = new ConcurrentHashMap<>();

    HouseDaySystem(@Nonnull PersistentHouseRegistry houses) {
        this.houses = houses;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total < PASS_SECONDS) return;
        elapsed.put(world.getName(), 0f);
        if (houses.inWorld(world.getName()).isEmpty()) return;
        world.execute(() -> pass(world));
    }

    static void forceNight(@Nonnull String worldName) {
        FORCED.put(worldName, Phase.NIGHT);
    }

    static void forceMorning(@Nonnull String worldName, @Nonnull List<HouseRecord> inWorld) {
        FORCED.put(worldName, Phase.DAY);
        for (HouseRecord h : inWorld) FORCED_MORNING.add(h.houseId);
    }

    /** World thread only. */
    void pass(@Nonnull World world) {
        if (!world.isAlive()) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) return;
        double progress = time.getDayProgress();
        long cycleDay = HouseRules.cycleDay(time.getGameDateTime().toLocalDate().toEpochDay(), progress);
        Phase natural = HouseRules.phase(progress);
        Phase forced = FORCED.get(world.getName());
        if (forced == natural) FORCED.remove(world.getName(), forced);
        Phase phase = forced != null ? forced : natural;

        boolean checkBlocks = passes.merge(world.getName(), 1, Integer::sum) % BLOCK_CHECK_PASSES == 0;
        SectionReader reader = checkBlocks ? new SectionReader(world.getChunkStore()) : null;
        long now = System.currentTimeMillis();
        for (HouseRecord house : houses.inWorld(world.getName())) {
            try {
                if (!HouseBlocks.isLoaded(world, house.doorX, house.doorZ) || !HouseBlocks.isLoaded(world, house.x, house.z)) continue;
                if (reader != null && isBlockGone(reader, house)) {
                    HousePlacement.houseBroken(world, store, house, null);
                    continue;
                }
                if (phase == Phase.DAY) day(world, store, house, cycleDay);
                else night(world, store, house, cycleDay, now);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "House pass failed for " + house.houseId, e);
            }
        }
    }

    private static boolean isBlockGone(@Nonnull SectionReader reader, @Nonnull HouseRecord house) {
        if (!reader.hasStorageInMemory(house.x, house.y, house.z)) return false;
        int expected = BlockType.getAssetMap().getIndex(house.blockId);
        if (expected < 0 || expected == BlockType.UNKNOWN_ID) return false;
        return reader.getBlock(house.x, house.y, house.z) != expected;
    }

    private void day(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house, long cycleDay) {
        boolean forcedMorning = FORCED_MORNING.remove(house.houseId);
        if (forcedMorning || HouseRules.morningDue(house.lastMorningDay, cycleDay)) {
            HouseDayHooks.fireMorning(world, house, Ranch.animals().inHouse(house.houseId));
            house.lastMorningDay = Math.max(house.lastMorningDay, cycleDay);
            houses.markDirty(house);
            HouseResidents.log("House " + house.houseId + " morning (day " + cycleDay + ")");
        }
        double range = RanchConfig.get().homeRangeRadius();
        for (AnimalRecord r : Ranch.animals().inHouse(house.houseId)) {
            WALKING_SINCE.remove(r.animalId);
            if (r.state == AnimalRecord.State.STORED) {
                if (HouseResidents.bringOut(world, store, house, r)) {
                    HouseResidents.log("Ranch animal " + r.animalId + " came out of house " + house.houseId + " (generation " + r.generation + ")");
                }
                continue;
            }
            if (r.state != AnimalRecord.State.ACTIVE || r.following) continue;
            Ref<EntityStore> ref = HouseResidents.live(world, r);
            if (ref == null) continue;
            keepInRange(store, ref, house, r, range);
        }
    }

    /** Idle wanders around the door; a resident beyond the home range walks back, then idles again halfway in. Stay is the player's order and is left alone. */
    private static void keepInRange(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull HouseRecord house, @Nonnull AnimalRecord r, double range) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        HouseResidents.rememberRole(r.animalId, npc);
        if (!HouseResidents.hasHomeStates(npc)) return;
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (states == null || t == null) return;
        String state = HouseResidents.stateName(states);
        if (!state.equals(HouseResidents.STATE_IDLE) && !state.equals(HouseResidents.STATE_GO_HOME)) return;
        HouseResidents.leashToDoor(npc, house);
        double d = horizontal(t.getPosition(), house);
        if (state.equals(HouseResidents.STATE_IDLE) && d > range) {
            HouseResidents.setState(ref, store, HouseResidents.STATE_GO_HOME);
        } else if (state.equals(HouseResidents.STATE_GO_HOME) && d <= Math.max(2.0, range / 2)) {
            HouseResidents.setState(ref, store, HouseResidents.STATE_IDLE);
        }
    }

    private void night(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house, long cycleDay, long now) {
        List<AnimalRecord> residents = Ranch.animals().inHouse(house.houseId);
        boolean someoneOut = false;
        for (AnimalRecord r : residents) {
            switch (r.state) {
                case STORED -> {
                    Ref<EntityStore> stray = HouseResidents.live(world, r);
                    if (stray != null) store.removeEntity(stray, com.hypixel.hytale.component.RemoveReason.REMOVE);
                }
                case MISSING -> HouseResidents.storeInside(store, house, r, null);
                case ACTIVE -> {
                    if (r.following) continue;
                    if (!goingHome(world, store, house, r, now)) someoneOut = true;
                }
            }
        }
        if (!someoneOut && houses.nightfallDay(house.houseId) != cycleDay) {
            HouseDayHooks.fireNightfall(world, house, Ranch.animals().inHouse(house.houseId));
            houses.setNightfallDay(house, cycleDay);
            HouseResidents.log("House " + house.houseId + " nightfall (day " + cycleDay + ")");
        }
    }

    /** Walks one resident to the door; true once it is inside. */
    private static boolean goingHome(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house, @Nonnull AnimalRecord r, long now) {
        Ref<EntityStore> ref = HouseResidents.live(world, r);
        if (ref == null) {
            HouseResidents.storeInside(store, house, r, null);
            HouseResidents.log("Ranch animal " + r.animalId + " went into house " + house.houseId + " while unloaded");
            return true;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (npc == null || t == null) return false;
        HouseResidents.rememberRole(r.animalId, npc);
        if (npc.getRole() != null && npc.getRole().isRoleChangeRequested()) return false;
        Vector3d p = t.getPosition();
        long since = WALKING_SINCE.computeIfAbsent(r.animalId, id -> now);
        boolean arrived = HouseRules.arrived(p.x - house.doorX, p.y - house.doorY, p.z - house.doorZ);
        if (arrived || !HouseResidents.hasHomeStates(npc) || HouseRules.walkTimedOut(since, now)) {
            WALKING_SINCE.remove(r.animalId);
            HouseResidents.storeInside(store, house, r, ref);
            HouseResidents.log("Ranch animal " + r.animalId + " went into house " + house.houseId + (arrived ? " at the door" : " (did not reach the door in time)"));
            return true;
        }
        HouseResidents.leashToDoor(npc, house);
        HouseResidents.setState(ref, store, HouseResidents.STATE_GO_HOME);
        return false;
    }

    private static double horizontal(@Nonnull Vector3d p, @Nonnull HouseRecord house) {
        double dx = p.x - house.doorX, dz = p.z - house.doorZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void forget(@Nullable UUID animalId) {
        if (animalId != null) WALKING_SINCE.remove(animalId);
    }
}
