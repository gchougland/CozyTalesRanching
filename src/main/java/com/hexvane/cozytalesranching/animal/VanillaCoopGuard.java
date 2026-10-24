package com.hexvane.cozytalesranching.animal;

import com.hypixel.hytale.builtin.adventure.farming.component.CoopResidentComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps ranch animals out of vanilla coops.
 * <p>
 * Vanilla's chicken coop ({@code Server/Farming/Coops/Coop_Chicken.json}) captures every NPC within 10 blocks
 * whose role is in the {@code Chicken} NPC group, and that group is {@code "*Chicken"}, so it matches
 * {@code Tamed_Chicken} and our own {@code CozyRanch_Chicken}. A captured resident is despawned at dusk and
 * spawned again at dawn as a brand new entity from its role id ({@code CoopBlock.ensureSpawnResidentsInWorld}):
 * new UUID, no ranch tag, no name, so the record went MISSING and the chicken came back a stranger.
 * <p>
 * Capturing is {@code CoopBlock.tryPutWildResidentFromWild}, which adds {@link CoopResidentComponent} to the
 * entity. When that component lands on an entity that has a ranch record we take it off again. The coop then
 * drops the resident at its next dusk check ({@code ensureNoResidentsInWorld}: no component, resident removed)
 * instead of despawning it. Runs only when that component is added, never per tick.
 */
final class VanillaCoopGuard extends RefChangeSystem<EntityStore, CoopResidentComponent> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private final PersistentAnimalRegistry registry;
    private final ComponentType<EntityStore, AnimalMarker> markerType;
    private final ComponentType<EntityStore, CoopResidentComponent> coopType;

    private VanillaCoopGuard(@Nonnull PersistentAnimalRegistry registry, @Nonnull ComponentType<EntityStore, AnimalMarker> markerType,
                             @Nonnull ComponentType<EntityStore, CoopResidentComponent> coopType) {
        this.registry = registry;
        this.markerType = markerType;
        this.coopType = coopType;
    }

    /** Null when the farming module is not running (then there are no coops to guard against). */
    @Nullable
    static VanillaCoopGuard create(@Nonnull PersistentAnimalRegistry registry, @Nonnull ComponentType<EntityStore, AnimalMarker> markerType) {
        try {
            ComponentType<EntityStore, CoopResidentComponent> type = CoopResidentComponent.getComponentType();
            return type == null ? null : new VanillaCoopGuard(registry, markerType, type);
        } catch (RuntimeException | LinkageError e) {
            LOG.log(Level.INFO, "Vanilla farming coops not available; coop guard not installed", e);
            return null;
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, CoopResidentComponent> componentType() {
        return coopType;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull CoopResidentComponent component, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        UUID animalId = ranchAnimal(ref, cb);
        if (animalId == null) {
            AnimalEntitySystem.debug("Vanilla coop at " + component.getCoopLocation() + " took a creature that is not a ranch animal");
            return;
        }
        cb.tryRemoveComponent(ref, coopType);
        AnimalEntitySystem.debug("Ranch animal " + animalId + " kept out of the vanilla coop at " + component.getCoopLocation());
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable CoopResidentComponent oldComponent, @Nonnull CoopResidentComponent newComponent, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        if (ranchAnimal(ref, cb) != null) cb.tryRemoveComponent(ref, coopType);
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull CoopResidentComponent component, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
    }

    /** The record id when this entity is a ranch animal (tagged, or bound to a record whose tag is still queued). */
    @Nullable
    private UUID ranchAnimal(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> cb) {
        AnimalMarker marker = cb.getComponent(ref, markerType);
        if (marker != null && marker.animalId() != null && registry.store().get(marker.animalId()) != null) return marker.animalId();
        UUIDComponent uuid = cb.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) return null;
        AnimalRecord r = registry.store().byEntity(uuid.getUuid());
        return r == null ? null : r.animalId;
    }
}
