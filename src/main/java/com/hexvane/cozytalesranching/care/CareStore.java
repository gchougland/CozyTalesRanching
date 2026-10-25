package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Every trough that holds or held food, by world and origin block, and the last trough meal of each animal.
 * One lock: world threads change it, the save thread serialises it. No engine types.
 */
final class CareStore {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    record Pos(int x, int y, int z) {}

    /** A trough the mod knows about. {@code stateDirty}: its block state may not match its contents yet. */
    static final class Trough {
        final String world;
        final Pos pos;
        final TroughContents contents;
        boolean stateDirty = true;

        Trough(@Nonnull String world, @Nonnull Pos pos, @Nonnull TroughContents contents) {
            this.world = world;
            this.pos = pos;
            this.contents = contents;
        }
    }

    record MealView(@Nonnull FoodKind kind, long atMs, long treatAtMs) {}

    record TroughView(@Nonnull String world, int x, int y, int z, @Nonnull TroughContents contents, boolean stateDirty) {}

    @Nullable private final Path dir;
    private final Map<String, Map<Pos, Trough>> troughs = new HashMap<>();
    private final Map<UUID, CareSaveFile.Meal> meals = new HashMap<>();
    private final Map<UUID, String> mealWorlds = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();

    CareStore(@Nullable Path dir) {
        this.dir = dir;
    }

    // ---- troughs ----

    @Nonnull
    synchronized Optional<TroughView> trough(@Nonnull String world, int x, int y, int z) {
        Trough t = troughs.getOrDefault(world, Map.of()).get(new Pos(x, y, z));
        return Optional.ofNullable(t == null ? null : view(t));
    }

    @Nonnull
    synchronized List<TroughView> troughsIn(@Nonnull String world) {
        List<TroughView> out = new ArrayList<>();
        for (Trough t : troughs.getOrDefault(world, Map.of()).values()) out.add(view(t));
        return out;
    }

    /** Adds to a trough, creating its entry. Returns how many went in. */
    synchronized int add(@Nonnull String world, int x, int y, int z, @Nonnull String item, int amount, @Nonnull ToIntFunction<String> maxStack) {
        Trough t = troughs.computeIfAbsent(world, w -> new LinkedHashMap<>())
            .computeIfAbsent(new Pos(x, y, z), p -> new Trough(world, p, new TroughContents()));
        boolean wasEmpty = t.contents.isEmpty();
        int put = t.contents.add(item, amount, maxStack);
        if (put > 0) {
            if (wasEmpty) t.stateDirty = true;
            dirty.add(world);
        }
        return put;
    }

    /** Takes one item out. Returns false if the trough or the item is not there. */
    synchronized boolean takeOne(@Nonnull String world, int x, int y, int z, @Nonnull String item) {
        Trough t = troughs.getOrDefault(world, Map.of()).get(new Pos(x, y, z));
        if (t == null || !t.contents.takeOne(item)) return false;
        if (t.contents.isEmpty()) t.stateDirty = true;
        dirty.add(world);
        return true;
    }

    /** Empties a trough; its entry stays until the block state shows empty. */
    @Nonnull
    synchronized List<TroughContents.Slot> takeAll(@Nonnull String world, int x, int y, int z) {
        Trough t = troughs.getOrDefault(world, Map.of()).get(new Pos(x, y, z));
        if (t == null) return List.of();
        List<TroughContents.Slot> out = t.contents.takeAll();
        if (!out.isEmpty()) {
            t.stateDirty = true;
            dirty.add(world);
        }
        return out;
    }

    /** Forgets a trough (broken). Returns what it held. */
    @Nonnull
    synchronized List<TroughContents.Slot> remove(@Nonnull String world, int x, int y, int z) {
        Map<Pos, Trough> m = troughs.get(world);
        Trough t = m == null ? null : m.remove(new Pos(x, y, z));
        if (t == null) return List.of();
        dirty.add(world);
        return t.contents.takeAll();
    }

    /** The block now shows the contents. An empty trough whose block is right is forgotten. */
    synchronized void stateShown(@Nonnull String world, int x, int y, int z) {
        Map<Pos, Trough> m = troughs.get(world);
        Trough t = m == null ? null : m.get(new Pos(x, y, z));
        if (t == null) return;
        t.stateDirty = false;
        if (t.contents.isEmpty()) {
            m.remove(t.pos);
            dirty.add(world);
        }
    }

