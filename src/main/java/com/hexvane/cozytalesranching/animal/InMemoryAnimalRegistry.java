package com.hexvane.cozytalesranching.animal;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Stand-in registry that keeps records in memory only. Nothing survives a restart. */
public final class InMemoryAnimalRegistry implements AnimalRegistry {
    private final Map<UUID, AnimalRecord> records = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entityToAnimal = new ConcurrentHashMap<>();

    @Nonnull @Override
    public Optional<AnimalRecord> get(@Nonnull UUID animalId) {
        return Optional.ofNullable(records.get(animalId));
    }

    @Nonnull @Override
    public Optional<AnimalRecord> byEntity(@Nonnull UUID entityUuid) {
        UUID id = entityToAnimal.get(entityUuid);
        return id == null ? Optional.empty() : get(id);
    }

    @Nonnull @Override
    public List<AnimalRecord> ownedBy(@Nonnull UUID ownerId) {
        return records.values().stream().filter(r -> ownerId.equals(r.ownerId)).toList();
    }

    @Nonnull @Override
    public List<AnimalRecord> inHouse(@Nonnull UUID houseId) {
        return records.values().stream().filter(r -> houseId.equals(r.houseId)).toList();
    }

    @Nonnull @Override
    public AnimalRecord create(@Nonnull String speciesId, @Nullable UUID ownerId, @Nonnull String worldName, boolean baby) {
        AnimalRecord r = new AnimalRecord();
        r.animalId = UUID.randomUUID();
        r.speciesId = speciesId;
        r.ownerId = ownerId;
        r.worldName = worldName;
        r.baby = baby;
        r.tamedAtMs = System.currentTimeMillis();
        records.put(r.animalId, r);
        return r;
    }

    @Override
    public void bindEntity(@Nonnull AnimalRecord record, @Nonnull Ref<EntityStore> entity, @Nonnull UUID entityUuid, @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (record.entityUuid != null) entityToAnimal.remove(record.entityUuid);
        record.generation++;
        record.entityUuid = entityUuid;
        record.state = AnimalRecord.State.ACTIVE;
        entityToAnimal.put(entityUuid, record.animalId);
    }

    @Override
    public boolean isCurrent(@Nonnull UUID animalId, @Nonnull UUID entityUuid, long generation) {
        AnimalRecord r = records.get(animalId);
        return r != null && r.generation == generation && Objects.equals(r.entityUuid, entityUuid);
    }

    @Override
    public void markDirty(@Nonnull AnimalRecord record) {}

    @Override
    public void remove(@Nonnull UUID animalId) {
        AnimalRecord r = records.remove(animalId);
        if (r != null && r.entityUuid != null) entityToAnimal.remove(r.entityUuid);
    }
}
