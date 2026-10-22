package com.hexvane.cozytalesranching.house;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Stand-in until the house role installs its persistent registry. Nothing is saved. */
public final class InMemoryHouseRegistry implements HouseRegistry {
    private final Map<UUID, HouseRecord> houses = new ConcurrentHashMap<>();

    @Nonnull @Override
    public Optional<HouseRecord> get(@Nonnull UUID houseId) {
        return Optional.ofNullable(houses.get(houseId));
    }

    @Nonnull @Override
    public Optional<HouseRecord> at(@Nonnull String worldName, int x, int y, int z) {
        return houses.values().stream()
            .filter(h -> h.worldName.equals(worldName) && h.x == x && h.y == y && h.z == z)
            .findFirst();
    }

    @Nonnull @Override
    public List<HouseRecord> ownedBy(@Nonnull UUID ownerId) {
        return houses.values().stream().filter(h -> ownerId.equals(h.ownerId)).toList();
    }

    @Nonnull @Override
    public List<HouseRecord> inWorld(@Nonnull String worldName) {
        return houses.values().stream().filter(h -> h.worldName.equals(worldName)).toList();
    }

    @Nonnull @Override
    public HouseRecord create(@Nonnull UUID ownerId, @Nonnull String speciesId, @Nonnull String blockId,
                              @Nonnull String worldName, int x, int y, int z, int rotation) {
        HouseRecord h = new HouseRecord();
        h.houseId = UUID.randomUUID();
        h.ownerId = ownerId;
        h.speciesId = speciesId;
        h.blockId = blockId;
        h.worldName = worldName;
        h.x = x;
        h.y = y;
        h.z = z;
        h.rotation = rotation;
        h.doorX = x + 0.5;
        h.doorY = y;
        h.doorZ = z + 0.5;
        h.placedAtMs = System.currentTimeMillis();
        houses.put(h.houseId, h);
        return h;
    }

    @Override
    public void markDirty(@Nonnull HouseRecord house) {}

    @Override
    public void remove(@Nonnull UUID houseId) {
        houses.remove(houseId);
    }
}
