package com.hexvane.cozytalesranching.animal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Every record with its indexes, the tombstones of removed animals, and which worlds need saving.
 * No engine types here, so all of it runs in unit tests. One lock: worlds tick on their own threads.
 */
final class AnimalStore {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    static final long TOMBSTONE_KEEP_MS = TimeUnit.DAYS.toMillis(180);

    /** What the registry decides about an entity carrying a marker. */
    enum Verdict {
        /** The one real incarnation. */
        CURRENT,
        /** Newer than the saved record (the server stopped before the record was saved): the record catches up to it. */
        CURRENT_CAUGHT_UP,
        /** An older or copied incarnation, or a removed animal: remove it silently. */
        STALE,
        /** No record and no tombstone: the record was never saved. The caller rebuilds one. */
        UNKNOWN
    }

    record AdoptResult(@Nonnull AnimalRecord record, boolean created) {}

    private record Tombstone(@Nonnull String world, long atMs) {}

    private final Path dir;
    private final Map<UUID, AnimalRecord> records = new HashMap<>();
    private final Map<UUID, UUID> byEntity = new HashMap<>();
    private final Map<UUID, Set<UUID>> byOwner = new HashMap<>();
    private final Map<UUID, Set<UUID>> byHouse = new HashMap<>();
    private final Map<String, Set<UUID>> byWorld = new HashMap<>();
    /** What each record was indexed under, so a changed field can be moved in the index. */
    private final Map<UUID, Indexed> indexed = new HashMap<>();
    private final Map<UUID, Tombstone> tombstones = new HashMap<>();
    private final Map<UUID, String> roles = new HashMap<>();
    private final Set<String> dirtyWorlds = new LinkedHashSet<>();
    /** Worlds whose file could not be read; their files are only replaced once a save succeeds. */
    private final Set<String> loadProblems = new LinkedHashSet<>();

    private record Indexed(@Nullable UUID entity, @Nullable UUID owner, @Nullable UUID house, @Nonnull String world) {}

    AnimalStore(@Nonnull Path dir) {
        this.dir = dir;
    }

    @Nonnull
    Path dir() {
        return dir;
    }

    // ---- reads

    @Nullable
    synchronized AnimalRecord get(@Nonnull UUID animalId) {
        return records.get(animalId);
    }

    @Nullable
    synchronized AnimalRecord byEntity(@Nonnull UUID entityUuid) {
        UUID id = byEntity.get(entityUuid);
        return id == null ? null : records.get(id);
    }

    @Nonnull
    synchronized List<AnimalRecord> ownedBy(@Nonnull UUID ownerId) {
        return collect(byOwner.get(ownerId));
    }

    @Nonnull
    synchronized List<AnimalRecord> inHouse(@Nonnull UUID houseId) {
        return collect(byHouse.get(houseId));
    }

    @Nonnull
    synchronized List<AnimalRecord> inWorld(@Nonnull String worldName) {
        return collect(byWorld.get(worldName));
    }

    synchronized int size() {
        return records.size();
    }

    synchronized boolean isTombstoned(@Nonnull UUID animalId) {
        return tombstones.containsKey(animalId);
    }

    @Nullable
    synchronized String role(@Nonnull UUID animalId) {
        return roles.get(animalId);
    }

    synchronized boolean isCurrent(@Nonnull UUID animalId, @Nonnull UUID entityUuid, long generation) {
        AnimalRecord r = records.get(animalId);
        return r != null && r.generation == generation && entityUuid.equals(r.entityUuid);
    }

