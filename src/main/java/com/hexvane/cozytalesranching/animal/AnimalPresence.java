package com.hexvane.cozytalesranching.animal;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Which animals have their current incarnation loaded right now, and since when missing ones have
 * been missing. Memory only: rebuilt from entity adds after a restart.
 */
final class AnimalPresence {
    private static final Map<UUID, Ref<EntityStore>> loaded = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> missingSince = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> suspect = new ConcurrentHashMap<>();

    private AnimalPresence() {}

    static void put(@Nonnull UUID animalId, @Nonnull Ref<EntityStore> ref) {
        loaded.put(animalId, ref);
        missingSince.remove(animalId);
        suspect.remove(animalId);
    }

    /** The loaded current incarnation, or null. A ref that went invalid is dropped. */
    @Nullable
    static Ref<EntityStore> get(@Nonnull UUID animalId) {
        Ref<EntityStore> ref = loaded.get(animalId);
        if (ref != null && !ref.isValid()) {
            loaded.remove(animalId, ref);
            return null;
        }
        return ref;
    }

    static boolean isLoadedRef(@Nonnull UUID animalId, @Nonnull Ref<EntityStore> ref) {
        return ref.equals(loaded.get(animalId));
    }

    static void remove(@Nonnull UUID animalId) {
        loaded.remove(animalId);
        suspect.remove(animalId);
    }

    static boolean removeIfRef(@Nonnull UUID animalId, @Nonnull Ref<EntityStore> ref) {
        return loaded.remove(animalId, ref);
    }

    static void markMissing(@Nonnull UUID animalId, long now) {
        missingSince.putIfAbsent(animalId, now);
        suspect.remove(animalId);
    }

    /** When the animal went missing; a MISSING record never seen missing this session counts from now. */
    static long missingSince(@Nonnull UUID animalId, long now) {
        Long t = missingSince.putIfAbsent(animalId, now);
        return t == null ? now : t;
    }

    static int bumpSuspect(@Nonnull UUID animalId) {
        return suspect.merge(animalId, 1, Integer::sum);
    }

    static void clearSuspect(@Nonnull UUID animalId) {
        suspect.remove(animalId);
    }

    static void clearAll() {
        loaded.clear();
        missingSince.clear();
        suspect.clear();
    }
}
