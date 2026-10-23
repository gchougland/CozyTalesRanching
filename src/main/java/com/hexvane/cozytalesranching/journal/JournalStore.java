package com.hexvane.cozytalesranching.journal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Every player's journal, in memory. One lock: world threads change it, the save thread serialises it. */
final class JournalStore {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    @Nullable private final Path dir;
    private final Map<UUID, PlayerJournal> journals = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    JournalStore(@Nullable Path dir) {
        this.dir = dir;
    }

    /** Changes one player's journal; it is saved later if the change says anything happened. */
    @Nonnull
    synchronized JournalRules.Change apply(@Nonnull UUID player, @Nonnull Function<PlayerJournal, JournalRules.Change> edit) {
        PlayerJournal j = journals.computeIfAbsent(player, p -> {
            PlayerJournal fresh = new PlayerJournal();
            fresh.Player = p.toString();
            return fresh;
        });
        JournalRules.Change change = edit.apply(j);
        if (change.any()) dirty.add(player);
        return change;
    }

    /** A copy to read without the lock. Empty for a player with no journal yet. */
    @Nonnull
    synchronized PlayerJournal copy(@Nonnull UUID player) {
        PlayerJournal j = journals.get(player);
        if (j != null) return j.copy();
        PlayerJournal fresh = new PlayerJournal();
        fresh.Player = player.toString();
        return fresh;
    }

    /** Forgets everything one player found out (admin). */
    synchronized void reset(@Nonnull UUID player) {
        PlayerJournal fresh = new PlayerJournal();
        fresh.Player = player.toString();
        journals.put(player, fresh);
        dirty.add(player);
    }

    synchronized int size() {
        return journals.size();
    }

    synchronized boolean hasDirty() {
        return !dirty.isEmpty();
    }

    /** Reads every journal file. Setup thread. */
    void loadAll() {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> JournalSaveFile.playerOf(p) != null && Files.isRegularFile(p)).sorted().toList()) {
                UUID player = JournalSaveFile.playerOf(f);
                PlayerJournal j = JournalSaveFile.read(f);
                j.Player = player.toString();
                synchronized (this) {
                    journals.put(player, j);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list journal files in " + dir, e);
        }
    }

    /** Writes every changed journal on the calling thread (never a world thread). Returns how many failed. */
    int saveDirty() {
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
                PlayerJournal j = journals.get(player);
                if (j == null) continue;
                j.SavedAtMs = System.currentTimeMillis();
                json = JournalSaveFile.serialize(j);
            }
            try {
                JournalSaveFile.write(dir.resolve(JournalSaveFile.fileNameFor(player)), json);
            } catch (IOException e) {
                failed++;
                synchronized (this) {
                    dirty.add(player);
                }
                LOG.log(Level.WARNING, "Could not save the journal of " + player, e);
            }
        }
        return failed;
    }
}
