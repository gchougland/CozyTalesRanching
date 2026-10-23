package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.animal.AnimalMarker;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps an owned animal's nameplate in step with its name and the {@code ShowNameplates} switch. Applied when the
 * animal is tamed, claimed, named or petted, and once each time its entity enters a world (spawned, chunk loaded,
 * role swapped), so a plate left over from before the switch was turned off goes away on next sight.
 */
public final class Nameplates {
    private Nameplates() {}

    /** Puts, updates or removes the plate. World thread, outside ticks (or with a CommandBuffer as accessor). */
    public static void apply(@Nonnull AnimalRecord record, @Nonnull Ref<EntityStore> animal, @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (!animal.isValid()) return;
        String text = TamingApi.nameplateText(record);
        Nameplate current = accessor.getComponent(animal, Nameplate.getComponentType());
        if (text == null) {
            if (current != null) accessor.tryRemoveComponent(animal, Nameplate.getComponentType());
            return;
        }
        if (current == null) accessor.putComponent(animal, Nameplate.getComponentType(), new Nameplate(text));
        else if (!text.equals(current.getText())) current.setText(text);
    }

    /** Applies the plate to the record's entity if it is loaded. World thread of the animal's world. */
    public static void applyLoaded(@Nonnull AnimalRecord record) {
        Ref<EntityStore> ref = loadedEntity(record);
        if (ref != null) apply(record, ref, ref.getStore());
    }

    @Nullable
    static Ref<EntityStore> loadedEntity(@Nonnull AnimalRecord record) {
        if (record.entityUuid == null || record.worldName == null || record.worldName.isEmpty()) return null;
        World world = Universe.get().getWorld(record.worldName);
        if (world == null) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(record.entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    /** Once per ranch animal entering a world; never per tick. Only entities with the ranch marker are seen. */
    static final class OnLoad extends RefSystem<EntityStore> {
        private final ComponentType<EntityStore, AnimalMarker> markerType;

        OnLoad(@Nonnull ComponentType<EntityStore, AnimalMarker> markerType) {
            this.markerType = markerType;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return markerType;
        }

        @Override
        public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
            AnimalMarker marker = cb.getComponent(ref, markerType);
            if (marker == null || marker.animalId() == null) return;
            UUIDComponent uuid = cb.getComponent(ref, UUIDComponent.getComponentType());
            if (uuid == null) return;
            Ranch.animals().get(marker.animalId()).ifPresent(record -> {
                // only the current incarnation; a stale copy is being removed by the animal system
                if (record.ownerId != null && uuid.getUuid().equals(record.entityUuid)) apply(record, ref, cb);
            });
        }

        @Override
        public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {}
    }
}
