package com.hexvane.cozytalesranching.behaviour;

import com.hexvane.cozytalesranching.house.HouseRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Toy blocks around each house, found by a small scan around the door (a disc of {@code ToyScanRadius} columns,
 * 2 cells below to 3 above the door) at most once per {@code ToyScanSeconds} per house, and at most one house per
 * planner pass. The planner reads the cached list; nothing is scanned when an animal decides.
 */
final class ToyIndex {
    record Toy(int x, int y, int z, @Nonnull String behaviourId) {}

    private record Entry(long scannedAtMs, @Nonnull List<Toy> toys) {}

    private static final int BELOW = 2;
    private static final int ABOVE = 3;

    private final Map<UUID, Entry> byHouse = new ConcurrentHashMap<>();

    @Nonnull
    List<Toy> toys(@Nonnull UUID houseId) {
        Entry e = byHouse.get(houseId);
        return e == null ? List.of() : e.toys;
    }

    long scannedAtMs(@Nonnull UUID houseId) {
        Entry e = byHouse.get(houseId);
        return e == null ? 0 : e.scannedAtMs;
    }

    void invalidate() {
        byHouse.replaceAll((k, e) -> new Entry(0, e.toys));
    }

    /** Makes this house's list due now, so the next planner pass rescans it. */
    void expire(@Nonnull UUID houseId) {
        byHouse.computeIfPresent(houseId, (k, e) -> new Entry(0, e.toys));
    }

    void forget(@Nonnull UUID houseId) {
        byHouse.remove(houseId);
    }

    /** True if this house's list is older than the interval. */
    boolean due(@Nonnull UUID houseId, long nowMs, @Nonnull BehaviourConfig c) {
        return nowMs - scannedAtMs(houseId) >= (long) (c.ToyScanSeconds * 1000);
    }

    /** Scans one house now. Columns in sections not in memory are skipped. Returns the cells read. */
    int scan(@Nonnull WorldBlocks blocks, @Nonnull HouseRecord house, int radius, @Nonnull ToyLookup lookup, long nowMs) {
        int before = blocks.reads;
        int cx = (int) Math.floor(house.doorX), cy = (int) Math.floor(house.doorY), cz = (int) Math.floor(house.doorZ);
        List<Toy> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int y = cy - BELOW; y <= cy + ABOVE; y++) {
                    int x = cx + dx, z = cz + dz;
                    String id = blocks.block(x, y, z);
                    if (id == null) continue;
                    String behaviour = lookup.toyBehaviour(id);
                    if (behaviour != null && !blocks.filler(x, y, z)) found.add(new Toy(x, y, z, behaviour));
                }
            }
        }
        byHouse.put(house.houseId, new Entry(nowMs, List.copyOf(found)));
        return blocks.reads - before;
    }

    /** The nearest toy of this behaviour in the house's list, or null. */
    @Nullable
    Toy nearest(@Nonnull UUID houseId, @Nonnull String behaviourId, double x, double z) {
        Toy best = null;
        double bestD = Double.MAX_VALUE;
        for (Toy t : toys(houseId)) {
            if (!t.behaviourId().equals(behaviourId)) continue;
            double ddx = t.x() + 0.5 - x, ddz = t.z() + 0.5 - z;
            double d = ddx * ddx + ddz * ddz;
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    interface ToyLookup {
        @Nullable String toyBehaviour(@Nonnull String baseBlockId);
    }
}
