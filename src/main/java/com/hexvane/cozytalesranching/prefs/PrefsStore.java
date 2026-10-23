package com.hexvane.cozytalesranching.prefs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Every player's settings, in memory, and their files {@code <dir>/<uuid>.json} (written like the journal's: temp file,
 * forced, previous copy kept as {@code .prev}, atomic move). Only players who changed something have a file.
 */
public final class PrefsStore {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    static final String EXT = ".json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Nullable private final Path dir;
    private final Map<UUID, PlayerPrefs> prefs = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    /** Read on every sound without the lock: who has sounds or chat off. Usually empty. */
    private final Set<UUID> soundsOff = ConcurrentHashMap.newKeySet();
    private final Set<UUID> chatOff = ConcurrentHashMap.newKeySet();

    public PrefsStore(@Nullable Path dir) {
        this.dir = dir;
    }

    public boolean chat(@Nonnull UUID player) {
        return !chatOff.contains(player);
    }

    public boolean sounds(@Nonnull UUID player) {
        return !soundsOff.contains(player);
    }

    /** True while at least one player has sounds off, so sounds need a per-listener check at all. */
    public boolean anySoundsOff() {
        return !soundsOff.isEmpty();
    }

    @Nonnull
    public synchronized PlayerPrefs copy(@Nonnull UUID player) {
        PlayerPrefs p = prefs.get(player);
        if (p != null) return p.copy();
        PlayerPrefs fresh = new PlayerPrefs();
        fresh.Player = player.toString();
        return fresh;
    }

    /** Sets one toggle. Returns true when it changed (and will be saved). */
    public synchronized boolean set(@Nonnull UUID player, @Nonnull PlayerPrefs.Setting setting, boolean on) {
        PlayerPrefs p = prefs.computeIfAbsent(player, id -> {
            PlayerPrefs fresh = new PlayerPrefs();
            fresh.Player = id.toString();
            return fresh;
        });
        if (p.get(setting) == on) return false;
        p.set(setting, on);
        index(player, p);
        dirty.add(player);
        return true;
    }

    /** Flips one toggle and returns its new value. */
    public synchronized boolean toggle(@Nonnull UUID player, @Nonnull PlayerPrefs.Setting setting) {
        boolean on = !copy(player).get(setting);
        set(player, setting, on);
        return on;
    }

    public synchronized boolean hasDirty() {
        return !dirty.isEmpty();
    }

    private void index(@Nonnull UUID player, @Nonnull PlayerPrefs p) {
        if (p.Sounds) soundsOff.remove(player);
        else soundsOff.add(player);
        if (p.Chat) chatOff.remove(player);
        else chatOff.add(player);
    }

    /** Reads every file. Setup thread. */
    public void loadAll() {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> playerOf(p) != null && Files.isRegularFile(p)).sorted().toList()) {
                UUID player = playerOf(f);
                PlayerPrefs p = read(f);
                p.Player = player.toString();
                synchronized (this) {
                    prefs.put(player, p);
                    index(player, p);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list settings files in " + dir, e);
        }
    }

    /** Writes every changed file on the calling thread (never a world thread). Returns how many failed. */
    public int saveDirty() {
        if (dir == null) return 0;
        List<UUID> players;
        synchronized (this) {
            players = new ArrayList<>(dirty);
            dirty.clear();
        }
        int failed = 0;
        for (UUID player : players) {
            String json;
            synchronized (this) {
                PlayerPrefs p = prefs.get(player);
                if (p == null) continue;
                p.SavedAtMs = System.currentTimeMillis();
                json = GSON.toJson(p);
            }
            try {
                write(dir.resolve(player + EXT), json);
            } catch (IOException e) {
                failed++;
                synchronized (this) {
                    dirty.add(player);
                }
                LOG.log(Level.WARNING, "Could not save the ranch settings of " + player, e);
            }
        }
        return failed;
    }

    @Nullable
    static UUID playerOf(@Nonnull Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(EXT)) return null;
        try {
            return UUID.fromString(name.substring(0, name.length() - EXT.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void write(@Nonnull Path file, @Nonnull String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        if (Files.isRegularFile(file)) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".prev"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The file, else its previous copy, else the defaults (everything on). */
    @Nonnull
    static PlayerPrefs read(@Nonnull Path file) {
        for (Path p : new Path[] {file, file.resolveSibling(file.getFileName() + ".prev")}) {
            if (!Files.isRegularFile(p)) continue;
            try {
                return parse(Files.readString(p, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException ignored) {
                // try the next copy
            }
        }
        return new PlayerPrefs();
    }

    @Nonnull
    static PlayerPrefs parse(@Nonnull String text) {
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        PlayerPrefs p = GSON.fromJson(text, PlayerPrefs.class);
        if (p == null) throw new JsonParseException("empty file");
        return p;
    }
}
