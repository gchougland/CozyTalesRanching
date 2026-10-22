package com.hexvane.cozytalesranching.house;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Every placed house. Implemented by the {@code house} role (persistent); {@link InMemoryHouseRegistry}
 * stands in until then. Get it with {@code Ranch.houses()}. World thread for writes.
 */
public interface HouseRegistry {
    @Nonnull Optional<HouseRecord> get(@Nonnull UUID houseId);

    /** The house whose origin block is at this position, if any. */
    @Nonnull Optional<HouseRecord> at(@Nonnull String worldName, int x, int y, int z);

    @Nonnull List<HouseRecord> ownedBy(@Nonnull UUID ownerId);

    @Nonnull List<HouseRecord> inWorld(@Nonnull String worldName);

    /** A new record with a fresh id, saved. Does not touch the world. */
    @Nonnull HouseRecord create(@Nonnull UUID ownerId, @Nonnull String speciesId, @Nonnull String blockId,
                                @Nonnull String worldName, int x, int y, int z, int rotation);

    void markDirty(@Nonnull HouseRecord house);

    /** Deletes the record. The caller has already moved residents out. */
    void remove(@Nonnull UUID houseId);
}
