package com.hexvane.cozytalesranching.wildlife;

import com.hexvane.cozytalesranching.species.SpeciesOverlay;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code Server/CozyTalesRanching/Wildlife.json}, or {@code <data>/Wildlife.json} on the server (replaces it whole).
 * Field names are the JSON keys; times are real minutes or seconds as named.
 */
public final class WildlifeConfig {
    private static final String RESOURCE = "/Server/CozyTalesRanching/Wildlife.json";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Gson GSON = new Gson();

    /** How often each world looks at its houses and visitors. */
    public double PassSeconds = 45;
    /** A house is considered for a visit once per random wait in this range. */
    public double MinVisitMinutes = 4;
    public double MaxVisitMinutes = 10;
    /** A visitor stays for a random time in this range, then walks off. */
    public double MinStayMinutes = 5;
    public double MaxStayMinutes = 9;
    /** After this long walking off, a visitor goes as soon as no player is within {@link #NearPlayerDistance}. */
    public double LeaveSeconds = 60;
    /** A leaving visitor with no player this close is out of sight and goes at once. */
    public double HiddenDistance = 28;
    /** Players closer than this see a leaving visitor vanish, so it waits (up to 4 x LeaveSeconds). */
    public double NearPlayerDistance = 10;
    /** A house only gets visitors while some player is this close to it (and its owner is online). */
    public double PlayerRange = 48;
    public int SpawnMinDistance = 12;
    public int SpawnMaxDistance = 24;
    public int SpawnAttempts = 24;
    /** Columns above the ground that must be open sky (leaves allowed), so visitors never appear inside builds. */
    public int OpenSkyAbove = 12;
    /** Frogs need water within this many blocks of where they appear. */
    public int WaterSearchRadius = 3;
    public int MaxPerHouse = 1;
    public int MaxPerOwner = 3;
    public int MaxPerWorld = 8;
    /** At most this many visitors arrive per world per pass. */
    public int MaxArrivalsPerPass = 2;
    /** A house of the visitor's own species needs its requirements met and at least this habitat score. */
    public double MinHabitatScore = 0.5;
    /** A visitor that stays unloaded this long after its time is forgotten (it remains an ordinary wild animal). */
    public double ForgetUnloadedHours = 72;
    public Map<String, SpeciesWildlife> Species = new LinkedHashMap<>();

    /** What attracts one species to a ranch, and its rare variant. */
    public static final class SpeciesWildlife {
        /** Wild roles a normal visitor spawns as (vanilla roles). */
        public List<String> Roles = List.of();
        /** Weight at a house of its own species whose habitat is good. */
        public double OwnHouseWeight = 1.0;
        /** Other houses that attract it: all of {@code Features} found there by the habitat scan. */
        public List<Attractor> Attractors = List.of();
        /** The same species does not visit the same house again for this long. */
        public double CooldownMinutes = 20;
        /** Spawn only near water (frogs). */
        public boolean NearWater;
        @Nullable public Rare Rare;
    }

    public static final class Attractor {
        /** Only houses of this species (empty: any house). */
        public String HouseSpecies = "";
        public List<String> Features = List.of();
        public double Weight = 0.3;
    }

    /** One rare variant per species, only at a house of that species, under harder conditions. */
    public static final class Rare {
        /** Its own wild role (a recoloured Variant); taming maps it through the species' RanchRoleVariants. */
        public String Role = "";
        /** Its display name. */
        public String NameKey = "";
        /** Chance a visitor is the rare one when every condition holds. */
        public double Chance = 0.25;
        public double CooldownMinutes = 60;
        /** Habitat features the scan must have found at the house. */
        public List<String> Features = List.of();
        public double MinHabitatScore = 0;
        /** Residents of the house's species living in it. */
        public int MinResidents = 0;
        /** Average happiness of the residents (only checked when MinResidents > 0). */
        public double MinAvgHappiness = 0;
        /** Items of the species' favourite treats in troughs near the house. */
        public int FavoriteInTroughs = 0;
        /** Products waiting in the house, item id to at least this many. */
        public Map<String, Integer> StoredItems = Map.of();
    }

    @Nonnull
    WildlifeConfig normalise() {
        if (Species == null) Species = new LinkedHashMap<>();
        for (SpeciesWildlife s : Species.values()) {
            if (s.Roles == null) s.Roles = List.of();
            if (s.Attractors == null) s.Attractors = List.of();
            for (Attractor a : s.Attractors) {
                if (a.Features == null) a.Features = List.of();
                if (a.HouseSpecies == null) a.HouseSpecies = "";
            }
            if (s.Rare != null) {
                if (s.Rare.Features == null) s.Rare.Features = List.of();
                if (s.Rare.StoredItems == null) s.Rare.StoredItems = Map.of();
                if (s.Rare.Role == null) s.Rare.Role = "";
                if (s.Rare.NameKey == null) s.Rare.NameKey = "";
            }
        }
        MinVisitMinutes = Math.max(0.1, MinVisitMinutes);
        MaxVisitMinutes = Math.max(MinVisitMinutes, MaxVisitMinutes);
        MinStayMinutes = Math.max(0.1, MinStayMinutes);
        MaxStayMinutes = Math.max(MinStayMinutes, MaxStayMinutes);
        SpawnMinDistance = Math.max(2, SpawnMinDistance);
        SpawnMaxDistance = Math.max(SpawnMinDistance, SpawnMaxDistance);
        PassSeconds = Math.clamp(PassSeconds, 5, 600);
        return this;
    }

    public long passMs() {
        return Math.round(PassSeconds * 1000);
    }

    @Nullable
    public SpeciesWildlife species(@Nonnull String speciesId) {
        return Species.get(speciesId);
    }

    @Nonnull
    static WildlifeConfig parse(@Nonnull Reader reader) {
        WildlifeConfig c = GSON.fromJson(reader, WildlifeConfig.class);
        return (c == null ? new WildlifeConfig() : c).normalise();
    }

    /** The server's own file if it has one, else the bundled one, else defaults. */
    @Nonnull
    static WildlifeConfig load(@Nonnull Path dataDirectory) {
        Path override = dataDirectory.resolve("Wildlife.json");
        if (Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                return parse(r);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + override + "; using the bundled Wildlife.json", e);
            }
        }
        return bundled();
    }

    @Nonnull
    static WildlifeConfig bundled() {
        try (InputStream in = WildlifeConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOG.warning("Bundled Wildlife.json missing; no visitors will come");
                return new WildlifeConfig().normalise();
            }
            return parse(SpeciesOverlay.merged(in, "Wildlife"));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read the bundled Wildlife.json", e);
            return new WildlifeConfig().normalise();
        }
    }
}
