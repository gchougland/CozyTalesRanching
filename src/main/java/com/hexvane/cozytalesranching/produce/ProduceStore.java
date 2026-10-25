package com.hexvane.cozytalesranching.produce;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Products waiting in houses, interval progress per animal and the last pass time, per world in
 * {@code Produce/<world>.json} under the plugin data folder. One lock guards everything; saves are debounced on
 * their own thread and written like the house file ({@code .tmp} forced, previous copied to {@code .prev}, atomic move).
 */
final class ProduceStore {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    static final int FORMAT_VERSION = 1;
    static final String EXT = ".json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Products waiting in one house, with the point they drop at if the house goes. */
    static final class Stock {
        UUID HouseId;
        String World = "";
        double X;
        double Y;
        double Z;
        LinkedHashMap<String, Integer> Items = new LinkedHashMap<>();

        int total() {
            int n = 0;
            for (Integer v : Items.values()) if (v != null) n += v;
            return n;
        }
    }

    /** One world's file. Field names are the save format. */
    static final class Data {
        int FormatVersion = FORMAT_VERSION;
        String World = "";
        long SavedAtMs;
        long LastPassMs;
        List<Stock> Houses = new ArrayList<>();
        /** Animal id to product item id to the fraction of an interval done. */
        Map<UUID, Map<String, Double>> Progress = new LinkedHashMap<>();
        /** Products of houses that are gone, waiting for their drop point's chunk to be loaded. */
        List<Stock> PendingDrops = new ArrayList<>();

        Data normalise() {
            if (World == null) World = "";
            if (Houses == null) Houses = new ArrayList<>();
            if (Progress == null) Progress = new LinkedHashMap<>();
            if (PendingDrops == null) PendingDrops = new ArrayList<>();
            Houses.removeIf(s -> s == null || s.HouseId == null);
            PendingDrops.removeIf(s -> s == null);
            for (Stock s : Houses) fix(s);
            for (Stock s : PendingDrops) fix(s);
            Progress.values().removeIf(m -> m == null);
            return this;
        }

        private void fix(Stock s) {
            if (s.World == null || s.World.isEmpty()) s.World = World;
            if (s.Items == null) s.Items = new LinkedHashMap<>();
            s.Items.values().removeIf(v -> v == null || v <= 0);
        }
    }

    private final Path dir;
    private final DoubleSupplier debounceSeconds;
    private final Map<String, Data> worlds = new HashMap<>();
    private final Map<UUID, Stock> byHouse = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();
    /**
     * Worlds where only the pass clock or production progress changed, with when that first happened. Progress moves
     * every pass for every producing animal, so it is written with the next real change (an item made, taken or
     * dropped), at most {@link #PROGRESS_SAVE_MS} late on its own, and always at shutdown.
     */
    private final Map<String, Long> progressSince = new HashMap<>();
    static final long PROGRESS_SAVE_MS = TimeUnit.MINUTES.toMillis(5);
    private LongSupplier clock = System::currentTimeMillis;
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile boolean closed;