    private List<AnimalRecord> collect(@Nullable Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<AnimalRecord> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            AnimalRecord r = records.get(id);
            if (r != null) out.add(r);
        }
        return out;
    }

    // ---- writes

    @Nonnull
    synchronized AnimalRecord create(@Nonnull String speciesId, @Nullable UUID ownerId, @Nonnull String worldName, boolean baby) {
        return createWithId(UUID.randomUUID(), speciesId, ownerId, worldName, baby);
    }

    @Nonnull
    synchronized AnimalRecord createWithId(@Nonnull UUID animalId, @Nonnull String speciesId, @Nullable UUID ownerId, @Nonnull String worldName, boolean baby) {
        AnimalRecord r = new AnimalRecord();
        r.animalId = animalId;
        r.speciesId = speciesId;
        r.ownerId = ownerId;
        r.worldName = worldName;
        r.baby = baby;
        long now = System.currentTimeMillis();
        r.tamedAtMs = now;
        if (baby) r.bornAtMs = now;
        records.put(animalId, r);
        tombstones.remove(animalId);
        reindex(r);
        return r;
    }

    /**
     * The adoption step: the record for this entity, creating an unowned adopted one only if none
     * exists. Safe when the entity's add is seen twice before its marker lands.
     */
    @Nonnull
    synchronized AdoptResult adoptOrGet(@Nonnull UUID entityUuid, @Nonnull String speciesId, @Nonnull String worldName, boolean baby) {
        AnimalRecord existing = byEntity(entityUuid);
        if (existing != null) return new AdoptResult(existing, false);
        AnimalRecord r = create(speciesId, null, worldName, baby);
        r.adopted = true;
        r.entityUuid = entityUuid;
        reindex(r);
        return new AdoptResult(r, true);
    }

    /** New incarnation: bumps the generation and names the entity. Returns the generation for its marker. */
    synchronized long bind(@Nonnull AnimalRecord r, @Nonnull UUID entityUuid) {
        r.generation++;
        r.entityUuid = entityUuid;
        r.state = AnimalRecord.State.ACTIVE;
        reindex(r);
        return r.generation;
    }

    /**
     * Decides about an entity that carries a marker. Only {@link Verdict#CURRENT_CAUGHT_UP} changes
     * the record, and only its generation and entity UUID: a marker can only carry a higher
     * generation than the record if the record's save was lost, since generations only grow.
     */
    @Nonnull
    synchronized Verdict judge(@Nonnull UUID animalId, long generation, @Nonnull UUID entityUuid) {
        AnimalRecord r = records.get(animalId);
        if (r == null) return tombstones.containsKey(animalId) ? Verdict.STALE : Verdict.UNKNOWN;
        if (generation > r.generation) {
            r.generation = generation;
            r.entityUuid = entityUuid;
            if (r.state != AnimalRecord.State.STORED) r.state = AnimalRecord.State.ACTIVE;
            reindex(r);
            return Verdict.CURRENT_CAUGHT_UP;
        }
        if (generation == r.generation && entityUuid.equals(r.entityUuid)) return Verdict.CURRENT;
        return Verdict.STALE;
    }

    /** Rebuilds a record whose save was lost, from what the entity's marker knows. Unowned, adopted. */
    @Nonnull
    synchronized AnimalRecord recreate(@Nonnull UUID animalId, long generation, @Nonnull UUID entityUuid, @Nonnull String speciesId, @Nonnull String worldName, boolean baby) {
        AnimalRecord existing = records.get(animalId);
        if (existing != null) return existing;
        AnimalRecord r = createWithId(animalId, speciesId, null, worldName, baby);
        r.adopted = true;
        r.generation = generation;
        r.entityUuid = entityUuid;
        reindex(r);
        return r;
    }

    synchronized void setRole(@Nonnull UUID animalId, @Nonnull String role) {
        AnimalRecord r = records.get(animalId);
        if (r == null) return;
        if (!role.equals(roles.put(animalId, role))) dirtyWorlds.add(r.worldName);
    }

    /** After a field changed: moves the record in the indexes and marks its world (and its old world) for saving. */
    synchronized void touch(@Nonnull AnimalRecord r) {
        if (records.get(r.animalId) != r) return;
        reindex(r);
    }

    synchronized void remove(@Nonnull UUID animalId) {
        AnimalRecord r = records.remove(animalId);
        if (r == null) return;
        unindex(animalId);
        roles.remove(animalId);
        tombstones.put(animalId, new Tombstone(r.worldName, System.currentTimeMillis()));
        dirtyWorlds.add(r.worldName);
    }

    private void reindex(@Nonnull AnimalRecord r) {
        if (r.worldName == null) r.worldName = "";
        Indexed old = indexed.get(r.animalId);
        Indexed now = new Indexed(r.entityUuid, r.ownerId, r.houseId, r.worldName);
        dirtyWorlds.add(r.worldName);
        if (now.equals(old)) return;
        if (old != null) {
            if (old.entity != null && !Objects.equals(old.entity, now.entity)) byEntity.remove(old.entity, r.animalId);
            if (old.owner != null && !Objects.equals(old.owner, now.owner)) drop(byOwner, old.owner, r.animalId);
            if (old.house != null && !Objects.equals(old.house, now.house)) drop(byHouse, old.house, r.animalId);
            if (!old.world.equals(now.world)) {
                drop(byWorld, old.world, r.animalId);
                dirtyWorlds.add(old.world);
            }
        }
        if (now.entity != null) byEntity.put(now.entity, r.animalId);
        if (now.owner != null) byOwner.computeIfAbsent(now.owner, k -> new LinkedHashSet<>()).add(r.animalId);
        if (now.house != null) byHouse.computeIfAbsent(now.house, k -> new LinkedHashSet<>()).add(r.animalId);
        byWorld.computeIfAbsent(now.world, k -> new LinkedHashSet<>()).add(r.animalId);
        indexed.put(r.animalId, now);
    }

    private void unindex(@Nonnull UUID animalId) {
        Indexed old = indexed.remove(animalId);
        if (old == null) return;
        if (old.entity != null) byEntity.remove(old.entity, animalId);
        if (old.owner != null) drop(byOwner, old.owner, animalId);
        if (old.house != null) drop(byHouse, old.house, animalId);
        drop(byWorld, old.world, animalId);
    }

    private static <K> void drop(@Nonnull Map<K, Set<UUID>> index, @Nonnull K key, @Nonnull UUID id) {
        Set<UUID> s = index.get(key);
        if (s == null) return;
        s.remove(id);
        if (s.isEmpty()) index.remove(key);
    }

    // ---- disk

    synchronized boolean hasDirty() {
        return !dirtyWorlds.isEmpty();
    }

    @Nonnull
    synchronized List<String> takeDirty() {
        List<String> out = new ArrayList<>(dirtyWorlds);
        dirtyWorlds.clear();
        return out;
    }

    synchronized void markWorldDirty(@Nonnull String worldName) {
        dirtyWorlds.add(worldName);
    }

    /** The JSON for one world, built under the lock so it is one consistent picture of the indexes. */
    @Nonnull
    synchronized String serialize(@Nonnull String worldName) {
        AnimalSaveFile.Data d = new AnimalSaveFile.Data();
        d.World = worldName;
        d.SavedAtMs = System.currentTimeMillis();
        d.Records = new ArrayList<>(inWorld(worldName));
        d.Records.sort((a, b) -> a.animalId.compareTo(b.animalId));
        long cutoff = d.SavedAtMs - TOMBSTONE_KEEP_MS;
        tombstones.entrySet().removeIf(e -> e.getValue().atMs < cutoff);
        Map<UUID, Long> tomb = new LinkedHashMap<>();
        tombstones.forEach((id, t) -> {
            if (t.world.equals(worldName)) tomb.put(id, t.atMs);
        });
        d.Tombstones = tomb;
        Map<UUID, String> r = new LinkedHashMap<>();
        for (AnimalRecord rec : d.Records) {
            String role = roles.get(rec.animalId);
            if (role != null) r.put(rec.animalId, role);
        }
        d.Roles = r;
        return AnimalSaveFile.serialize(d);
    }

    /** Writes one world's file. Off the world thread only. */
    void save(@Nonnull String worldName) throws IOException {
        String json = serialize(worldName);
        AnimalSaveFile.write(dir.resolve(AnimalSaveFile.fileNameFor(worldName)), json);
        synchronized (this) {
            loadProblems.remove(worldName);
        }
    }

    /** Saves every dirty world; a world that fails stays dirty. Returns how many failed. */
    int saveDirty() {
        int failed = 0;
        for (String world : takeDirty()) {
            try {
                save(world);
            } catch (IOException | RuntimeException e) {
                failed++;
                markWorldDirty(world);
                LOG.log(Level.WARNING, "Could not save ranch animals for world " + world, e);
            }
        }
        return failed;
    }

    /** Reads every world file. Called once at start, before any world adds entities. */
    synchronized void loadAll() {
        if (!Files.isDirectory(dir)) return;
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(AnimalSaveFile::isMainFile).sorted().toList();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not list " + dir + "; ranch animals not loaded", e);
            return;
        }
        Set<Path> seen = new LinkedHashSet<>(files);
        // a world whose main file was renamed aside earlier still has its .prev copy
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(AnimalSaveFile.EXT + AnimalSaveFile.PREV))
                .map(p -> p.resolveSibling(p.getFileName().toString().substring(0, p.getFileName().toString().length() - AnimalSaveFile.PREV.length())))
                .forEach(seen::add);
        } catch (IOException ignored) {
            // the main files are enough
        }
        for (Path file : seen) loadFile(file);
    }

    private void loadFile(@Nonnull Path file) {
        AnimalSaveFile.Loaded loaded = AnimalSaveFile.read(file);
        AnimalSaveFile.Data d = loaded.data();
        if (loaded.problem() != null) {
            loadProblems.add(d.World);
            LOG.warning("Ranch animal file problem (" + loaded.source() + " used): " + loaded.problem());
        }
        if (loaded.source() == AnimalSaveFile.Source.NONE) return;
        for (AnimalRecord r : d.Records) {
            AnimalRecord prior = records.get(r.animalId);
            if (prior != null && prior.generation >= r.generation) continue;
            if (prior != null) unindex(prior.animalId);
            records.put(r.animalId, r);
            reindex(r);
        }
        d.Tombstones.forEach((id, at) -> {
            if (id != null && at != null && !records.containsKey(id)) tombstones.put(id, new Tombstone(d.World, at));
        });
        d.Roles.forEach((id, role) -> {
            if (id != null && role != null && records.containsKey(id)) roles.put(id, role);
        });
        // a world rebuilt from .prev is saved soon so the main file is whole again
        if (loaded.source() == AnimalSaveFile.Source.MAIN) dirtyWorlds.remove(d.World);
        for (AnimalRecord r : d.Records) if (loaded.source() == AnimalSaveFile.Source.MAIN) dirtyWorlds.remove(r.worldName);
    }

    @Nonnull
    synchronized Set<String> loadProblems() {
        return Set.copyOf(loadProblems);
    }
}
