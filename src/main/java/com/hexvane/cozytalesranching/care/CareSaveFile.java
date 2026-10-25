package com.hexvane.cozytalesranching.care;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
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

/**
 * One world's troughs and trough meals on disk: {@code Troughs/<world>.json} in the plugin data folder.
 * Written to {@code <file>.tmp}, forced, the previous file copied to {@code <file>.prev}, then moved over the
 * real file atomically (the same scheme as the animal registry). A file that cannot be read is renamed aside.
 */
final class CareSaveFile {
    static final int FORMAT_VERSION = 1;
    static final String EXT = ".json";
    private static final String TMP = ".tmp";
    private static final String PREV = ".prev";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Field names are the save format. */
    static final class Data {
        int FormatVersion = FORMAT_VERSION;
        String World = "";
        long SavedAtMs;
        List<TroughEntry> Troughs = new ArrayList<>();
        /** Last trough meal per animal eaten in this world. */
        Map<UUID, Meal> Meals = new LinkedHashMap<>();

        @Nonnull
        Data normalise() {
            if (World == null) World = "";
            if (Troughs == null) Troughs = new ArrayList<>();
            if (Meals == null) Meals = new LinkedHashMap<>();
            Troughs.removeIf(t -> t == null);
            for (TroughEntry t : Troughs) {
                if (t.Contents == null) t.Contents = new TroughContents();
                t.Contents.normalise();
            }
            Meals.values().removeIf(m -> m == null || m.Kind == null);
            return this;
        }
    }

    static final class TroughEntry {
        int X;
        int Y;
        int Z;
        TroughContents Contents = new TroughContents();
    }

    static final class Meal {
        FoodKind Kind = FoodKind.BASIC;
        long AtMs;
        /** When the animal last ate a favourite treat from a trough; 0 for never. */
        long TreatAtMs;

        Meal() {}

        Meal(@Nonnull FoodKind kind, long atMs, long treatAtMs) {
            Kind = kind;
            AtMs = atMs;
            TreatAtMs = treatAtMs;
        }
    }

    private CareSaveFile() {}

    @Nonnull
    static String fileNameFor(@Nonnull String worldName) {
        String safe = worldName.replaceAll("[^A-Za-z0-9._-]", "_");
        return (safe.isEmpty() ? "_" : safe) + EXT;
    }

    @Nonnull
    static String serialize(@Nonnull Data data) {
        return GSON.toJson(data);
    }

    static void write(@Nonnull Path file, @Nonnull String json) throws IOException {
        com.hexvane.cozytalesranching.persist.SafeWrite.write(file, json);
    }

    /** The main file, else the previous good copy, else empty. A leftover temp file is never trusted. */
    @Nonnull
    static Data read(@Nonnull Path file) {
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
        return new Data();
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
