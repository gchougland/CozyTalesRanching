package com.hexvane.cozytalesranching.house;

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
 * One world's houses on disk: {@code Houses/<world>.json} in the plugin data folder. Written like the
 * animal file: {@code <file>.tmp} forced to disk, the previous good file copied to {@code <file>.prev},
 * then an atomic move over the real file.
 */
final class HouseSaveFile {
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
        List<HouseRecord> Houses = new ArrayList<>();
        /** Cycle day of the last nightfall fired per house, so a restart at night does not fire it twice. */
        Map<UUID, Long> Nightfall = new LinkedHashMap<>();

        Data normalise() {
            if (World == null) World = "";
            if (Houses == null) Houses = new ArrayList<>();
            if (Nightfall == null) Nightfall = new LinkedHashMap<>();
            Houses.removeIf(h -> h == null || h.houseId == null);
            for (HouseRecord h : Houses) {
                if (h.speciesId == null) h.speciesId = "";
                if (h.blockId == null) h.blockId = "";
                if (h.worldName == null || h.worldName.isEmpty()) h.worldName = World;
                if (h.habitatMissing == null) h.habitatMissing = new ArrayList<>();
            }
            return this;
        }
    }

    record Loaded(@Nonnull Data data, boolean fromPrevious, @Nullable String problem) {}

    private HouseSaveFile() {}

    @Nonnull
    static String fileNameFor(@Nonnull String worldName) {
        String safe = worldName.replaceAll("[^A-Za-z0-9._-]", "_");
        return (safe.isEmpty() ? "_" : safe) + EXT;
    }

    static void write(@Nonnull Path file, @Nonnull String json) throws IOException {
        com.hexvane.cozytalesranching.persist.SafeWrite.write(file, json);
    }

    /** The main file, else the previous good copy. An unreadable main file is renamed aside, never deleted. */
    @Nonnull
    static Loaded read(@Nonnull Path file) {
        com.hexvane.cozytalesranching.persist.SafeWrite.heal(file);
        Path tmp = file.resolveSibling(file.getFileName() + TMP);
        Path prev = file.resolveSibling(file.getFileName() + PREV);
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // the next write truncates it
        }
        String problem = null;
        if (Files.isRegularFile(file)) {
            try {
                return new Loaded(parse(file), false, null);
            } catch (IOException | RuntimeException e) {
                problem = file.getFileName() + ": " + e.getMessage();
                try {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
                } catch (IOException ignored) {
                    // left in place
                }
            }
        }
        if (Files.isRegularFile(prev)) {
            try {
                return new Loaded(parse(prev), true, problem);
            } catch (IOException | RuntimeException e) {
                problem = (problem == null ? "" : problem + "; ") + prev.getFileName() + ": " + e.getMessage();
            }
        }
        return new Loaded(new Data(), false, problem);
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
