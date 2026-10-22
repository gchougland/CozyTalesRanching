package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import com.hypixel.hytale.server.spawning.SpawningContext;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Resident entities: walking home, going in (record STORED, then the entity removed) and coming out at the
 * door (spawn, bind, ACTIVE). World thread, outside system ticks (world tasks and commands).
 */
final class HouseResidents {
    static final String STATE_IDLE = "Idle";
    static final String STATE_STAY = "Stay";
    static final String STATE_GO_HOME = "GoHome";

    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    /** Last role seen on each resident's entity, for residents stored while unloaded. Memory only. */
    private static final Map<UUID, String> LAST_ROLE = new ConcurrentHashMap<>();

    private HouseResidents() {}

    /** The animal's current entity in this world, if loaded. */
    @Nullable
    static Ref<EntityStore> live(@Nonnull World world, @Nonnull AnimalRecord r) {
        if (r.entityUuid == null) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(r.entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    /** Our ranch roles have the Idle, Stay and GoHome states; adopted vanilla roles do not. */
    static boolean hasHomeStates(@Nullable NPCEntity npc) {
        if (npc == null || npc.getRoleName() == null) return false;
        return SpeciesRegistry.byRole(npc.getRoleName())
            .map(m -> m.kind() == RoleKind.RANCH || m.kind() == RoleKind.RANCH_BABY)
            .orElse(false);
    }

    static void rememberRole(@Nonnull UUID animalId, @Nullable NPCEntity npc) {
        if (npc != null && npc.getRoleName() != null) LAST_ROLE.put(animalId, npc.getRoleName());
    }

    /** Points the animal's home (leash point) at the door. Cheap when it already is. */
    static void leashToDoor(@Nonnull NPCEntity npc, @Nonnull HouseRecord house) {
        Vector3d leash = npc.getLeashPoint();
        double dx = leash.x - house.doorX, dy = leash.y - house.doorY, dz = leash.z - house.doorZ;
        if (dx * dx + dy * dy + dz * dz > 0.25) npc.setLeashPoint(HouseBlocks.door(house));
    }

    /** Sets a state our roles define, unless the role is about to change. */
    static void setState(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String state) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        if (npc == null || states == null || npc.getRole() == null || npc.getRole().isRoleChangeRequested()) return;
        if (state.equals(stateName(states))) return;
        if (!STATE_GO_HOME.equals(state)) {
            MarkedEntitySupport marked = store.getComponent(ref, MarkedEntitySupport.getComponentType());
            if (marked != null) marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, null);
        }
        states.setState(ref, state, null, store);
    }

