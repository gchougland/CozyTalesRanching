package com.hexvane.cozytalesranching.config;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.util.Config;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.bson.BsonDocument;

/**
 * Keeps an existing config file up to date: the engine only writes a config file when there is none, so a setting added
 * in a later version would never show up in an older file. After loading, any setting the codec knows but the file
 * lacks is written back with its default; every value already in the file is kept as it was loaded.
 */
public final class ConfigDefaults {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private ConfigDefaults() {}

    /**
     * Writes the default config when the file is missing, or adds the settings it lacks. Call after the config is loaded.
     *
     * @return the settings that were added (empty when the file was complete or new)
     */
    @Nonnull
    public static <T> List<String> fill(@Nonnull Config<T> config, @Nonnull BuilderCodec<T> codec, @Nonnull Path file) {
        T loaded = config.get();
        if (!Files.exists(file)) {
            config.save().join();
            return List.of();
        }
        Set<String> present;
        try {
            present = BsonDocument.parse(Files.readString(file, StandardCharsets.UTF_8)).keySet();
        } catch (IOException | RuntimeException e) {
            // never overwrite a file we cannot read: the admin may be halfway through editing it
            LOG.log(Level.WARNING, "Could not read " + file.getFileName() + " to add new settings; leaving it as it is", e);
            return List.of();
        }
        List<String> missing = missingKeys(codec.encode(loaded, new ExtraInfo()).keySet(), present);
        if (!missing.isEmpty()) config.save().join();
        return missing;
    }

    /** The keys the codec writes that the file does not have, in the codec's order. */
    @Nonnull
    static List<String> missingKeys(@Nonnull Set<String> written, @Nonnull Set<String> present) {
        return written.stream().filter(k -> !present.contains(k)).toList();
    }
}
