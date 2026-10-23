package com.hexvane.cozytalesranching.rehome;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Rehome's settings until they live in {@code RanchConfig}: {@code ProtectFromHostileMobs} (default true) read from
 * an optional {@code Rehome.json} in the plugin data directory. Read at start and on /ranch reload, never per event.
 */
public final class RehomeSettings {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    static final String FILE = "Rehome.json";

    private static volatile boolean protectFromHostileMobs = true;

    private RehomeSettings() {}

    /** Damage from creatures to owned animals is cancelled when this or {@code RanchConfig.ProtectFromHostileMobs} is on. */
    public static boolean protectFromHostileMobs() {
        return protectFromHostileMobs;
    }

    static void load(@Nullable Path dataDirectory) {
        boolean value = true;
        Path file = dataDirectory == null ? null : dataDirectory.resolve(FILE);
        if (file != null && Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                value = parseProtect(JsonParser.parseReader(r));
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + file + "; hostile-mob protection stays on", e);
            }
        }
        protectFromHostileMobs = value;
    }

    static boolean parseProtect(@Nonnull JsonElement json) {
        if (!json.isJsonObject()) return true;
        JsonObject o = json.getAsJsonObject();
        JsonElement v = o.get("ProtectFromHostileMobs");
        return v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean() || v.getAsBoolean();
    }
}
