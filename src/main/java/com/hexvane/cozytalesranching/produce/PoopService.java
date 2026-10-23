package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.produce.PoopRules.Decision;
import com.hexvane.cozytalesranching.produce.PoopRules.Pile;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Owned, active, loaded ranch animals now and then leave a poop pile under them by day, or add to a pile nearby.
 * World thread only. The piles themselves hold their count in the block state; this class keeps only a per-world
 * index of pile cells it has seen (rebuilt lazily per house after a restart by one scan of that house's area) and the
 * time each animal last made one. Nothing here is saved.
 */
final class PoopService {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    /** Cells above and below a house door scanned when a house's piles are first counted. */
    private static final int HOUSE_SCAN_DY = 4;
    private static final double DAY_START = 0.25;
    private static final double DAY_END = 0.75;

    /** Pile cells seen, per world. */
    private final Map<String, Set<Long>> piles = new ConcurrentHashMap<>();
    /** Houses whose area was scanned since start. */
    private final Set<UUID> scannedHouses = ConcurrentHashMap.newKeySet();
    /** Animal id to the last time it made or grew a pile. */
    private final Map<UUID, Long> lastPoop = new ConcurrentHashMap<>();
    private volatile String lastPass = "none yet";
    private volatile boolean log;

    record Outcome(@Nonnull String what, int x, int y, int z, int count) {}

    record PassResult(int eligible, int rolled, int placed, int merged, @Nonnull Map<String, Integer> skipped, int reads, long micros) {}

    void setLog(boolean on) {
        log = on;
    }

    @Nonnull
    String lastPass() {
        return lastPass;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    static int keyX(long k) { return (int) (k >> 38); }

    static int keyZ(long k) { return (int) ((k << 26) >> 38); }

    static int keyY(long k) { return (int) ((k << 52) >> 52); }

    @Nonnull
    private Set<Long> index(@Nonnull World world) {
        return piles.computeIfAbsent(world.getName(), w -> ConcurrentHashMap.newKeySet());
    }

    private static boolean isDay(@Nonnull Store<EntityStore> store) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) return true;
        double p = time.getDayProgress();
        return p >= DAY_START && p < DAY_END;
    }

    @Nullable
    private static Ref<EntityStore> live(@Nonnull World world, @Nonnull AnimalRecord r) {
        if (r.entityUuid == null) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(r.entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    /**
     * One pass over the houses of a world. {@code force} (the admin command) ignores the chance, cooldown, time of day,
     * the global switch and the per-pass limit, but never the ground, door, merge and house rules.
     */
    @Nonnull
    PassResult pass(@Nonnull World world, boolean force) {
        long t0 = System.nanoTime();
        PoopTuning t = PoopTuning.get();
        Map<String, Integer> skipped = new TreeMap<>();
        if (!world.isAlive() || (!t.Enabled && !force)) return new PassResult(0, 0, 0, 0, skipped, 0, 0);
        Store<EntityStore> store = world.getEntityStore().getStore();
        boolean day = isDay(store);
        long now = System.currentTimeMillis();
        double chance = PoopRules.passChance(t.ChancePerMinute, t.PassSeconds);
        PoopBlocks blocks = new PoopBlocks(world);
        int eligible = 0, rolled = 0, placed = 0, merged = 0;
        List<HouseRecord> houses = Ranch.houses().inWorld(world.getName());
        outer:
        for (HouseRecord house : houses) {
            for (AnimalRecord r : Ranch.animals().inHouse(house.houseId)) {
                if (!world.getName().equals(r.worldName)) continue;
                Ref<EntityStore> ref = live(world, r);
                PoopRules.Animal a = new PoopRules.Animal(r.ownerId != null, r.houseId != null, r.state == AnimalRecord.State.ACTIVE,
                    r.baby, t.speciesOn(r.speciesId), ref != null, day || force);
                if (!"ok".equals(PoopRules.mayPoop(a, t.Babies))) continue;
                eligible++;
                if (!force) {
                    if (!PoopRules.cooledDown(lastPoop.getOrDefault(r.animalId, 0L), now, t.CooldownMinutes)) continue;
                    if (ThreadLocalRandom.current().nextDouble() >= chance) continue;
                }
                rolled++;
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc == null) continue;
                Vector3d p = tc.getPosition();
                Outcome o = drop(world, blocks, house, (int) Math.floor(p.x), (int) Math.floor(p.y + 0.05), (int) Math.floor(p.z));
                switch (o.what()) {
                    case "place" -> placed++;
                    case "merge" -> merged++;
                    default -> skipped.merge(o.what(), 1, Integer::sum);
                }
                if (o.what().equals("place") || o.what().equals("merge")) {
                    lastPoop.put(r.animalId, now);
                    if (log) LOG.info("Ranch animal " + r.animalId + " (" + r.speciesId + ") poop " + o.what() + " at " + o.x() + " " + o.y() + " " + o.z() + " -> " + o.count());
                    if (!force && placed + merged >= t.MaxPilesPerPass) break outer;
                }
            }
        }
        long micros = (System.nanoTime() - t0) / 1000;
        PassResult res = new PassResult(eligible, rolled, placed, merged, skipped, blocks.reads, micros);
        if (rolled > 0 || force) lastPass = world.getName() + ": " + eligible + " eligible, " + rolled + " tried, " + placed + " placed, "
            + merged + " grown, skipped " + skipped + ", " + blocks.reads + " reads, " + micros + " us";
        return res;
    }

    /**
     * One dropping at the cell {@code (x,y,z)} (the cell the feet are in), for {@code house} (null: no house limit,
     * as for a player).
     */
    @Nonnull
    Outcome drop(@Nonnull World world, @Nonnull PoopBlocks blocks, @Nullable HouseRecord house, int x, int y, int z) {
        PoopTuning t = PoopTuning.get();
        if (!blocks.loaded(x, y, z)) return new Outcome("not_loaded", x, y, z, 0);
        List<Pile> near = blocks.scan(x, y, z, t.MergeRadius, 1);
        Set<Long> idx = index(world);
        for (Pile p : near) idx.add(key(p.x(), p.y(), p.z()));
        int housePiles = house == null ? -1 : housePiles(world, blocks, house);
        Decision d = PoopRules.decide(x, y, z, near, housePiles, t);
        if (d.kind() == PoopRules.Kind.MERGE) {
            Pile p = d.target();
            int n = p.count() + 1;
            if (!blocks.setCount(p.x(), p.y(), p.z(), n)) return new Outcome("write_failed", p.x(), p.y(), p.z(), p.count());
            return new Outcome("merge", p.x(), p.y(), p.z(), n);
        }
        if (d.kind() == PoopRules.Kind.SKIP) return new Outcome(d.reason(), x, y, z, 0);
        PoopRules.Ground g = blocks.ground(x, y, z);
        if (g == null) return new Outcome("not_loaded", x, y, z, 0);
        String ground = PoopRules.groundCheck(g, t.ExcludedGround);
        if (!"ok".equals(ground)) return new Outcome(ground, x, y, z, 0);
        for (HouseRecord h : Ranch.houses().inWorld(world.getName())) {
            if (Math.abs(h.doorY - y) <= 3 && PoopRules.nearDoor(x, z, h.doorX, h.doorZ, t.DoorClearance)) return new Outcome("near_door", x, y, z, 0);
        }
        if (!blocks.setCount(x, y, z, 1)) return new Outcome("write_failed", x, y, z, 0);
        idx.add(key(x, y, z));
        return new Outcome("place", x, y, z, 1);
    }

    /** Area counted as one house's: its home range plus the merge radius, as a square round the door. */
    private static int houseReach(@Nonnull PoopTuning t) {
        return Math.max(1, RanchConfig.get().homeRangeRadius()) + t.MergeRadius;
    }

    /**
     * Piles round a house. The first time after a start (and only when the door's section is loaded) the house's area
     * is scanned once to find piles from before; after that the index is enough, each entry checked with one read.
     */
    int housePiles(@Nonnull World world, @Nonnull PoopBlocks blocks, @Nonnull HouseRecord house) {
        PoopTuning t = PoopTuning.get();
        int reach = houseReach(t);
        int dx0 = (int) Math.floor(house.doorX), dy0 = (int) Math.floor(house.doorY), dz0 = (int) Math.floor(house.doorZ);
        Set<Long> idx = index(world);
        if (!scannedHouses.contains(house.houseId) && blocks.loaded(dx0, dy0, dz0)) {
            for (Pile p : blocks.scan(dx0, dy0, dz0, reach, HOUSE_SCAN_DY)) idx.add(key(p.x(), p.y(), p.z()));
            scannedHouses.add(house.houseId);
        }
        int n = 0;
        for (Iterator<Long> it = idx.iterator(); it.hasNext(); ) {
            long k = it.next();
            int x = keyX(k), y = keyY(k), z = keyZ(k);
            if (Math.abs(x - dx0) > reach || Math.abs(z - dz0) > reach || Math.abs(y - dy0) > HOUSE_SCAN_DY + 4) continue;
            if (!blocks.loaded(x, y, z)) {
                n++;
                continue;
            }
            if (blocks.count(x, y, z) > 0) n++;
            else it.remove();
        }
        return n;
    }

    /**
     * A player placed a pile item at this cell: with {@code PlayerPilesMerge}, it joins a pile nearby that is not full
     * (the new block goes, the old pile shows one more). World thread, after the placement.
     */
    @Nonnull
    Outcome afterPlayerPlaced(@Nonnull World world, int x, int y, int z) {
        PoopTuning t = PoopTuning.get();
        PoopBlocks blocks = new PoopBlocks(world);
        if (blocks.count(x, y, z) != 1) return new Outcome("gone", x, y, z, 0);
        Set<Long> idx = index(world);
        idx.add(key(x, y, z));
        if (!t.PlayerPilesMerge) return new Outcome("place", x, y, z, 1);
        List<Pile> near = blocks.scan(x, y, z, t.MergeRadius, 1);
        for (Pile p : near) idx.add(key(p.x(), p.y(), p.z()));
        Decision d = PoopRules.decide(x, y, z, near, -1, t, false);
        if (d.kind() != PoopRules.Kind.MERGE) return new Outcome("place", x, y, z, 1);
        Pile p = d.target();
        if (!blocks.clear(x, y, z)) return new Outcome("place", x, y, z, 1);
        idx.remove(key(x, y, z));
        blocks.setCount(p.x(), p.y(), p.z(), p.count() + 1);
        return new Outcome("merge", p.x(), p.y(), p.z(), p.count() + 1);
    }

    /** Known piles of a world with their current counts (loaded ones only; gone ones are dropped from the index). */
    @Nonnull
    List<Pile> known(@Nonnull World world) {
        PoopBlocks blocks = new PoopBlocks(world);
        for (HouseRecord h : Ranch.houses().inWorld(world.getName())) housePiles(world, blocks, h);
        List<Pile> out = new ArrayList<>();
        for (Iterator<Long> it = index(world).iterator(); it.hasNext(); ) {
            long k = it.next();
            int x = keyX(k), y = keyY(k), z = keyZ(k);
            if (!blocks.loaded(x, y, z)) continue;
            int n = blocks.count(x, y, z);
            if (n > 0) out.add(new Pile(x, y, z, n));
            else it.remove();
        }
        return out;
    }

    /** Forgets animals that are gone, so the cooldown map does not grow forever. */
    void prune() {
        lastPoop.keySet().removeIf(id -> Ranch.animals().get(id).isEmpty());
    }
}
