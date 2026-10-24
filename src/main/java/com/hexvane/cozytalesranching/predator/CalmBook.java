package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.predator.CalmRules.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Every predator that is lured, calm or spooked right now, by entity UUID. Memory only: a restart forgets it, which is
 * what the design wants (Calm never outlives CalmMinutes). Read from the attitude provider and damage filter on the
 * world thread, written on the world thread; the map is concurrent so a command thread may read it too.
 */
public final class CalmBook {
    /** One predator's state. Fields are written on its world thread only. */
    public static final class Entry {
        public final UUID entity;
        public final String speciesId;
        public final String world;
        public volatile Phase phase;
        public volatile long untilMs;
        /** The bowl it is lured to or ate from (block coordinates). */
        public int bowlX;
        public int bowlY;
        public int bowlZ;
        public String baitItem;
        /** The player who startled it, for the flee target; null when unknown. */
        @Nullable
        public UUID startler;
        public boolean warnedRestless;
        /** A lured predator reached the bowl and its meal is queued for the world thread. */
        public volatile boolean eatQueued;
        /** Where a lured predator stood at the last one-second check, to notice a stalled walk. */
        public double lastX = Double.NaN;
        public double lastZ = Double.NaN;
        public int kicks;

        Entry(@Nonnull UUID entity, @Nonnull String speciesId, @Nonnull String world) {
            this.entity = entity;
            this.speciesId = speciesId;
            this.world = world;
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    /** Predators that gave up on a bowl, until when they are left alone. */
    private final Map<UUID, Long> gaveUp = new ConcurrentHashMap<>();

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Nullable
    public Entry get(@Nullable UUID entity) {
        return entity == null ? null : entries.get(entity);
    }

    @Nullable
    public Phase phase(@Nullable UUID entity) {
        Entry e = get(entity);
        return e == null ? null : e.phase;
    }

    @Nonnull
    public Entry lure(@Nonnull UUID entity, @Nonnull String speciesId, @Nonnull String world, int x, int y, int z, @Nonnull String bait, long nowMs) {
        Entry e = new Entry(entity, speciesId, world);
        e.phase = Phase.LURED;
        e.untilMs = CalmRules.lureUntil(nowMs);
        e.bowlX = x;
        e.bowlY = y;
        e.bowlZ = z;
        e.baitItem = bait;
        entries.put(entity, e);
        return e;
    }

    /** Starts or restarts Calm (an entry made by {@link #lure} keeps its bowl). */
    @Nonnull
    public Entry calm(@Nonnull UUID entity, @Nonnull String speciesId, @Nonnull String world, long untilMs) {
        Entry e = entries.computeIfAbsent(entity, id -> new Entry(id, speciesId, world));
        e.phase = Phase.CALM;
        e.untilMs = untilMs;
        e.startler = null;
        e.warnedRestless = false;
        return e;
    }

    /** A hand feeding: Calm lasts at least until {@code untilMs} from now. Returns false when it is not calm. */
    public boolean extend(@Nonnull UUID entity, long untilMs) {
        Entry e = entries.get(entity);
        if (e == null || e.phase != Phase.CALM) return false;
        if (untilMs > e.untilMs) {
            e.untilMs = untilMs;
            e.warnedRestless = false;
        }
        return true;
    }

    @Nullable
    public Entry spook(@Nonnull UUID entity, @Nullable UUID startler, long nowMs) {
        Entry e = entries.get(entity);
        if (e == null) return null;
        e.phase = Phase.SPOOKED;
        e.untilMs = CalmRules.spookUntil(nowMs);
        e.startler = startler;
        return e;
    }

    @Nullable
    public Entry remove(@Nullable UUID entity) {
        return entity == null ? null : entries.remove(entity);
    }

    public void gaveUp(@Nonnull UUID entity, long nowMs) {
        entries.remove(entity);
        gaveUp.put(entity, nowMs + CalmRules.GAVE_UP_COOLDOWN_SECONDS * 1000L);
    }

    /** Whether this predator may be lured now (not already in the book, not sulking after giving up). */
    public boolean canLure(@Nonnull UUID entity, long nowMs) {
        if (entries.containsKey(entity)) return false;
        Long until = gaveUp.get(entity);
        if (until == null) return true;
        if (nowMs >= until) {
            gaveUp.remove(entity);
            return true;
        }
        return false;
    }

    /** Whether some predator is already walking to this bowl. */
    public boolean bowlTaken(@Nonnull String world, int x, int y, int z) {
        for (Entry e : entries.values()) {
            if (e.phase == Phase.LURED && e.bowlX == x && e.bowlY == y && e.bowlZ == z && e.world.equals(world)) return true;
        }
        return false;
    }

    @Nonnull
    public List<Entry> inWorld(@Nonnull String world) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.world.equals(world)) out.add(e);
        return out;
    }

    public int size() {
        return entries.size();
    }
}