    ProduceStore(@Nonnull Path dir, @Nonnull DoubleSupplier debounceSeconds) {
        this.dir = dir;
        this.debounceSeconds = debounceSeconds;
        this.executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "CozyRanch-Produce");
            t.setDaemon(true);
            return t;
        });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);
    }

    synchronized void loadAll() {
        if (!Files.isDirectory(dir)) return;
        // a save cut off between its two renames is finished first, so the listing below sees it
        com.hexvane.cozytalesranching.persist.SafeWrite.healDir(dir);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(EXT)).sorted().toList()) {
                Data d = read(file);
                if (d == null) continue;
                worlds.put(d.World, d);
                for (Stock s : d.Houses) byHouse.put(s.HouseId, s);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list " + dir, e);
        }
        int items = 0;
        for (Stock s : byHouse.values()) items += s.total();
        LOG.info("Loaded ranch products: " + byHouse.size() + " houses, " + items + " items waiting, from " + dir);
    }

    @Nonnull
    private Data world(@Nonnull String world) {
        return worlds.computeIfAbsent(world, w -> {
            Data d = new Data();
            d.World = w;
            return d;
        });
    }

    synchronized long lastPass(@Nonnull String world) {
        Data d = worlds.get(world);
        return d == null ? 0 : d.LastPassMs;
    }

    synchronized void setLastPass(@Nonnull String world, long ms) {
        world(world).LastPassMs = ms;
        progressChanged(world);
    }

    /** For tests: the clock that times progress-only saves. */
    synchronized void setClock(@Nonnull LongSupplier clock) {
        this.clock = clock;
    }

    private void progressChanged(@Nonnull String world) {
        progressSince.putIfAbsent(world, clock.getAsLong());
    }

    /** Item id to count, in the order they first arrived. A copy. */
    @Nonnull
    synchronized Map<String, Integer> stored(@Nonnull UUID houseId) {
        Stock s = byHouse.get(houseId);
        return s == null || s.Items.isEmpty() ? Map.of() : new LinkedHashMap<>(s.Items);
    }

    synchronized int total(@Nonnull UUID houseId) {
        Stock s = byHouse.get(houseId);
        return s == null ? 0 : s.total();
    }

    /** Makes sure the house has an entry and records where its products drop. */
    synchronized void track(@Nonnull String world, @Nonnull UUID houseId, double x, double y, double z) {
        Stock s = byHouse.get(houseId);
        if (s == null) {
            s = new Stock();
            s.HouseId = houseId;
            s.World = world;
            world(world).Houses.add(s);
            byHouse.put(houseId, s);
            dirty.add(world);
        }
        if (s.X != x || s.Y != y || s.Z != z) {
            s.X = x;
            s.Y = y;
            s.Z = z;
            dirty.add(s.World);
        }
    }

    synchronized void add(@Nonnull UUID houseId, @Nonnull String item, int count) {
        Stock s = byHouse.get(houseId);
        if (s == null || count <= 0) return;
        s.Items.merge(item, count, Integer::sum);
        dirty.add(s.World);
    }

    /** Removes up to {@code count}; returns how many were removed. */
    synchronized int take(@Nonnull UUID houseId, @Nonnull String item, int count) {
        Stock s = byHouse.get(houseId);
        if (s == null || count <= 0) return 0;
        int have = s.Items.getOrDefault(item, 0);
        int took = Math.min(have, count);
        if (took <= 0) return 0;
        if (have - took <= 0) s.Items.remove(item);
        else s.Items.put(item, have - took);
        dirty.add(s.World);
        return took;
    }

    synchronized double progress(@Nonnull String world, @Nonnull UUID animalId, @Nonnull String item) {
        Data d = worlds.get(world);
        Map<String, Double> m = d == null ? null : d.Progress.get(animalId);
        Double v = m == null ? null : m.get(item);
        return v == null ? 0 : v;
    }

    synchronized void setProgress(@Nonnull String world, @Nonnull UUID animalId, @Nonnull String item, double value) {
        world(world).Progress.computeIfAbsent(animalId, k -> new LinkedHashMap<>()).put(item, value);
        progressChanged(world);
    }

    /** Forgets progress of animals that no longer produce here. */
    synchronized void keepProgress(@Nonnull String world, @Nonnull Predicate<UUID> keep) {
        Data d = worlds.get(world);
        if (d != null && d.Progress.keySet().removeIf(id -> !keep.test(id))) progressChanged(world);
    }

    @Nonnull
    synchronized List<UUID> houses(@Nonnull String world) {
        Data d = worlds.get(world);
        if (d == null) return List.of();
        List<UUID> out = new ArrayList<>(d.Houses.size());
        for (Stock s : d.Houses) out.add(s.HouseId);
        return out;
    }

    /** The house is gone: its entry leaves, and whatever it held waits as a pending drop. Returns that drop or null. */
    @Nullable
    synchronized Stock retire(@Nonnull UUID houseId) {
        Stock s = byHouse.remove(houseId);
        if (s == null) return null;
        Data d = world(s.World);
        d.Houses.remove(s);
        dirty.add(s.World);
        if (s.Items.isEmpty()) return null;
        d.PendingDrops.add(s);
        return s;
    }

    @Nonnull
    synchronized List<Stock> pendingDrops(@Nonnull String world) {
        Data d = worlds.get(world);
        return d == null ? List.of() : List.copyOf(d.PendingDrops);
    }

    synchronized void dropped(@Nonnull Stock s) {
        Data d = worlds.get(s.World);
        if (d != null && d.PendingDrops.remove(s)) dirty.add(s.World);
    }

    /** A save is due: a real change, or progress that has waited {@link #PROGRESS_SAVE_MS}. */
    synchronized boolean hasDirty() {
        return !dirty.isEmpty() || !progressDue(false).isEmpty();
    }

    /** Worlds whose progress-only changes are due ({@code all}: every one, for shutdown). */
    private Set<String> progressDue(boolean all) {
        if (progressSince.isEmpty()) return Set.of();
        long now = clock.getAsLong();
        Set<String> out = new HashSet<>();
        progressSince.forEach((w, since) -> {
            if (all || now - since >= PROGRESS_SAVE_MS) out.add(w);
        });
        return out;
    }

    void scheduleSave() {
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

    /** Writes every dirty world (and progress that is due) on the calling thread. Returns how many failed (they stay dirty). */
    int saveDirty() {
        return saveDirty(false);
    }

    /** As {@link #saveDirty()}; {@code allProgress} also writes progress that is not due yet (shutdown). */
    int saveDirty(boolean allProgress) {
        Map<String, String> json = new LinkedHashMap<>();
        synchronized (this) {
            Set<String> worldsToSave = new java.util.LinkedHashSet<>(dirty);
            worldsToSave.addAll(progressDue(allProgress));
            for (String w : worldsToSave) {
                Data d = world(w);
                d.SavedAtMs = System.currentTimeMillis();
                json.put(w, GSON.toJson(d));
                progressSince.remove(w);
            }
            dirty.clear();
        }
        int failed = 0;
        for (Map.Entry<String, String> e : json.entrySet()) {
            try {
                write(dir.resolve(fileNameFor(e.getKey())), e.getValue());
            } catch (IOException | RuntimeException ex) {
                failed++;
                LOG.log(Level.WARNING, "Could not save ranch products of world " + e.getKey(), ex);
                synchronized (this) {
                    dirty.add(e.getKey());
                }
            }
        }
        return failed;
    }

    void close() {
        if (!closing.compareAndSet(false, true)) return;
        closed = true;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int failed = saveDirty(true);
        if (failed > 0) LOG.severe(failed + " ranch product file(s) could not be saved at shutdown");
        else LOG.info("Saved ranch products.");
    }

    @Nonnull
    static String fileNameFor(@Nonnull String worldName) {
        String safe = worldName.replaceAll("[^A-Za-z0-9._-]", "_");
        return (safe.isEmpty() ? "_" : safe) + EXT;
    }

    static void write(@Nonnull Path file, @Nonnull String json) throws IOException {
        com.hexvane.cozytalesranching.persist.SafeWrite.write(file, json);
    }

    /** The file, else its previous good copy. An unreadable file is renamed aside, never deleted. */
    @Nullable
    static Data read(@Nonnull Path file) {
        com.hexvane.cozytalesranching.persist.SafeWrite.heal(file);
        Path prev = file.resolveSibling(file.getFileName() + ".prev");
        try {
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
        } catch (IOException ignored) {
            // the next write truncates it
        }
        try {
            return parse(file);
        } catch (IOException | RuntimeException e) {
            LOG.warning("Ranch product file problem: " + file.getFileName() + ": " + e.getMessage());
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
            } catch (IOException ignored) {
                // left in place
            }
        }
        if (Files.isRegularFile(prev)) {
            try {
                return parse(prev);
            } catch (IOException | RuntimeException e) {
                LOG.warning("Ranch product file problem: " + prev.getFileName() + ": " + e.getMessage());
            }
        }
        return null;
    }

    @Nonnull
    private static Data parse(@Nonnull Path p) throws IOException {
        String text = Files.readString(p, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        Data d = GSON.fromJson(text, Data.class);
        if (d == null) throw new JsonParseException("empty file");
        return d.normalise();
    }
}
