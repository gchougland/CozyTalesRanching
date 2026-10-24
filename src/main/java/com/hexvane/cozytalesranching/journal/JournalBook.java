package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.species.SpeciesOverlay;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The journal's own data per species, from {@code Server/CozyTalesRanching/Journal.json} (or {@code Journal.json} in
 * the plugin data folder, which replaces it). A species without an entry still gets a page, with the defaults.
 * Field names are the JSON keys.
 */
public final class JournalBook {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    static final String RESOURCE = "/Server/CozyTalesRanching/Journal.json";
    static final String ART_DIR = "UI/Custom/CozyRanch/Journal/";
    private static final Gson GSON = new GsonBuilder().create();

    public int FormatVersion = 1;
    /** Shown for a species whose art is missing, and as its silhouette. */
    public String UnknownArt = ART_DIR + "Unknown.png";
    public List<SpeciesPage> Species = List.of();

    public static final class SpeciesPage {
        public String Id = "";
        /** Contents and next/previous order; lower first. */
        public int Order = 1000;
        @Nullable public String DescriptionKey;
        /** One line for the rare variant, shown once one has been seen. */
        @Nullable public String RareKey;
        /** Paths under the pack's {@code Common/} folder, as {@code AssetImage.AssetPath} takes them. */
        @Nullable public String Art;
        @Nullable public String Silhouette;
        /** Behaviour ids the page lists (as {@code ???} until seen). Each has {@code cozyranch_journal.behaviour.<id>}. */
        public List<String> Behaviours = List.of();
    }

    private transient Map<String, SpeciesPage> byId = Map.of();

    @Nullable private static volatile JournalBook current;

    @Nonnull
    public static JournalBook get() {
        JournalBook b = current;
        return b != null ? b : new JournalBook().normalise();
    }

    static void install(@Nonnull JournalBook book) {
        current = book;
    }

    /** The bundled file, replaced by {@code <data>/Journal.json} when the server owner provides one. */
    @Nonnull
    public static JournalBook load(@Nullable Path dataDirectory) {
        Path override = dataDirectory == null ? null : dataDirectory.resolve("Journal.json");
        if (override != null && Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                return read(r);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + override + "; using the bundled journal file", e);
            }
        }
        try (InputStream in = JournalBook.class.getResourceAsStream(RESOURCE)) {
            if (in != null) return read(SpeciesOverlay.merged(in, "Journal"));
            LOG.warning("Bundled Journal.json missing");
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read bundled Journal.json", e);
        }
        return new JournalBook().normalise();
    }

    @Nonnull
    static JournalBook read(@Nonnull Reader reader) {
        JournalBook b = GSON.fromJson(reader, JournalBook.class);
        return (b == null ? new JournalBook() : b).normalise();
    }

    @Nonnull
    JournalBook normalise() {
        if (UnknownArt == null || UnknownArt.isBlank()) UnknownArt = ART_DIR + "Unknown.png";
        Map<String, SpeciesPage> ids = new LinkedHashMap<>();
        if (Species != null) {
            for (SpeciesPage p : Species) {
                if (p == null || p.Id == null || p.Id.isBlank()) continue;
                p.Id = p.Id.toLowerCase(Locale.ROOT);
                if (p.Behaviours == null) p.Behaviours = List.of();
                p.Behaviours = p.Behaviours.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
                ids.put(p.Id, p);
            }
        }
        Species = List.copyOf(ids.values());
        byId = Map.copyOf(ids);
        return this;
    }

    /** This species' entry, or the defaults. */
    @Nonnull
    public SpeciesPage page(@Nonnull String speciesId) {
        SpeciesPage p = byId.get(speciesId);
        if (p != null) return p;
        SpeciesPage d = new SpeciesPage();
        d.Id = speciesId;
        return d;
    }

    @Nonnull
    public String art(@Nonnull String speciesId) {
        String a = page(speciesId).Art;
        return a == null || a.isBlank() ? UnknownArt : a;
    }

    @Nonnull
    public String silhouette(@Nonnull String speciesId) {
        String a = page(speciesId).Silhouette;
        return a == null || a.isBlank() ? UnknownArt : a;
    }

    /** Every species the server knows, in journal order (then by id). */
    @Nonnull
    public List<SpeciesDefinition> ordered(@Nonnull Iterable<SpeciesDefinition> all) {
        List<SpeciesDefinition> out = new ArrayList<>();
        for (SpeciesDefinition s : all) out.add(s);
        out.sort(Comparator.<SpeciesDefinition>comparingInt(s -> page(s.Id).Order).thenComparing(s -> s.Id));
        return out;
    }

    @Nonnull
    public List<SpeciesDefinition> ordered() {
        return ordered(SpeciesRegistry.all());
    }
}
