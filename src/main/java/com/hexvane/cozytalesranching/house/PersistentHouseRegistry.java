package com.hexvane.cozytalesranching.house;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * The real {@link HouseRegistry}: houses indexed by id, world and origin block, saved per world a few
 * seconds after the last change on its own background thread, flushed at shutdown. One lock guards the
 * maps; record fields are changed by world threads as the shared class allows.
 */
public final class PersistentHouseRegistry implements HouseRegistry {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private final Path dir;
    private final DoubleSupplier debounceSeconds;
    private final Map<UUID, HouseRecord> byId = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> byWorld = new HashMap<>();
    private final Map<String, UUID> byOrigin = new HashMap<>();
    private final Map<UUID, Long> nightfall = new HashMap<>();
    private final Set<String> dirtyWorlds = new HashSet<>();
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile boolean closed;

    public PersistentHouseRegistry(@Nonnull Path dir, @Nonnull DoubleSupplier debounceSeconds) {
        this.dir = dir;
        this.debounceSeconds = debounceSeconds;
        this.executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "CozyRanch-Houses");
            t.setDaemon(true);
            return t;
        });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);
    }

    /** Reads every world's file. Call once before worlds start. */
    public synchronized void load() {
        if (!Files.isDirectory(dir)) return;
        // a save cut off between its two renames is finished first, so the listing below sees it
        com.hexvane.cozytalesranching.persist.SafeWrite.healDir(dir);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(HouseSaveFile.EXT)).sorted().toList()) {
                HouseSaveFile.Loaded loaded = HouseSaveFile.read(file);
                if (loaded.problem() != null) LOG.warning("House file problem: " + loaded.problem() + (loaded.fromPrevious() ? " (loaded the previous copy)" : ""));
                for (HouseRecord h : loaded.data().Houses) index(h);
                for (Map.Entry<UUID, Long> e : loaded.data().Nightfall.entrySet()) {
                    if (byId.containsKey(e.getKey()) && e.getValue() != null) nightfall.put(e.getKey(), e.getValue());
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list " + dir, e);
        }
        LOG.info("Loaded " + byId.size() + " ranch houses from " + dir);
    }

    @Nonnull @Override
    public synchronized Optional<HouseRecord> get(@Nonnull UUID houseId) {
        return Optional.ofNullable(byId.get(houseId));
    }

    @Nonnull @Override
    public synchronized Optional<HouseRecord> at(@Nonnull String worldName, int x, int y, int z) {
        UUID id = byOrigin.get(originKey(worldName, x, y, z));
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Nonnull @Override
    public synchronized List<HouseRecord> ownedBy(@Nonnull UUID ownerId) {
        List<HouseRecord> out = new ArrayList<>();
        for (HouseRecord h : byId.values()) if (ownerId.equals(h.ownerId)) out.add(h);
        return out;
    }

    @Nonnull @Override
    public synchronized List<HouseRecord> inWorld(@Nonnull String worldName) {
        Set<UUID> ids = byWorld.get(worldName);
        if (ids == null) return List.of();
        List<HouseRecord> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            HouseRecord h = byId.get(id);
            if (h != null) out.add(h);
        }
        return out;
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
        synchronized (this) {
            index(h);
            dirtyWorlds.add(worldName);
        }
        scheduleSave();
        return h;
    }

    @Override
    public void markDirty(@Nonnull HouseRecord house) {
        synchronized (this) {
            dirtyWorlds.add(house.worldName);
        }
        scheduleSave();
    }

    @Override
    public void remove(@Nonnull UUID houseId) {
        synchronized (this) {
            HouseRecord h = byId.remove(houseId);
            if (h == null) return;
            Set<UUID> ids = byWorld.get(h.worldName);
            if (ids != null) ids.remove(houseId);
            byOrigin.remove(originKey(h.worldName, h.x, h.y, h.z), houseId);
            nightfall.remove(houseId);
            dirtyWorlds.add(h.worldName);
        }
        scheduleSave();
    }

    /** Cycle day of the last nightfall fired for this house, or Long.MIN_VALUE. */
    synchronized long nightfallDay(@Nonnull UUID houseId) {
        return nightfall.getOrDefault(houseId, Long.MIN_VALUE);
    }

    void setNightfallDay(@Nonnull HouseRecord house, long day) {
        synchronized (this) {
            nightfall.put(house.houseId, day);
            dirtyWorlds.add(house.worldName);
        }
        scheduleSave();
    }

    synchronized int size() {
        return byId.size();
    }

    private void index(@Nonnull HouseRecord h) {
        byId.put(h.houseId, h);
        byWorld.computeIfAbsent(h.worldName, w -> new LinkedHashSet<>()).add(h.houseId);
        byOrigin.put(originKey(h.worldName, h.x, h.y, h.z), h.houseId);
    }

    @Nonnull
    private static String originKey(@Nonnull String world, int x, int y, int z) {
        return world + '|' + x + '|' + y + '|' + z;
    }

    private void scheduleSave() {
        if (closed || !saveScheduled.compareAndSet(false, true)) return;
        long delayMs = (long) (Math.max(1.0, debounceSeconds.getAsDouble()) * 1000);
        try {
            executor.schedule(this::saveNow, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            saveScheduled.set(false);
        }
    }

    private void saveNow() {
        saveScheduled.set(false);
        if (saveDirty() > 0) scheduleSave();
    }

    /** Writes every dirty world now on the calling thread. Returns how many worlds failed (they stay dirty). */
    int saveDirty() {
        Map<String, String> jsonByWorld = new LinkedHashMap<>();
        synchronized (this) {
            for (String world : dirtyWorlds) {
                HouseSaveFile.Data data = new HouseSaveFile.Data();
                data.World = world;
                data.SavedAtMs = System.currentTimeMillis();
                for (UUID id : byWorld.getOrDefault(world, Set.of())) {
                    HouseRecord h = byId.get(id);
                    if (h == null) continue;
                    data.Houses.add(h);
                    Long night = nightfall.get(id);
                    if (night != null) data.Nightfall.put(id, night);
                }
                jsonByWorld.put(world, HouseSaveFile.GSON.toJson(data));
            }
            dirtyWorlds.clear();
        }
        int failed = 0;
        for (Map.Entry<String, String> e : jsonByWorld.entrySet()) {
            try {
                HouseSaveFile.write(dir.resolve(HouseSaveFile.fileNameFor(e.getKey())), e.getValue());
            } catch (IOException | RuntimeException ex) {
                failed++;
                LOG.log(Level.WARNING, "Could not save houses of world " + e.getKey(), ex);
                synchronized (this) {
                    dirtyWorlds.add(e.getKey());
                }
            }
        }
        return failed;
    }

    /** Writes everything dirty on the calling thread and stops the background thread. For shutdown. */
    public void close() {
        if (!closing.compareAndSet(false, true)) return;
        closed = true;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int failed = saveDirty();
        if (failed > 0) LOG.severe(failed + " ranch house world file(s) could not be saved at shutdown");
        else LOG.info("Saved ranch houses.");
    }
}
