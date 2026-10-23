package com.hexvane.cozytalesranching.produce;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Poop pile numbers, read with Gson from {@code Server/CozyTalesRanching/Poop.json} (or {@code Poop.json} in the plugin
 * data folder, which replaces it).
 */
public final class PoopTuning {
    static final String RESOURCE = "/Server/CozyTalesRanching/Poop.json";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Gson GSON = new Gson();

    /** Off: animals never make piles (players can still place the item). */
    public boolean Enabled = true;
    /** Seconds between passes over a world's houses. */
    public double PassSeconds = 15;
    /** Real minutes an animal waits after making (or adding to) a pile before it can again. */
    public double CooldownMinutes = 6;
    /** Chance per real minute, once cooled down, that an eligible animal makes a pile. */
    public double ChancePerMinute = 0.12;
    /** Babies make piles too. */
    public boolean Babies = false;
    /** Per species id: false turns piles off for it. A species not listed makes piles. */
    public Map<String, Boolean> Species = new HashMap<>();
    /** Blocks (horizontally, and one cell up or down) within which a new dropping grows an existing pile. */
    public int MergeRadius = 3;
    /** The most droppings one pile holds; a full pile nearby stops new piles there. */
    public int MaxCount = 8;
    /** Piles at most around one house (within its home range); past it the animals of that house wait. */
    public int MaxPilesPerHouse = 6;
    /** Most piles made or grown in one world per pass, whatever the number of animals. */
    public int MaxPilesPerPass = 4;
    /** No new pile this close (horizontally) to a house door. */
    public double DoorClearance = 2;
    /** A pile a player places next to an existing one joins it (the item goes into the old pile). */
    public boolean PlayerPilesMerge = true;
    /** No pile on a block whose id contains any of these (farmland, paths, floors, our own blocks). */
    public List<String> ExcludedGround = new ArrayList<>(List.of("Farmland", "Tilled", "Path", "Plant_Crop", "CozyRanch_", "Wood_", "Furniture_"));

    private static volatile PoopTuning current = new PoopTuning().normalise();

    @Nonnull
    public static PoopTuning get() {
        return current;
    }

    static void set(@Nonnull PoopTuning tuning) {
        current = tuning.normalise();
    }

    boolean speciesOn(@Nonnull String speciesId) {
        Boolean on = Species.get(speciesId.toLowerCase(Locale.ROOT));
        return on == null || on;
    }

    @Nonnull
    PoopTuning normalise() {
        PassSeconds = Math.clamp(PassSeconds, 2.0, 600.0);
        CooldownMinutes = Math.max(0.0, CooldownMinutes);
        ChancePerMinute = Math.clamp(ChancePerMinute, 0.0, 1.0);
        MergeRadius = Math.clamp(MergeRadius, 0, 8);
        MaxCount = Math.clamp(MaxCount, 1, PoopRules.MAX_STATES);
        MaxPilesPerHouse = Math.max(0, MaxPilesPerHouse);
        MaxPilesPerPass = Math.max(1, MaxPilesPerPass);
        DoorClearance = Math.max(0.0, DoorClearance);
        Map<String, Boolean> lower = new HashMap<>();
        if (Species != null) Species.forEach((k, v) -> { if (k != null) lower.put(k.toLowerCase(Locale.ROOT), v == null || v); });
        Species = lower;
        if (ExcludedGround == null) ExcludedGround = new ArrayList<>();
        ExcludedGround.removeIf(s -> s == null || s.isBlank());
        return this;
    }

    static synchronized void load(@Nonnull Path dataDirectory) {
        PoopTuning loaded = null;
        Path override = dataDirectory.resolve("Poop.json");
        if (Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                loaded = read(r);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + override + "; using the bundled poop tuning", e);
            }
        }
        if (loaded == null) {
            try (InputStream in = PoopTuning.class.getResourceAsStream(RESOURCE)) {
                if (in == null) LOG.warning("Bundled Poop.json missing; using defaults");
                else loaded = read(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read bundled Poop.json", e);
            }
        }
        if (loaded != null) set(loaded);
    }

    @Nonnull
    static PoopTuning read(@Nonnull Reader reader) {
        PoopTuning t = GSON.fromJson(reader, PoopTuning.class);
        if (t == null) throw new IllegalArgumentException("empty poop file");
        return t.normalise();
    }
}
