package com.hexvane.cozytalesranching.housedisplay;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Failed display spawns: a house whose shown animal was removed as it was added (another system took it) waits
 * {@link #WAIT_MS} before it tries again, instead of every check, and each model's failure is reported once.
 * Pure (times are passed in), so it is unit tested.
 */
final class DisplayBackoff {
    /** How long a house waits after a failed spawn before it tries again. */
    static final long WAIT_MS = 5 * 60 * 1000L;

    private final Map<UUID, Long> retryAt = new ConcurrentHashMap<>();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    /** True while the house is waiting after a failed spawn. */
    boolean waiting(@Nonnull UUID houseId, long nowMs) {
        Long at = retryAt.get(houseId);
        if (at == null) return false;
        if (nowMs < at) return true;
        retryAt.remove(houseId, at);
        return false;
    }

    /** A spawn on this house failed: it waits before trying again. */
    void failed(@Nonnull UUID houseId, long nowMs) {
        retryAt.put(houseId, nowMs + WAIT_MS);
    }

    /** True the first time a model fails (since the start or the last {@link #clear()}): report it then only. */
    boolean firstFailure(@Nonnull String modelId) {
        return reported.add(modelId);
    }

    /** Forgets the house (taken down or removed). */
    void forget(@Nonnull UUID houseId) {
        retryAt.remove(houseId);
    }

    /** Everything tries again and reports again (a reload). */
    void clear() {
        retryAt.clear();
        reported.clear();
    }
}
