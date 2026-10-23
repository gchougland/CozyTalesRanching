package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import com.hypixel.hytale.server.spawning.SpawningContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Finds animals whose entity is gone and brings them back.
 * <p>
 * Every {@link #SWEEP_SECONDS} the background thread asks each world to run {@link #sweep} on its own
 * thread. A sweep walks that world's records once (no entity scans, no chunk loads):
 * <ul>
 *   <li>current entity loaded: refresh the record's last position if it moved;</li>
 *   <li>ACTIVE, not loaded, but the chunk where it was last seen is loaded for {@link #SUSPECT_SWEEPS}
 *       sweeps in a row: it is gone, mark it MISSING;</li>
 *   <li>MISSING for {@code RecoveryDelaySeconds}: respawn it beside its owner (online, same world) or where
 *       it was last seen (chunk loaded), on a spot the game's own spawn test accepts and with no fluid
 *       underfoot. If neither place is loaded it waits.</li>
 * </ul>
 * Houses are wave 2; once they have a position, "near its house" goes first here.
 */
final class AnimalRecovery {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    static final long SWEEP_SECONDS = 15;
    static final int SUSPECT_SWEEPS = 3;
    static long recoveryDelayMs() {
        return TimeUnit.SECONDS.toMillis(RanchConfig.get().recoveryDelaySeconds());
    }
    private static final double MOVED_ENOUGH_SQ = 4.0 * 4.0;

    private final PersistentAnimalRegistry registry;
    private final ComponentType<EntityStore, AnimalMarker> markerType;
    @Nullable private ScheduledFuture<?> task;

    AnimalRecovery(@Nonnull PersistentAnimalRegistry registry, @Nonnull ComponentType<EntityStore, AnimalMarker> markerType) {
        this.registry = registry;
        this.markerType = markerType;
    }

    void start() {
        task = registry.executor().scheduleWithFixedDelay(this::requestSweeps, SWEEP_SECONDS, SWEEP_SECONDS, TimeUnit.SECONDS);
    }

    void stop() {
        if (task != null) task.cancel(false);
    }

    private void requestSweeps() {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            for (World world : universe.getWorlds().values()) {
                if (!world.isAlive()) continue;
                if (registry.store().inWorld(world.getName()).isEmpty()) continue;
                world.execute(() -> sweep(world));
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Ranch animal recovery sweep could not be scheduled", e);
        }
    }

    /** World thread only. */
    void sweep(@Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        long now = System.currentTimeMillis();
        for (AnimalRecord r : registry.store().inWorld(world.getName())) {
            try {
                sweepOne(world, store, r, now);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Recovery failed for ranch animal " + r.animalId, e);
            }
        }
    }

    private void sweepOne(World world, Store<EntityStore> store, AnimalRecord r, long now) {
        switch (r.state) {
            case STORED -> { }
            case ACTIVE -> {
                Ref<EntityStore> ref = AnimalPresence.get(r.animalId);
                if (ref == null && r.entityUuid != null) {
                    // loaded but missed by presence (it should not happen); trust the world's own index
                    Ref<EntityStore> byUuid = world.getEntityStore().getRefFromUUID(r.entityUuid);
                    if (byUuid != null && byUuid.isValid()) ref = byUuid;
                }
                if (ref != null) {
                    AnimalPresence.clearSuspect(r.animalId);
                    if (ref.getStore() == store) refreshPosition(store, ref, r);
                    return;
                }
                if (r.entityUuid == null || !isChunkLoaded(world, r.lastX, r.lastZ)) {
                    AnimalPresence.clearSuspect(r.animalId);
                    return;
                }
                if (AnimalPresence.bumpSuspect(r.animalId) >= SUSPECT_SWEEPS) {
                    r.state = AnimalRecord.State.MISSING;
                    AnimalPresence.markMissing(r.animalId, now);
                    registry.markDirty(r);
                    AnimalEntitySystem.debug("Ranch animal " + r.animalId + " not where it was last seen; marked missing");
                }
            }
            case MISSING -> {
                if (now - AnimalPresence.missingSince(r.animalId, now) < recoveryDelayMs()) return;
                Vector3d near = recoveryTarget(world, r);
                if (near != null) respawn(world, store, r, near);
            }
        }
    }

    private void refreshPosition(Store<EntityStore> store, Ref<EntityStore> ref, AnimalRecord r) {
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t == null) return;
        Vector3d p = t.getPosition();
        double dx = p.x - r.lastX, dy = p.y - r.lastY, dz = p.z - r.lastZ;
        if (dx * dx + dy * dy + dz * dz < MOVED_ENOUGH_SQ) return;
        r.lastX = p.x;
        r.lastY = p.y;
        r.lastZ = p.z;
        registry.markDirty(r);
    }

    /** Owner if online in this world, else the last known spot if its chunk is loaded, else null (wait). */
    @Nullable
    private static Vector3d recoveryTarget(@Nonnull World world, @Nonnull AnimalRecord r) {
        if (r.ownerId != null) {
            PlayerRef owner = Universe.get().getPlayer(r.ownerId);
            if (owner != null && world.getWorldConfig().getUuid().equals(owner.getWorldUuid())) {
                Vector3d p = owner.getTransform().getPosition();
                if (isChunkLoaded(world, p.x, p.z)) return new Vector3d(p);
            }
        }
        if (isChunkLoaded(world, r.lastX, r.lastZ)) return new Vector3d(r.lastX, r.lastY, r.lastZ);
        return null;
    }

    static boolean isChunkLoaded(@Nonnull World world, double x, double z) {
        return world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z)) != null;
    }

    /**
     * Makes a new incarnation near {@code near}. World thread only, outside a tick (a world task or a
     * command). Any loaded older incarnation is removed first; one in an unloaded chunk becomes stale
     * by the generation bump and is removed when it loads. Returns false if no safe spot or role was found.
     */
    boolean respawn(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull AnimalRecord r, @Nonnull Vector3d near) {
        NPCPlugin npcs = NPCPlugin.get();
        if (npcs == null) return false;
        for (String role : roleCandidates(registry.store(), r)) {
            int roleIndex = npcs.getIndex(role);
            if (roleIndex < 0 || !CompatGuard.isManageableRole(role)) continue;
            var builder = npcs.tryGetCachedValidRole(roleIndex);
            if (builder == null || !(builder instanceof ISpawnableWithModel spawnable)) continue;
            Spot spot = findSpot(world, spawnable, near);
            if (spot == null) return false;
            Ref<EntityStore> old = AnimalPresence.get(r.animalId);
            AnimalPresence.remove(r.animalId);
            if (old != null) removeOld(old);
            var spawned = npcs.spawnEntity(store, roleIndex, spot.pos, new Rotation3f(0f, (float) (Math.random() * Math.PI * 2), 0f), spot.model, (npc, holder, s) -> {
                UUIDComponent uuid = holder.getComponent(UUIDComponent.getComponentType());
                if (uuid == null) return;
                long generation = registry.store().bind(r, uuid.getUuid());
                holder.putComponent(markerType, new AnimalMarker(r.animalId, generation));
                String plate = com.hexvane.cozytalesranching.taming.TamingApi.nameplateText(r);
                if (plate != null) holder.putComponent(Nameplate.getComponentType(), new Nameplate(plate));
            }, null);
            if (spawned == null) return false;
            r.lastX = spot.pos.x;
            r.lastY = spot.pos.y;
            r.lastZ = spot.pos.z;
            r.worldName = world.getName();
            registry.markDirty(r);
            LOG.info("Ranch animal " + r.animalId + " recovered in " + world.getName() + " as " + role);
            return true;
        }
        return false;
    }

    private static void removeOld(@Nonnull Ref<EntityStore> old) {
        Store<EntityStore> s = old.getStore();
        World w = s.getExternalData().getWorld();
        Runnable remove = () -> {
            if (old.isValid()) old.getStore().removeEntity(old, RemoveReason.REMOVE);
        };
        if (w.isInThread()) remove.run();
        else w.execute(remove);
    }

    /** The role it last had, then the species' roles that fit how it joined the ranch. */
    @Nonnull
    static List<String> roleCandidates(@Nonnull AnimalStore animals, @Nonnull AnimalRecord r) {
        Set<String> out = new LinkedHashSet<>();
        if (r.spawnRole != null && !r.spawnRole.isEmpty()) out.add(r.spawnRole);
        String last = animals.role(r.animalId);
        if (last != null) out.add(last);
        SpeciesDefinition s = SpeciesRegistry.byId(r.speciesId).orElse(null);
        if (s != null) {
            List<String> adopt = r.baby ? s.AdoptBabyRoles : s.AdoptRoles;
            String ranch = r.baby ? s.RanchBabyRole : s.RanchRole;
            if (!r.adopted && !ranch.isEmpty()) out.add(ranch);
            out.addAll(adopt);
            if (r.adopted && !ranch.isEmpty()) out.add(ranch);
            if (r.baby) {
                out.addAll(s.AdoptRoles);
                if (!s.RanchRole.isEmpty()) out.add(s.RanchRole);
            }
        }
        return new ArrayList<>(out);
    }

    private record Spot(@Nonnull Vector3d pos, @Nullable com.hypixel.hytale.server.core.asset.type.model.config.Model model) {}

    /** Tries columns in rings around the target, never loading a chunk; the game's spawn test checks room and breathing. */
    @Nullable
    private static Spot findSpot(@Nonnull World world, @Nonnull ISpawnableWithModel spawnable, @Nonnull Vector3d near) {
        int cx = (int) Math.floor(near.x);
        int cz = (int) Math.floor(near.z);
        int[][] offsets = {
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2},
            {3, 1}, {-3, -1}, {1, -3}, {-1, 3}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {0, 0}
        };
        SpawningContext ctx = new SpawningContext();
        try {
            if (!ctx.setSpawnable(spawnable)) return null;
            for (int[] o : offsets) {
                int x = cx + o[0], z = cz + o[1];
                if (!isChunkLoaded(world, x, z)) continue;
                if (ctx.set(world, x + 0.5, near.y, z + 0.5) && ctx.canSpawn() == SpawnTestResult.TEST_OK && ctx.groundFluid == null) {
                    return new Spot(new Vector3d(ctx.xSpawn, ctx.ySpawn, ctx.zSpawn), ctx.getModel());
                }
                ctx.release();
            }
            return null;
        } finally {
            ctx.releaseFull();
        }
    }

    /** Respawns a record next to a position right now, ignoring the delay. For the admin command. */
    boolean resummon(@Nonnull World world, @Nonnull AnimalRecord r, @Nonnull Vector3d near) {
        return respawn(world, world.getEntityStore().getStore(), r, near);
    }
}
