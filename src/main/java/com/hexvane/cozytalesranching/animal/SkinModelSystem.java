package com.hexvane.cozytalesranching.animal;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Puts a ranch animal's skin back whenever the engine gives it a new model: a role swap's new appearance (tamed, grown
 * up, a variant), or a role action rebuilding the model in place (shearing), which drops attachments our ranch model
 * asset does not define. Also gives a released animal its skin back on its wild model. Runs only when an NPC's model
 * component is set (rare), never per tick; NPCs that are not ranch animals return after one or two lookups.
 */
final class SkinModelSystem extends RefChangeSystem<EntityStore, ModelComponent> {
    private final PersistentAnimalRegistry registry;
    private final ComponentType<EntityStore, AnimalMarker> markerType;

    SkinModelSystem(@Nonnull PersistentAnimalRegistry registry, @Nonnull ComponentType<EntityStore, AnimalMarker> markerType) {
        this.registry = registry;
        this.markerType = markerType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, ModelComponent> componentType() {
        return ModelComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull ModelComponent component, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        handle(ref, cb, true);
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable ModelComponent oldComponent, @Nonnull ModelComponent newComponent,
                               @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        handle(ref, cb, changedAsset(oldComponent, newComponent));
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull ModelComponent component, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
    }

    /** A new model asset (a swap) rather than the same asset rebuilt in place. */
    static boolean changedAsset(@Nullable ModelComponent before, @Nonnull ModelComponent after) {
        if (before == null || before.getModel() == null || after.getModel() == null) return true;
        return !Objects.equals(before.getModel().getModelAssetId(), after.getModel().getModelAssetId());
    }

    private void handle(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> cb, boolean fresh) {
        AnimalRecord r = currentRecord(ref, cb);
        if (r != null) {
            if (AnimalSkins.apply(r, ref, cb, fresh)) registry.markDirty(r);
            return;
        }
        if (!AnimalSkins.anyReleased()) return;
        UUIDComponent uuid = cb.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid != null) AnimalSkins.applyReleased(uuid.getUuid(), ref, cb);
    }

    /** The record this entity is the live incarnation of (tagged, or bound with its tag still queued), else null. */
    @Nullable
    private AnimalRecord currentRecord(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> cb) {
        UUIDComponent uuid = cb.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) return null;
        UUID entityUuid = uuid.getUuid();
        AnimalMarker marker = cb.getComponent(ref, markerType);
        AnimalRecord r = marker != null && marker.animalId() != null ? registry.store().get(marker.animalId()) : registry.store().byEntity(entityUuid);
        if (r == null || !entityUuid.equals(r.entityUuid)) return null;
        return r;
    }
}
