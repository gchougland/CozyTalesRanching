package com.hexvane.cozytalesranching.breeding;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Ids of baby records per world, so the grow-up pass never walks every animal. Filled at birth and rebuilt once a
 * minute per world from the world's houses and its online players' animals (which also picks up babies from before a
 * restart and tamed wild babies). Memory only.
 */
final class BabyIndex {
    private final Map<String, Set<UUID>> byWorld = new ConcurrentHashMap<>();

    void add(@Nonnull String world, @Nonnull UUID animalId) {
        byWorld.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet()).add(animalId);
    }

    void remove(@Nonnull String world, @Nonnull UUID animalId) {
        Set<UUID> s = byWorld.get(world);
        if (s != null) s.remove(animalId);
    }

    @Nonnull
    Set<UUID> inWorld(@Nonnull String world) {
        Set<UUID> s = byWorld.get(world);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    int size(@Nonnull String world) {
        Set<UUID> s = byWorld.get(world);
        return s == null ? 0 : s.size();
    }
}
