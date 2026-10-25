package com.hexvane.cozytalesranching.housedisplay;

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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Display points per house block, read with Gson from {@code Server/CozyTalesRanching/HouseDisplay.json} (or
 * {@code HouseDisplay.json} in the plugin data folder, which replaces it). Field names are the format.
 */
public final class HouseDisplayData {
    static final String RESOURCE = "/Server/CozyTalesRanching/HouseDisplay.json";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Gson GSON = new Gson();
    /** More points than this on one house are ignored (each is an entity). */
    static final int MAX_POINTS = 8;

    /** Off: no house shows its residents. */
    public boolean Enabled = true;
    /** Seconds between the checks that catch what the house day hooks do not (one resident going in late, chunk loads). */
    public double PassSeconds = 5;
    /** Animation the shown animals play (an animation set of their model); skipped when the model has none. */
    public String Animation = "Idle";
    /** House block id to its points. */
    public Map<String, HouseEntry> Houses = new HashMap<>();

    public static final class HouseEntry {
        public List<Point> Points = new ArrayList<>();
    }

    /** One spot. Position in house model units (32 a block) from the origin cell's bottom centre, house at rotation 0. */
    public static final class Point {
        public double[] Position = {0, 0, 0};
        /** Degrees; 0 looks out of the door side (+Z), 90 towards the house's +X side. */
        public double Yaw;
        /** Times the animal's normal size. */
        public double Scale = 0.5;
        /** Overrides {@link HouseDisplayData#Animation} for this spot. */
        @Nullable public String Animation;

        double x() { return Position.length > 0 ? Position[0] : 0; }
        double y() { return Position.length > 1 ? Position[1] : 0; }
        double z() { return Position.length > 2 ? Position[2] : 0; }
    }

    private static volatile HouseDisplayData current = new HouseDisplayData();

    @Nonnull
    public static HouseDisplayData get() {
        return current;
    }

    static void set(@Nonnull HouseDisplayData data) {
        current = data.normalise();
    }

    /** The points of a house block, or an empty list. */
    @Nonnull
    public List<Point> points(@Nullable String blockId) {
        if (!Enabled || blockId == null) return List.of();
        HouseEntry e = Houses.get(blockId);
        return e == null ? List.of() : e.Points;
    }

    @Nonnull
    HouseDisplayData normalise() {
        PassSeconds = Math.clamp(PassSeconds, 1.0, 120.0);
        if (Animation == null || Animation.isBlank()) Animation = "Idle";
        Map<String, HouseEntry> clean = new HashMap<>();
        if (Houses != null) {
            Houses.forEach((block, entry) -> {
                if (block == null || entry == null || entry.Points == null) return;
                List<Point> pts = new ArrayList<>();
                for (Point p : entry.Points) {
                    if (p == null || p.Position == null) continue;
                    if (!(p.Scale > 0)) p.Scale = 0.5;
                    p.Scale = Math.min(p.Scale, 4.0);
                    if (p.Animation != null && p.Animation.isBlank()) p.Animation = null;
                    if (pts.size() < MAX_POINTS) pts.add(p);
                }
                HouseEntry e = new HouseEntry();
                e.Points = List.copyOf(pts);
                clean.put(block, e);
            });
        }
        Houses = clean;
        return this;
    }

    static synchronized void load(@Nonnull Path dataDirectory) {
        HouseDisplayData loaded = null;
        Path override = dataDirectory.resolve("HouseDisplay.json");
        if (Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                loaded = read(r);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + override + "; using the bundled house display points", e);
            }
        }
        if (loaded == null) {
            try (InputStream in = HouseDisplayData.class.getResourceAsStream(RESOURCE)) {
                if (in == null) LOG.warning("Bundled HouseDisplay.json missing; no house shows its residents");
                else loaded = read(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read bundled HouseDisplay.json", e);
            }
        }
        if (loaded != null) set(loaded);
    }

    @Nonnull
    static HouseDisplayData read(@Nonnull Reader reader) {
        HouseDisplayData d = GSON.fromJson(reader, HouseDisplayData.class);
        if (d == null) throw new IllegalArgumentException("empty house display file");
        return d.normalise();
    }
}
