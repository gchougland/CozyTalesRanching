package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.behaviour.FollowProgress;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Once a second per world: points following animals at their owner (or the mount the owner rides, see
 * {@link FollowAnchor}) and brings back any that fell too far behind or got stuck. Walks owners in the world and their records, never entities, so the cost is
 * players × owned animals per second. Owners in another world or offline are left alone: the animal waits.
 */
final class FollowSystem extends TickingSystem<EntityStore> {
    private static final float PASS_SECONDS = 1f;
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("CozyTalesRanching");

    private record Track(double x, double y, double z, double stuckSeconds) {}

    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> RIDING = new ConcurrentHashMap<>();
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();

    static void forget(@Nonnull UUID animalId) {
        TRACKS.remove(animalId);
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total < PASS_SECONDS) return;
        elapsed.put(world.getName(), 0f);
        if (world.getPlayerRefs().isEmpty()) return;
        world.execute(() -> pass(world));
    }

    private static void pass(@Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        RanchConfig config = RanchConfig.get();
        for (PlayerRef owner : world.getPlayerRefs()) {
            Ref<EntityStore> ownerRef = owner.getReference();
            if (ownerRef == null || !ownerRef.isValid()) continue;
            // While the owner rides an NPC mount the mount carries the real position: follow it (FollowAnchor).
            Ref<EntityStore> anchor = FollowAnchor.of(ownerRef, store);
            TransformComponent ownerT = store.getComponent(anchor, TransformComponent.getComponentType());
            if (ownerT == null) continue;
            noteAnchor(owner.getUuid(), ownerRef, anchor, ownerT, store);
            for (AnimalRecord record : Ranch.animals().ownedBy(owner.getUuid())) {
                if (!record.following || record.state != AnimalRecord.State.ACTIVE || record.entityUuid == null) continue;
                Ref<EntityStore> animal = world.getEntityStore().getRefFromUUID(record.entityUuid);
                if (animal == null || !animal.isValid() || !isRanchRole(animal, store)) continue;
                AnimalControl.applyFollow(animal, record, anchor, store);
                // A follower plays no behaviour: drop any clip nobody is timing (a Sleep from the wild role or a lost nap).
                String clip = com.hexvane.cozytalesranching.behaviour.AnimalClips.current(store, animal);
                if (clip != null && com.hexvane.cozytalesranching.behaviour.AnimalClips.clearUntimed(store, animal)) {
                    LOG.info("Ranch animal " + record.animalId + " stops " + clip + " to follow its owner");
                }
                TransformComponent t = store.getComponent(animal, TransformComponent.getComponentType());
                if (t == null) continue;
                Vector3d pos = t.getPosition();
                Vector3d ownerPos = ownerT.getPosition();
                double distance = pos.distance(ownerPos);
                Track track = advance(record.animalId, pos, ownerPos);
                record.lastX = pos.x;
                record.lastY = pos.y;
                record.lastZ = pos.z;
                boolean tooFar = distance > config.followTeleportDistance();
                boolean stuck = track.stuckSeconds() >= config.followStuckSeconds();
                if (tooFar || stuck) {
                    LOG.info(String.format(java.util.Locale.ROOT, "Ranch animal %s brought back to its owner (%s, %.1f blocks away, state %s)",
                        record.animalId, tooFar ? "too far" : "no progress for " + (int) track.stuckSeconds() + " s", distance, stateOf(animal, store)));
                    bringBack(world, store, animal, record, ownerT);
                }
            }
        }
    }

    /** Logs when an owner gets on or off a mount, so a run's log shows which entity the followers are after. */
    private static void noteAnchor(@Nonnull UUID ownerId, @Nonnull Ref<EntityStore> ownerRef, @Nonnull Ref<EntityStore> anchor, @Nonnull TransformComponent anchorT, @Nonnull Store<EntityStore> store) {
        boolean riding = !anchor.equals(ownerRef);
        Boolean before = RIDING.put(ownerId, riding);
        if (before == null ? !riding : before == riding) return;
        Vector3d p = anchorT.getPosition();
        LOG.info(String.format(java.util.Locale.ROOT, "Ranch followers of %s now follow %s at %.1f %.1f %.1f",
            ownerId, riding ? "the mount " + uuidOf(anchor, store) : "their owner", p.x, p.y, p.z));
    }

    @Nonnull
    private static String uuidOf(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        com.hypixel.hytale.server.core.entity.UUIDComponent id = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        return id != null ? id.getUuid().toString() : "?";
    }

    /**
     * Counts the seconds the animal has not come closer to where its owner is now ({@link FollowProgress}): standing
     * still, pushing into a fence, or sliding along a pen's fence all count, walking after a running owner does not.
     */
    private static Track advance(@Nonnull UUID animalId, @Nonnull Vector3d pos, @Nonnull Vector3d ownerPos) {
        Track before = TRACKS.get(animalId);
        double stuck = 0;
        if (before != null) {
            double beforeToOwner = Math.sqrt(sq(before.x() - ownerPos.x) + sq(before.y() - ownerPos.y) + sq(before.z() - ownerPos.z));
            stuck = FollowProgress.stuckSeconds(beforeToOwner, pos.distance(ownerPos), PASS_SECONDS, before.stuckSeconds());
        }
        Track next = new Track(pos.x, pos.y, pos.z, stuck);
        TRACKS.put(animalId, next);
        return next;
    }

    private static void bringBack(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> animal,
        @Nonnull AnimalRecord record,
        @Nonnull TransformComponent ownerT
    ) {
        Vector3d o = ownerT.getPosition();
        float yaw = ownerT.getRotation().yaw();
        Vector3d look = Transform.getDirection(0f, yaw);
        Optional<TeleportSpotFinder.Spot> spot = TeleportSpotFinder.find(o.x, o.y, o.z, look.x, look.z, new WorldBlockGrid(world));
        if (spot.isEmpty()) return;
        TeleportSpotFinder.Spot s = spot.get();
        store.putComponent(animal, Teleport.getComponentType(), new Teleport(new Vector3d(s.x(), s.y(), s.z()), new Rotation3f(0f, yaw, 0f)));
        TRACKS.remove(record.animalId);
        record.lastX = s.x();
        record.lastY = s.y();
        record.lastZ = s.z();
        Ranch.animals().markDirty(record);
    }

    private static boolean isRanchRole(@Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) return false;
        return SpeciesRegistry.byRole(npc.getRoleName())
            .map(m -> m.kind() == RoleKind.RANCH || m.kind() == RoleKind.RANCH_BABY)
            .orElse(false);
    }

    @Nonnull
    private static String stateOf(@Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        com.hypixel.hytale.server.npc.role.support.StateSupport states = store.getComponent(animal, com.hypixel.hytale.server.npc.role.support.StateSupport.getComponentType());
        return states != null ? states.getStateName() : "?";
    }

    private static double sq(double v) {
        return v * v;
    }
}
