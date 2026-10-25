package com.hexvane.cozytalesranching.animal;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Every ranch animal's record, and the one place that decides which entity is the real one.
 * Implemented by the {@code animal} role (persistent, deduplicating); {@link InMemoryAnimalRegistry}
 * stands in until then. Get it with {@link Ranch#animals()}.
 */
public interface AnimalRegistry {
    @Nonnull Optional<AnimalRecord> get(@Nonnull UUID animalId);

    @Nonnull Optional<AnimalRecord> byEntity(@Nonnull UUID entityUuid);

    @Nonnull List<AnimalRecord> ownedBy(@Nonnull UUID ownerId);

    @Nonnull List<AnimalRecord> inHouse(@Nonnull UUID houseId);

    /** Every record whose {@code worldName} is this world, housed or not, owner online or not. */
    @Nonnull List<AnimalRecord> inWorld(@Nonnull String worldName);

    /** A new record with a fresh id, saved. Does not touch any entity. */
    @Nonnull AnimalRecord create(@Nonnull String speciesId, @Nullable UUID ownerId, @Nonnull String worldName, boolean baby);

    /**
     * Makes {@code entity} the animal's one real incarnation: bumps the record's generation, stores the
     * entity's UUID, and puts the ranch marker component on the entity through {@code accessor}
     * (a CommandBuffer inside systems). Any other entity carrying this animal id becomes stale.
     */
    void bindEntity(@Nonnull AnimalRecord record, @Nonnull Ref<EntityStore> entity, @Nonnull UUID entityUuid, @Nonnull ComponentAccessor<EntityStore> accessor);

    /** True only for the entity the record currently names, at the record's generation. */
    boolean isCurrent(@Nonnull UUID animalId, @Nonnull UUID entityUuid, long generation);

    /** Schedules a save (debounced, off the world thread). Call after changing a record. */
    void markDirty(@Nonnull AnimalRecord record);

    /** Deletes the record for good (released, butchered, died). */
    void remove(@Nonnull UUID animalId);

    /** The role the animal's entity last had (CozyRanch_Raven, CozyRanch_Snail_Frost), or null when none was seen. */
    @Nullable
    default String lastRole(@Nonnull UUID animalId) {
        return null;
    }
}
