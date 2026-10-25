package com.hexvane.cozytalesranching.predator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The index of baited bowls: world, block position and the one bait item each holds. Only baited bowls are listed,
 * so the lure pass walks this small list and never scans blocks. Saved to {@code <data>/BaitBowls.json} on a daemon
 * thread, two seconds after the last change (written to a temp file, then moved over the old one), and on shutdown.
 * The block's own state (empty, Meat, Fish, Berries) is what players see; this file remembers the exact item.
 */
public final class BowlStore {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** A baited bowl. */
    public record Bowl(@Nonnull String world, int x, int y, int z, @Nonnull String item) {}

    /** The save format. */
    static final class Data {
        int FormatVersion = 1;
        List<Row> Bowls = new ArrayList<>();
    }

    static final class Row {
        String World;
        int X;
        int Y;
        int Z;
        String Item;
    }

    private final Map<String, Map<Long, Bowl>> byWorld = new ConcurrentHashMap<>();
    @Nullable
    private final Path file;
    @Nullable
    private final ScheduledExecutorService saver;
    private final AtomicBoolean savePending = new AtomicBoolean();

    /** A store with no file, for tests. */
    public BowlStore() {
        this(null);
    }

    public BowlStore(@Nullable Path file) {
        this.file = file;
        this.saver = file == null ? null : Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CozyRanch-BaitBowls");
            t.setDaemon(true);
            return t;
        });
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    @Nullable
    public String bait(@Nonnull String world, int x, int y, int z) {
        Map<Long, Bowl> m = byWorld.get(world);
        Bowl b = m == null ? null : m.get(key(x, y, z));
        return b == null ? null : b.item();
    }

    /** Puts the bait in; false when the bowl already holds some. */
    public boolean fill(@Nonnull String world, int x, int y, int z, @Nonnull String item) {
        Map<Long, Bowl> m = byWorld.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
        if (m.putIfAbsent(key(x, y, z), new Bowl(world, x, y, z, item)) != null) return false;
        scheduleSave();
        return true;
    }

    /** Takes the bait out; returns it, or null when the bowl was empty. */
    @Nullable
    public String take(@Nonnull String world, int x, int y, int z) {
        Map<Long, Bowl> m = byWorld.get(world);
        Bowl b = m == null ? null : m.remove(key(x, y, z));
        if (b == null) return null;
        scheduleSave();
        return b.item();
    }

    @Nonnull
    public List<Bowl> inWorld(@Nonnull String world) {
        Map<Long, Bowl> m = byWorld.get(world);
        return m == null ? List.of() : new ArrayList<>(m.values());
    }

    public int size() {
        int n = 0;
        for (Map<Long, Bowl> m : byWorld.values()) n += m.size();
        return n;
    }

    // ---- disk ----

    public void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
            Data d = GSON.fromJson(text, Data.class);
            if (d == null || d.Bowls == null) return;
            for (Row r : d.Bowls) {
                if (r == null || r.World == null || r.Item == null || r.Item.isBlank()) continue;
                byWorld.computeIfAbsent(r.World, w -> new ConcurrentHashMap<>()).put(key(r.X, r.Y, r.Z), new Bowl(r.World, r.X, r.Y, r.Z, r.Item));
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("Could not read " + file + ": " + e.getMessage() + "; baited bowls start empty");
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
            } catch (IOException ignored) {
                // left in place
            }
        }
    }

    @Nonnull
    String serialize() {
        Data d = new Data();
        for (Map<Long, Bowl> m : byWorld.values()) {
            for (Bowl b : m.values()) {
                Row r = new Row();
                r.World = b.world();
                r.X = b.x();
                r.Y = b.y();
                r.Z = b.z();
                r.Item = b.item();
                d.Bowls.add(r);
            }
        }
        return GSON.toJson(d);
    }

    private void scheduleSave() {
        if (saver == null || !savePending.compareAndSet(false, true)) return;
        saver.schedule(() -> {
            savePending.set(false);
            saveNow();
        }, 2, TimeUnit.SECONDS);
    }

    /** Writes the file on the calling thread. */
    public synchronized void saveNow() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, serialize(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warning("Could not save " + file + ": " + e.getMessage());
        }
    }

    /** For shutdown: the last write, then the thread ends. */
    public void close() {
        saveNow();
        if (saver != null) saver.shutdownNow();
    }
}
