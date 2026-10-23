package com.hexvane.cozytalesranching.wildlife;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Which entities are visitors this mod spawned, when they leave, and each house's visit timing. Its own save,
 * {@code <data>/Visitors/<world>.json}, written on the wildlife thread: {@code .tmp} forced, the old file kept as
 * {@code .prev}, then an atomic move; an unreadable file is kept as {@code .corrupt-<ms>} and {@code .prev} read.
 * One lock; no engine types.
 */
public final class VisitorStore {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final int FORMAT_VERSION = 1;

    /** One visitor. Field names are the save format. */
    public static final class Visitor {
        public UUID entityUuid;
        public UUID houseId;
        public UUID ownerId;
        public String speciesId = "";
        public String roleId = "";
        public boolean rare;
        public String worldName = "";
        public long arrivedAtMs;
        /** When its stay ends and it starts walking off. */
        public long leaveAtMs;
        /** When it started walking off; 0 while it is still visiting. */
        public long leavingSinceMs;
        /** Last place it was seen; tells an unloaded visitor from one that is gone. */
        public double x;
        public double y;
        public double z;

        public boolean leaving() {
            return leavingSinceMs > 0;
        }
    }

    /** Visit timing for one house. */
    public static final class HouseState {
        public long nextVisitAtMs;
        public Map<String, Long> lastVisitBySpecies = new HashMap<>();
        public long lastRareAtMs;
    }

    static final class FileData {
        int FormatVersion = FORMAT_VERSION;
        String World = "";
        long SavedAtMs;
        List<Visitor> Visitors = new ArrayList<>();
        Map<String, HouseState> Houses = new LinkedHashMap<>();
    }

    private final Path directory;
    private final Map<UUID, Visitor> visitors = new LinkedHashMap<>();
    private final Map<UUID, HouseState> houses = new HashMap<>();
    private final Map<UUID, String> houseWorlds = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();

    public VisitorStore(@Nonnull Path dataDirectory) {
        this.directory = dataDirectory.resolve("Visitors");
    }

    // ---- visitors ----

    public synchronized void add(@Nonnull Visitor v) {
        visitors.put(v.entityUuid, v);
        dirty.add(v.worldName);
    }

    @Nonnull
    public synchronized Optional<Visitor> get(@Nonnull UUID entityUuid) {
        return Optional.ofNullable(visitors.get(entityUuid));
    }

    public synchronized boolean isVisitor(@Nonnull UUID entityUuid) {
        return visitors.containsKey(entityUuid);
    }

    @Nonnull
    public synchronized List<Visitor> inWorld(@Nonnull String worldName) {
        return visitors.values().stream().filter(v -> worldName.equals(v.worldName)).toList();
    }

    @Nonnull
    public synchronized List<Visitor> all() {
        return List.copyOf(visitors.values());
    }

    public synchronized void forget(@Nonnull UUID entityUuid) {
        Visitor v = visitors.remove(entityUuid);
        if (v != null) dirty.add(v.worldName);
    }

    public synchronized void markDirty(@Nonnull String worldName) {
        dirty.add(worldName);
    }

    // ---- houses ----

    @Nonnull
    public synchronized HouseState house(@Nonnull UUID houseId, @Nonnull String worldName) {
        houseWorlds.put(houseId, worldName);
        return houses.computeIfAbsent(houseId, id -> new HouseState());
    }

    /** Drops timing for houses that no longer exist in this world. */
    public synchronized void retainHouses(@Nonnull String worldName, @Nonnull Set<UUID> existing) {
        boolean removed = houses.keySet().removeIf(id -> worldName.equals(houseWorlds.get(id)) && !existing.contains(id));
        houseWorlds.keySet().retainAll(houses.keySet());
        if (removed) dirty.add(worldName);
    }

    // ---- persistence ----

    /** Reads every world file. Call once at start, before worlds tick. */
    public synchronized void load() {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                FileData data = read(file);
                if (data == null) continue;
                for (Visitor v : data.Visitors) {
                    if (v != null && v.entityUuid != null) visitors.put(v.entityUuid, v);
                }
                for (Map.Entry<String, HouseState> e : data.Houses.entrySet()) {
                    try {
                        UUID id = UUID.fromString(e.getKey());
                        HouseState s = e.getValue() == null ? new HouseState() : e.getValue();
                        if (s.lastVisitBySpecies == null) s.lastVisitBySpecies = new HashMap<>();
                        houses.put(id, s);
                        houseWorlds.put(id, data.World == null ? "" : data.World);
                    } catch (IllegalArgumentException ignored) {
                        // not a house id; skip
                    }
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list " + directory, e);
        }
    }

    @Nullable
    private static FileData read(@Nonnull Path file) {
        Path prev = sibling(file, ".prev");
        try {
            return parse(file);
        } catch (IOException | RuntimeException e) {
            try {
                Files.move(file, sibling(file, ".corrupt-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // keep going with .prev
            }
            LOG.log(Level.WARNING, "Visitor save " + file + " unreadable; trying " + prev, e);
        }
        if (Files.isRegularFile(prev)) {
            try {
                return parse(prev);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Visitor save " + prev + " unreadable too", e);
            }
        }
        return null;
    }

    @Nonnull
    private static FileData parse(@Nonnull Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            FileData d = GSON.fromJson(r, FileData.class);
            if (d == null) throw new IOException("empty file");
            if (d.Visitors == null) d.Visitors = new ArrayList<>();
            if (d.Houses == null) d.Houses = new LinkedHashMap<>();
            return d;
        }
    }

    /** The JSON of each dirty world, taken under the lock; the dirty set is cleared. */
    @Nonnull
    synchronized Map<String, String> drainDirty() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String world : dirty) {
            FileData d = new FileData();
            d.World = world;
            d.SavedAtMs = System.currentTimeMillis();
            for (Visitor v : visitors.values()) if (world.equals(v.worldName)) d.Visitors.add(v);
            for (Map.Entry<UUID, HouseState> e : houses.entrySet()) {
                if (world.equals(houseWorlds.get(e.getKey()))) d.Houses.put(e.getKey().toString(), e.getValue());
            }
            out.put(world, GSON.toJson(d));
        }
        dirty.clear();
        return out;
    }

    synchronized boolean hasDirty() {
        return !dirty.isEmpty();
    }

    /** Writes the given world JSON. Off the world thread. Failed worlds are marked dirty again. */
    void write(@Nonnull Map<String, String> worlds) {
        for (Map.Entry<String, String> e : worlds.entrySet()) {
            try {
                writeAtomically(fileFor(e.getKey()), e.getValue());
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Could not save visitors of world " + e.getKey(), ex);
                markDirty(e.getKey());
            }
        }
    }

    @Nonnull
    Path fileFor(@Nonnull String worldName) {
        return directory.resolve(worldName.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    private static void writeAtomically(@Nonnull Path file, @Nonnull String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = sibling(file, ".tmp");
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             Writer w = java.nio.channels.Channels.newWriter(ch, StandardCharsets.UTF_8)) {
            w.write(json);
            w.flush();
            ch.force(true);
        }
        if (Files.isRegularFile(file)) Files.copy(file, sibling(file, ".prev"), StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Nonnull
    private static Path sibling(@Nonnull Path file, @Nonnull String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }
}
