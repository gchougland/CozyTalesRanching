package com.hexvane.cozytalesranching.breeding;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** The loaded {@link TraitBook}. Read at start (and on {@link #load} again for a reload); lookups are plain field reads. */
public final class TraitRegistry {
    static final String RESOURCE = "/Server/CozyTalesRanching/Traits.json";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Gson GSON = new Gson();

    private static volatile TraitBook book = new TraitBook().normalise();

    private TraitRegistry() {}

    @Nonnull
    public static TraitBook get() {
        return book;
    }

    public static synchronized void load(@Nonnull Path dataDirectory) {
        TraitBook loaded = null;
        Path override = dataDirectory.resolve("Traits.json");
        if (Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                loaded = read(r);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + override + "; using the bundled traits", e);
            }
        }
        if (loaded == null) {
            try (InputStream in = TraitRegistry.class.getResourceAsStream(RESOURCE)) {
                if (in == null) LOG.warning("Bundled Traits.json missing");
                else loaded = read(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read bundled Traits.json", e);
            }
        }
        if (loaded != null) book = loaded;
        LOG.info("Loaded " + book.Traits.size() + " ranch traits");
    }

    @Nonnull
    static TraitBook read(@Nonnull Reader reader) {
        TraitBook b = GSON.fromJson(reader, TraitBook.class);
        if (b == null) throw new IllegalArgumentException("empty traits file");
        return b.normalise();
    }
}