    @Nonnull
    static String stateName(@Nonnull StateSupport states) {
        String name = states.getStateName();
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** A resident that just moved in (or stopped following) heads for its home range. */
    static void sendHome(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (!hasHomeStates(npc)) return;
        leashToDoor(npc, house);
        setState(ref, store, STATE_IDLE);
    }

    /** A former resident stays where it is: home becomes the spot it stands on. */
    static void releaseHome(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (npc == null || t == null) return;
        npc.setLeashPoint(new Vector3d(t.getPosition()));
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        if (states != null && hasHomeStates(npc) && STATE_GO_HOME.equals(stateName(states))) setState(ref, store, STATE_IDLE);
    }

    /**
     * Takes a resident inside: remembers its role, marks the record STORED and saves it, then removes the
     * entity (no drops; the animal role leaves a STORED record alone when its entity goes).
     */
    static void storeInside(@Nonnull Store<EntityStore> store, @Nonnull HouseRecord house, @Nonnull AnimalRecord r, @Nullable Ref<EntityStore> ref) {
        NPCEntity npc = ref != null ? store.getComponent(ref, NPCEntity.getComponentType()) : null;
        String role = npc != null ? npc.getRoleName() : LAST_ROLE.get(r.animalId);
        if (role != null && !role.isEmpty()) r.spawnRole = role;
        r.state = AnimalRecord.State.STORED;
        r.following = false;
        TransformComponent t = ref != null && ref.isValid() ? store.getComponent(ref, TransformComponent.getComponentType()) : null;
        if (t != null) {
            Vector3d p = t.getPosition();
            r.lastX = p.x;
            r.lastY = p.y;
            r.lastZ = p.z;
        } else if (!house.worldName.equals(r.worldName) || !nearDoor(house, r.lastX, r.lastZ)) {
            r.lastX = house.doorX;
            r.lastY = house.doorY;
            r.lastZ = house.doorZ;
        }
        r.worldName = house.worldName;
        Ranch.animals().markDirty(r);
        if (ref != null && ref.isValid()) store.removeEntity(ref, RemoveReason.REMOVE);
    }

    /**
     * Brings a stored (or lost) resident out just outside the door as a new incarnation. Any loaded older
     * entity is removed first; an unloaded one becomes stale by the generation bump. Returns false if no
     * role or safe spot was found (the record is left as it was).
     */
    static boolean bringOut(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house, @Nonnull AnimalRecord r) {
        NPCPlugin npcs = NPCPlugin.get();
        if (npcs == null || !HouseBlocks.isLoaded(world, house.doorX, house.doorZ)) return false;
        Set<String> candidates = roleCandidates(r);
        for (String role : candidates) {
            int roleIndex = npcs.getIndex(role);
            if (roleIndex < 0 || !CompatGuard.isManageableRole(role)) continue;
            var builder = npcs.tryGetCachedValidRole(roleIndex);
            if (!(builder instanceof ISpawnableWithModel spawnable)) continue;
            Spot spot = findSpot(world, spawnable, house, HouseBlocks.door(house));
            if (spot == null && house.worldName.equals(r.worldName) && nearDoor(house, r.lastX, r.lastZ)) {
                spot = findSpot(world, spawnable, house, new Vector3d(r.lastX, r.lastY, r.lastZ));
            }
            if (spot == null) {
                log("Ranch animal " + r.animalId + " cannot come out of house " + house.houseId + ": no room at the door");
                return false;
            }
            Ref<EntityStore> old = live(world, r);
            if (old != null) {
                if (r.state != AnimalRecord.State.STORED) {
                    r.state = AnimalRecord.State.STORED;
                    Ranch.animals().markDirty(r);
                }
                store.removeEntity(old, RemoveReason.REMOVE);
            }
            var spawned = npcs.spawnEntity(store, roleIndex, spot.pos, new Rotation3f(0f, (float) (Math.random() * Math.PI * 2), 0f), spot.model, null);
            if (spawned == null || spawned.first() == null || !spawned.first().isValid()) {
                log("Ranch animal " + r.animalId + " cannot come out of house " + house.houseId + ": spawning " + role + " failed");
                return false;
            }
            Ref<EntityStore> ref = spawned.first();
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuid == null) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                return false;
            }
            Ranch.animals().bindEntity(r, ref, uuid.getUuid(), store);
            r.state = AnimalRecord.State.ACTIVE;
            r.following = false;
            r.spawnRole = role;
            r.lastX = spot.pos.x;
            r.lastY = spot.pos.y;
            r.lastZ = spot.pos.z;
            r.worldName = world.getName();
            Ranch.animals().markDirty(r);
            NPCEntity npc = spawned.second();
            if (npc != null) npc.setLeashPoint(HouseBlocks.door(house));
            if (r.name != null && !r.name.isEmpty()) store.ensureAndGetComponent(ref, Nameplate.getComponentType()).setText(r.name);
            LAST_ROLE.put(r.animalId, role);
            return true;
        }
        log("Ranch animal " + r.animalId + " cannot come out of house " + house.houseId + ": no spawnable role in " + candidates);
        return false;
    }

    /** The role it was stored with, the last one seen, then the species' ranch role. */
    @Nonnull
    private static Set<String> roleCandidates(@Nonnull AnimalRecord r) {
        Set<String> out = new LinkedHashSet<>();
        if (r.spawnRole != null && !r.spawnRole.isEmpty()) out.add(r.spawnRole);
        String last = LAST_ROLE.get(r.animalId);
        if (last != null) out.add(last);
        SpeciesDefinition s = SpeciesRegistry.byId(r.speciesId).orElse(null);
        if (s != null) {
            String ranch = s.ranchRoleFor(null, r.baby);
            if (!ranch.isEmpty()) out.add(ranch);
            if (!s.RanchRole.isEmpty()) out.add(s.RanchRole);
        }
        return out;
    }

    private record Spot(@Nonnull Vector3d pos, @Nullable com.hypixel.hytale.server.core.asset.type.model.config.Model model) {}

    /** Where a resident that went in may come back out if the door is blocked: where it last stood, if close. */
    private static boolean nearDoor(@Nonnull HouseRecord house, double x, double z) {
        double dx = x - house.doorX, dz = z - house.doorZ;
        return dx * dx + dz * dz <= SPOT_FALLBACK_RANGE * SPOT_FALLBACK_RANGE;
    }

    private static final double SPOT_FALLBACK_RANGE = 16;
    private static final int SPOT_RADIUS = 4;
    /** Every column within {@link #SPOT_RADIUS} of the centre, nearest first. */
    private static final int[][] SPOT_OFFSETS = spotOffsets();

    @Nonnull
    private static int[][] spotOffsets() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -SPOT_RADIUS; dx <= SPOT_RADIUS; dx++) {
            for (int dz = -SPOT_RADIUS; dz <= SPOT_RADIUS; dz++) {
                if (dx * dx + dz * dz <= SPOT_RADIUS * SPOT_RADIUS) out.add(new int[] {dx, dz});
            }
        }
        out.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return out.toArray(new int[0][]);
    }

    /**
     * Columns around {@code centre}, nearest first, only in loaded chunks and never under the house itself
     * (a column probe there finds its roof). Each column gets a fresh context, as the engine's own
     * {@code NPCPlugin.spawnNPCWithColumnProbe} does: a context reused across columns keeps the previous
     * column's movement mode and rejects every later one.
     */
    @Nullable
    private static Spot findSpot(@Nonnull World world, @Nonnull ISpawnableWithModel spawnable, @Nonnull HouseRecord house, @Nonnull Vector3d centre) {
        int cx = (int) Math.floor(centre.x);
        int cz = (int) Math.floor(centre.z);
        HouseGeometry.Bounds footprint = HouseBlocks.bounds(house.blockId, house.rotation);
        StringBuilder why = new StringBuilder();
        for (int[] o : SPOT_OFFSETS) {
            int x = cx + o[0], z = cz + o[1];
            if (!HouseBlocks.isLoaded(world, x, z) || HouseGeometry.covers(footprint, house.x, house.y, house.z, x, house.y, z)) continue;
            SpawningContext ctx = new SpawningContext();
            try {
                if (!ctx.setSpawnable(spawnable)) {
                    log("No spawn spot for " + house.houseId + ": " + ctx.getLastResult());
                    return null;
                }
                if (!ctx.set(world, x + 0.5, centre.y, z + 0.5)) {
                    why.append(' ').append(x).append(',').append(z).append(':').append(ctx.getLastResult());
                    continue;
                }
                SpawnTestResult test = ctx.canSpawn();
                if (test != SpawnTestResult.TEST_OK) {
                    why.append(' ').append(x).append(',').append(z).append(':').append(test);
                    continue;
                }
                return new Spot(new Vector3d(ctx.xSpawn, ctx.ySpawn, ctx.zSpawn), ctx.getModel());
            } finally {
                ctx.releaseFull();
            }
        }
        log("No spawn spot near " + cx + " " + (int) Math.floor(centre.y) + " " + cz + ":" + (why.length() > 500 ? why.substring(0, 500) + " ..." : why));
        return null;
    }

    static void forget(@Nonnull UUID animalId) {
        LAST_ROLE.remove(animalId);
    }

    static void log(@Nonnull String msg) {
        LOG.info(msg);
    }
}
