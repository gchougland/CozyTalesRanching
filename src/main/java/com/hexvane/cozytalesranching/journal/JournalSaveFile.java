package com.hexvane.cozytalesranching.journal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One player's journal on disk: {@code Journal/<uuid>.json} in the plugin data folder. Written to {@code <file>.tmp},
 * forced, the previous file copied to {@code <file>.prev}, then moved over the real file atomically. A file that cannot
 * be read is renamed aside and the previous copy is tried.
 */
final class JournalSaveFile {
    static final String EXT = ".json";
    private static final String TMP = ".tmp";
    private static final String PREV = ".prev";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private JournalSaveFile() {}

    @Nonnull
    static String fileNameFor(@Nonnull UUID player) {
        return player + EXT;
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

    @Nonnull
    static String serialize(@Nonnull PlayerJournal journal) {
        return GSON.toJson(journal);
    }

    static void write(@Nonnull Path file, @Nonnull String json) throws IOException {
        com.hexvane.cozytalesranching.persist.SafeWrite.write(file, json);
    }

    /** The main file, else the previous good copy, else an empty journal. A leftover temp file is never trusted. */
    @Nonnull
    static PlayerJournal read(@Nonnull Path file) {
        com.hexvane.cozytalesranching.persist.SafeWrite.heal(file);
        Path tmp = file.resolveSibling(file.getFileName() + TMP);
        Path prev = file.resolveSibling(file.getFileName() + PREV);
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // the next write truncates it
        }
        if (Files.isRegularFile(file)) {
            try {
                return parse(file);
            } catch (IOException | RuntimeException e) {
                try {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
                } catch (IOException ignored) {
                    // left in place; prev is tried next
                }
            }
        }
        if (Files.isRegularFile(prev)) {
            try {
                return parse(prev);
            } catch (IOException | RuntimeException ignored) {
                // fall through to empty
            }
        }
        return new PlayerJournal();
    }

    @Nonnull
    static PlayerJournal parse(@Nonnull String text) {
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        PlayerJournal j = GSON.fromJson(text, PlayerJournal.class);
        if (j == null) throw new JsonParseException("empty file");
        return j.normalise();
    }

    @Nonnull
    private static PlayerJournal parse(@Nonnull Path p) throws IOException {
        return parse(Files.readString(p, StandardCharsets.UTF_8));
    }
}
