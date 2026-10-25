package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.api.event.AnimalTamedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.breeding.BreedingRules;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleMatch;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Runs once when an NPC enters a world (spawned, loaded with its chunk, or re-added by a role change)
 * and once when it leaves. Never per tick. NPCs whose role is not a supported species return after
 * one map lookup and are never touched.
 * <ul>
 *   <li>Carries the ranch marker: deduplicate against the record (stale copies are removed silently).</li>
 *   <li>No marker, adopt role (an animal tamed before this mod): give it an unowned record.</li>
 *   <li>Leaving: remember where the current incarnation was; a current one removed outright goes missing.</li>
 * </ul>
 */
final class AnimalEntitySystem extends RefSystem<EntityStore> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private final PersistentAnimalRegistry registry;
    private final ComponentType<EntityStore, AnimalMarker> markerType;
    private final ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
    private final ComponentType<EntityStore, UUIDComponent> uuidType = UUIDComponent.getComponentType();
    private final ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();

    AnimalEntitySystem(@Nonnull PersistentAnimalRegistry registry, @Nonnull ComponentType<EntityStore, AnimalMarker> markerType) {
        this.registry = registry;
        this.markerType = markerType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return npcType;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        NPCEntity npc = cb.getComponent(ref, npcType);
        if (npc == null) return;
        String role = npc.getRoleName();
        if (role == null) return;
        RoleMatch match = SpeciesRegistry.byRole(role).orElse(null);
        if (match == null) return;
        UUIDComponent uuidComponent = cb.getComponent(ref, uuidType);
        if (uuidComponent == null) return;
        UUID entityUuid = uuidComponent.getUuid();
        boolean foreign = CompatGuard.hasIgnoredComponent(cb.getArchetype(ref));
        AnimalMarker marker = cb.getComponent(ref, markerType);
        World world = store.getExternalData().getWorld();

        if (marker != null) {
            onMarkedAdded(ref, cb, world, marker, entityUuid, role, match, !foreign && CompatGuard.isManageableRole(role), reason == AddReason.SPAWN);
            return;
        }
        if (mayBeOrphan(match.kind(), foreign, role) && relinkOrphan(ref, cb, world, entityUuid, role, match)) return;
        if (shouldAdopt(role, false, foreign)) adopt(ref, cb, world, entityUuid, role, match);
    }

    /** Only untagged ranch and adopt roles can be one of ours that lost its tag; wild animals never are. */
    static boolean mayBeOrphan(@Nonnull RoleKind kind, boolean hasForeignComponent, @Nonnull String role) {
        if (hasForeignComponent || !CompatGuard.isManageableRole(role)) return false;
        return kind == RoleKind.RANCH || kind == RoleKind.RANCH_BABY || isAdoptRole(role, kind);
    }

    /**
     * An untagged ranch or adopt animal that is a MISSING record come back (a vanilla coop re-created it
     * with a new UUID): it becomes that record's current incarnation instead of a stranger or a new record.
     */
    private boolean relinkOrphan(Ref<EntityStore> ref, CommandBuffer<EntityStore> cb, World world, UUID entityUuid, String role, RoleMatch match) {
        TransformComponent t = cb.getComponent(ref, transformType);
        if (t == null) return false;
        return relink(registry, markerType, ref, cb, world, entityUuid, role, match, t.getPosition());
    }

    /**
     * Binds an untagged entity to the MISSING record {@link OrphanRelink#match} picks, if any. Works with a command
     * buffer (entity add) or the store (admin command). Returns false when nothing matched.
     */
    static boolean relink(@Nonnull PersistentAnimalRegistry registry, @Nonnull ComponentType<EntityStore, AnimalMarker> markerType,
                          @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull World world,
                          @Nonnull UUID entityUuid, @Nonnull String role, @Nonnull RoleMatch match, @Nonnull Vector3d p) {
        AnimalStore store = registry.store();
        if (store.byEntity(entityUuid) != null) return false;
        AnimalRecord r = OrphanRelink.match(store, world.getName(), match.species().Id, role, isBaby(match.kind()), p.x, p.z);
        if (r == null) return false;
        Ref<EntityStore> old = AnimalPresence.get(r.animalId);
        if (old != null && old != ref) return false;
        long generation = store.bind(r, entityUuid);
        accessor.putComponent(ref, markerType, new AnimalMarker(r.animalId, generation));
        String plate = com.hexvane.cozytalesranching.taming.TamingApi.nameplateText(r);
        if (plate != null) accessor.putComponent(ref, Nameplate.getComponentType(), new Nameplate(plate));
        AnimalPresence.put(r.animalId, ref);
        AnimalSkins.capture(r, ref, accessor);
        AnimalSkins.apply(r, ref, accessor, true);
        r.worldName = world.getName();
        r.lastX = p.x;
        r.lastY = p.y;
        r.lastZ = p.z;
        store.setRole(r.animalId, role);
        registry.markDirty(r);
        LOG.info("Ranch animal " + r.animalId + " (" + r.speciesId + (r.name != null ? " \"" + r.name + "\"" : "") + ") came back untagged as " + role
            + " entity " + entityUuid + " (re-created by a vanilla coop or another mod); relinked to its record");
        return true;
    }

    /** An NPC becomes an unowned ranch animal only if: supported species, adopt role, not ignored, no marker yet, no other mod's component. */
    static boolean shouldAdopt(@Nonnull String role, boolean hasMarker, boolean hasForeignComponent) {
        if (hasMarker || hasForeignComponent || !CompatGuard.isManageableRole(role)) return false;
        RoleMatch match = SpeciesRegistry.byRole(role).orElse(null);
        return match != null && isAdoptRole(role, match.kind());
    }

    private void onMarkedAdded(Ref<EntityStore> ref, CommandBuffer<EntityStore> cb, World world, AnimalMarker marker, UUID entityUuid, String role, RoleMatch match, boolean manageable,
                               boolean spawned) {
        UUID animalId = marker.animalId();
        if (animalId == null) return;
        AnimalStore store = registry.store();
        if (!manageable) {
            // another mod has claimed it: never removed or changed, only noted so recovery does not make a second one
            if (store.isCurrent(animalId, entityUuid, marker.generation())) AnimalPresence.put(animalId, ref);
            return;
        }
        AnimalStore.Verdict verdict = store.judge(animalId, marker.generation(), entityUuid);
        if (verdict == AnimalStore.Verdict.CURRENT || verdict == AnimalStore.Verdict.CURRENT_CAUGHT_UP) {
            Ref<EntityStore> other = AnimalPresence.get(animalId);
            if (other != null && other != ref) {
                // the same incarnation twice (a copied world, two stores loading at once): the first one stays
                removeStale(ref, cb, animalId, "already loaded elsewhere");
                return;
            }
            AnimalPresence.put(animalId, ref);
            AnimalRecord r = store.get(animalId);
            if (r != null) {
                boolean changed = verdict == AnimalStore.Verdict.CURRENT_CAUGHT_UP;
                if (r.state == AnimalRecord.State.MISSING) {
                    r.state = AnimalRecord.State.ACTIVE;
                    changed = true;
                }
                if (!world.getName().equals(r.worldName)) {
                    r.worldName = world.getName();
                    changed = true;
                }
                store.setRole(animalId, role);
                // the skin: noted once (animals tamed before skins were kept), then put back on (a loaded model drops
                // attachments our ranch model asset does not define; a fresh spawn has just rolled its own)
                changed |= AnimalSkins.capture(r, ref, cb);
                changed |= AnimalSkins.apply(r, ref, cb, spawned);
                if (changed) registry.markDirty(r);
                else registry.scheduleSave();
            }
            if (verdict == AnimalStore.Verdict.CURRENT_CAUGHT_UP) LOG.info("Ranch animal " + animalId + " record caught up to its entity (generation " + marker.generation() + ")");
            return;
        }
        if (verdict == AnimalStore.Verdict.STALE) {
            boolean removed = store.isTombstoned(animalId);
            if (removed && (match.kind() == RoleKind.WILD || match.kind() == RoleKind.WILD_BABY)) {
                // released back to the wild with the tag still on: it is a wild animal now, not a copy
                cb.tryRemoveComponent(ref, markerType);
                debug("Released animal " + animalId + " is wild again; ranch tag removed");
                return;
            }
            removeStale(ref, cb, animalId, removed ? "animal was removed" : "older incarnation");
            return;
        }
        // UNKNOWN: the record was never saved (the server stopped first). Keep the animal, unowned.
        AnimalRecord r = store.recreate(animalId, marker.generation(), entityUuid, match.species().Id, world.getName(), isBaby(match.kind()));
        store.setRole(animalId, role);
        AnimalPresence.put(animalId, ref);
        registry.markDirty(r);
        LOG.warning("Ranch animal " + animalId + " had no saved record; rebuilt it unowned");
        fireAdopted(world, r);
    }

    private void adopt(Ref<EntityStore> ref, CommandBuffer<EntityStore> cb, World world, UUID entityUuid, String role, RoleMatch match) {
        AnimalStore store = registry.store();
        AnimalStore.AdoptResult result = store.adoptOrGet(entityUuid, match.species().Id, world.getName(), isBaby(match.kind()));
        AnimalRecord r = result.record();
        if (result.created()) {
            r.traits = BreedingRules.tamedTraits(TraitRegistry.get(), ThreadLocalRandom.current());
            fillPosition(r, ref, cb);
            registry.bindEntity(r, ref, entityUuid, cb);
            store.setRole(r.animalId, role);
            registry.markDirty(r);
            fireAdopted(world, r);
        } else {
            // seen again before its marker landed: put the same marker again, no new generation, no second event
            cb.putComponent(ref, markerType, new AnimalMarker(r.animalId, r.generation));
            AnimalPresence.put(r.animalId, ref);
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        AnimalMarker marker = cb.getComponent(ref, markerType);
        if (marker == null || marker.animalId() == null) return;
        UUID animalId = marker.animalId();
        if (!AnimalPresence.removeIfRef(animalId, ref)) return;
        AnimalRecord r = registry.store().get(animalId);
        if (r == null) return;
        fillPosition(r, ref, cb);
        r.worldName = store.getExternalData().getWorld().getName();
        if (reason == RemoveReason.REMOVE && r.state == AnimalRecord.State.ACTIVE) {
            // removed outright (despawned, killed by a command, a builder undo): recovery brings it back
            r.state = AnimalRecord.State.MISSING;
            AnimalPresence.markMissing(animalId, System.currentTimeMillis());
            if (r.ownerId != null) LOG.info("Ranch animal " + animalId + " (" + r.speciesId + ", owner " + r.ownerId + ") entity was removed; marked missing, recovery will bring it back");
            else debug("Ranch animal " + animalId + " entity removed; marked missing");
        }
        // the entity is going (chunk unload, removal): its last spot is saved even if nothing else changed
        registry.markDirtyWithPosition(r);
    }

    private void removeStale(Ref<EntityStore> ref, CommandBuffer<EntityStore> cb, UUID animalId, String why) {
        cb.tryRemoveEntity(ref, RemoveReason.REMOVE);
        debug("Removed stale copy of ranch animal " + animalId + " (" + why + ")");
    }

    private void fillPosition(AnimalRecord r, Ref<EntityStore> ref, CommandBuffer<EntityStore> cb) {
        TransformComponent t = cb.getComponent(ref, transformType);
        if (t == null) return;
        Vector3d p = t.getPosition();
        r.lastX = p.x;
        r.lastY = p.y;
        r.lastZ = p.z;
    }

    static boolean isAdoptRole(@Nonnull String role, @Nonnull RoleKind kind) {
        return kind == RoleKind.ADOPT || kind == RoleKind.ADOPT_BABY;
    }

    static boolean isBaby(@Nonnull RoleKind kind) {
        return kind == RoleKind.ADOPT_BABY || kind == RoleKind.WILD_BABY || kind == RoleKind.RANCH_BABY;
    }

    private static void fireAdopted(@Nonnull World world, @Nonnull AnimalRecord r) {
        // after this tick's command buffer has run, so listeners may touch the store
        world.execute(() -> RanchEvents.fire(new AnimalTamedEvent(r.view(), null, AnimalTamedEvent.Source.ADOPTED)));
    }

    static void debug(@Nullable String msg) {
        if (RanchConfig.get().debugLogging()) LOG.info(msg);
    }
}
