package com.hexvane.cozytalesranching.taming;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Feedings given to wild animals that are not tamed yet. In memory only, shared by world threads: a
 * half-befriended animal forgets after {@link TamingRules#TAME_PROGRESS_TIMEOUT_MS} or a restart, which is fine
 * for something that takes a few seconds.
 */
public final class TameProgress {
    private record Entry(UUID playerId, int points, long lastFedMs) {}

    private final Map<UUID, Entry> byEntity = new HashMap<>();

    /** Adds feedings from {@code playerId} and returns the total. Another player's earlier feedings do not count. */
    public synchronized int add(@Nonnull UUID entityUuid, @Nonnull UUID playerId, int points, long nowMs) {
        Entry e = byEntity.get(entityUuid);
        int base = e != null && e.playerId.equals(playerId) && nowMs - e.lastFedMs < TamingRules.TAME_PROGRESS_TIMEOUT_MS ? e.points : 0;
        int total = base + points;
        byEntity.put(entityUuid, new Entry(playerId, total, nowMs));
        if (byEntity.size() > 256) prune(nowMs);
        return total;
    }

    public synchronized void clear(@Nonnull UUID entityUuid) {
        byEntity.remove(entityUuid);
    }

    public synchronized void prune(long nowMs) {
        byEntity.values().removeIf(e -> nowMs - e.lastFedMs >= TamingRules.TAME_PROGRESS_TIMEOUT_MS);
    }

    synchronized int size() {
        return byEntity.size();
    }
}