    // ---- meals ----

    @Nonnull
    synchronized Optional<MealView> meal(@Nonnull UUID animalId) {
        CareSaveFile.Meal m = meals.get(animalId);
        return Optional.ofNullable(m == null ? null : new MealView(m.Kind, m.AtMs, m.TreatAtMs));
    }

    synchronized void recordMeal(@Nonnull UUID animalId, @Nonnull String world, @Nonnull FoodKind kind, long atMs) {
        CareSaveFile.Meal before = meals.get(animalId);
        long treatAt = kind == FoodKind.FAVORITE ? atMs : before == null ? 0L : before.TreatAtMs;
        meals.put(animalId, new CareSaveFile.Meal(kind, atMs, treatAt));
        String oldWorld = mealWorlds.put(animalId, world);
        if (oldWorld != null && !oldWorld.equals(world)) dirty.add(oldWorld);
        dirty.add(world);
    }

    synchronized void forgetMeal(@Nonnull UUID animalId) {
        if (meals.remove(animalId) == null) return;
        String world = mealWorlds.remove(animalId);
        if (world != null) dirty.add(world);
    }

    // ---- disk ----

    synchronized int troughCount() {
        return troughs.values().stream().mapToInt(Map::size).sum();
    }

    void loadAll() {
        if (dir == null || !Files.isDirectory(dir)) return;
        // a save cut off between its two renames is finished first, so the listing below sees it
        com.hexvane.cozytalesranching.persist.SafeWrite.healDir(dir);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(CareSaveFile.EXT) && Files.isRegularFile(p)).sorted().toList()) {
                load(CareSaveFile.read(f));
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list trough files in " + dir, e);
        }
    }

    synchronized void load(@Nonnull CareSaveFile.Data data) {
        String world = data.World;
        if (world.isEmpty()) return;
        Map<Pos, Trough> m = troughs.computeIfAbsent(world, w -> new LinkedHashMap<>());
        for (CareSaveFile.TroughEntry e : data.Troughs) {
            Pos p = new Pos(e.X, e.Y, e.Z);
            m.put(p, new Trough(world, p, e.Contents));
        }
        data.Meals.forEach((id, meal) -> {
            CareSaveFile.Meal have = meals.get(id);
            if (have == null || have.AtMs < meal.AtMs) {
                meals.put(id, meal);
                mealWorlds.put(id, world);
            }
        });
    }

    /** One world's data as it would be saved. */
    @Nonnull
    synchronized CareSaveFile.Data snapshot(@Nonnull String world, long nowMs) {
        CareSaveFile.Data d = new CareSaveFile.Data();
        d.World = world;
        d.SavedAtMs = nowMs;
        for (Trough t : troughs.getOrDefault(world, Map.of()).values()) {
            if (t.contents.isEmpty()) continue;
            CareSaveFile.TroughEntry e = new CareSaveFile.TroughEntry();
            e.X = t.pos.x();
            e.Y = t.pos.y();
            e.Z = t.pos.z();
            e.Contents = t.contents.copy();
            d.Troughs.add(e);
        }
        mealWorlds.forEach((id, w) -> {
            if (w.equals(world)) {
                CareSaveFile.Meal m = meals.get(id);
                d.Meals.put(id, new CareSaveFile.Meal(m.Kind, m.AtMs, m.TreatAtMs));
            }
        });
        return d;
    }

    synchronized boolean hasDirty() {
        return !dirty.isEmpty();
    }

    /** Writes every dirty world on the calling thread (never a world thread). Returns how many failed. */
    int saveDirty() {
        if (dir == null) return 0;
        List<String> worlds;
        synchronized (this) {
            worlds = new ArrayList<>(dirty);
            dirty.clear();
        }
        int failed = 0;
        for (String world : worlds) {
            String json = CareSaveFile.serialize(snapshot(world, System.currentTimeMillis()));
            try {
                CareSaveFile.write(dir.resolve(CareSaveFile.fileNameFor(world)), json);
            } catch (IOException e) {
                failed++;
                synchronized (this) {
                    dirty.add(world);
                }
                LOG.log(Level.WARNING, "Could not save troughs of world " + world, e);
            }
        }
        return failed;
    }

    @Nonnull
    private static TroughView view(@Nonnull Trough t) {
        return new TroughView(t.world, t.pos.x(), t.pos.y(), t.pos.z(), t.contents.copy(), t.stateDirty);
    }
}
