package com.hexvane.cozytalesranching.houseui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** At most one go per player per interval (the house page's Refresh). Old entries are dropped as it is used. */
public final class RateLimit {
    private final long intervalMs;
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public RateLimit(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    /** True, and counts as a go, when this player's last go is at least the interval ago. */
    public boolean tryAcquire(@Nonnull UUID player, long nowMs) {
        if (last.size() > 256) last.values().removeIf(t -> nowMs - t >= intervalMs);
        Long before = last.get(player);
        if (before != null && nowMs - before < intervalMs) return false;
        last.put(player, nowMs);
        return true;
    }
}
