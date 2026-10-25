package com.hexvane.cozytalesranching.animal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One world's ranch animals on disk: {@code Animals/<world>.json} in the plugin data folder.
 * Written to {@code <file>.tmp}, forced to disk, the previous good file copied to {@code <file>.prev},
 * then moved over the real file atomically, so a crash at any point leaves a whole file behind.
 */
final class AnimalSaveFile {
    static final int FORMAT_VERSION = 1;
    static final String EXT = ".json";
    static final String TMP = ".tmp";
    static final String PREV = ".prev";

    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** The JSON document. Field names are the save format. */
    static final class Data {
        int FormatVersion = FORMAT_VERSION;
        String World = "";
        long SavedAtMs;
        List<AnimalRecord> Records = new ArrayList<>();
        /** Animal ids removed for good (released, butchered): an entity still carrying one is removed when it loads. Value: removal time. */
        Map<UUID, Long> Tombstones = new LinkedHashMap<>();
        /** Last NPC role seen for each animal, so recovery respawns the same kind of entity. */
        Map<UUID, String> Roles = new LinkedHashMap<>();

        Data normalise() {
            if (World == null) World = "";
            if (Records == null) Records = new ArrayList<>();
            if (Tombstones == null) Tombstones = new LinkedHashMap<>();
            if (Roles == null) Roles = new LinkedHashMap<>();
            Records.removeIf(r -> r == null || r.animalId == null);
            for (AnimalRecord r : Records) {
                if (r.traits == null) r.traits = new ArrayList<>();
                if (r.speciesId == null) r.speciesId = "";
                if (r.worldName == null || r.worldName.isEmpty()) r.worldName = World;
                if (r.state == null) r.state = AnimalRecord.State.ACTIVE;
            }
            return this;
        }
    }

    /** Where a load came from, so the caller can log a recovery. */
    enum Source { MAIN, PREVIOUS, NONE }

    record Loaded(@Nonnull Data data, @Nonnull Source source, @Nullable String problem) {}

    private AnimalSaveFile() {}

    @Nonnull
    static String fileNameFor(@Nonnull String worldName) {
        String safe = worldName.replaceAll("[^A-Za-z0-9._-]", "_");
        return (safe.isEmpty() ? "_" : safe) + EXT;
    }

    static boolean isMainFile(@Nonnull Path p) {
        String n = p.getFileName().toString();
        return n.endsWith(EXT) && Files.isRegularFile(p);
    }

    @Nonnull
    static String serialize(@Nonnull Data data) {
        return GSON.toJson(data);
    }

    /** Atomic replace. Throws on failure; the old file is untouched in that case. */
    static void write(@Nonnull Path file, @Nonnull String json) throws IOException {
        com.hexvane.cozytalesranching.persist.SafeWrite.write(file, json);
    }

    /**
     * Reads the main file, falling back to the previous good copy. A leftover temp file is the
     * remains of an interrupted write and is never trusted. A main file that cannot be read is
     * renamed aside (never deleted) so the next save cannot overwrite the evidence.
     */
    @Nonnull
    static Loaded read(@Nonnull Path file) {
        com.hexvane.cozytalesranching.persist.SafeWrite.heal(file);
        Path tmp = file.resolveSibling(file.getFileName() + TMP);
        Path prev = file.resolveSibling(file.getFileName() + PREV);
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // harmless: the next write truncates it
        }
        String problem = null;
        if (Files.isRegularFile(file)) {
            try {
                return new Loaded(parse(file), Source.MAIN, null);
            } catch (IOException | RuntimeException e) {
                problem = file.getFileName() + ": " + e.getMessage();
                try {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
                } catch (IOException ignored) {
                    // left in place; still not overwritten before a successful parse of prev below
                }
            }
        }
        if (Files.isRegularFile(prev)) {
            try {
                return new Loaded(parse(prev), Source.PREVIOUS, problem);
            } catch (IOException | RuntimeException e) {
                problem = (problem == null ? "" : problem + "; ") + prev.getFileName() + ": " + e.getMessage();
            }
        }
        return new Loaded(new Data(), Source.NONE, problem);
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
