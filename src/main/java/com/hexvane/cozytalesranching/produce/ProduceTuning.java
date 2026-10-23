package com.hexvane.cozytalesranching.produce;

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

/**
 * Production numbers, read with Gson from {@code Server/CozyTalesRanching/Produce.json} (or {@code Produce.json} in the
 * plugin data folder, which replaces it). Per-product numbers live in the species files' {@code Products}; the trait
 * numbers ({@code ProductionMultiplier}, {@code BonusProductChance}) in {@code Traits.json}.
 */
public final class ProduceTuning {
    static final String RESOURCE = "/Server/CozyTalesRanching/Produce.json";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Gson GSON = new Gson();

    /** Seconds between production passes over a world's houses. */
    public double PassSeconds = 30;
    /** Items a house holds in total; production pauses when it is full. */
    public int HouseCap = 64;
    /** Most real minutes credited for time the world was not loaded (a restart, a long stop). */
    public double CatchUpCapMinutes = 480;
    /** Rate factor of an animal at happiness 0; it rises linearly to 1 at happiness 1. */
    public double HappinessRateAtZero = 0.5;
    /** The rate never falls below this factor, whatever the inputs. */
    public double MinRateFactor = 0.25;
    /** Extra rate per heart (5 hearts at 0.05 = +25%). */
    public double HeartRateBonus = 0.05;
    /** Rate factor while Premium Feed is in effect. */
    public double PremiumFeedMultiplier = 1.2;
    /** The empty item a {@code Collect: "Bucket"} product is collected into, one per item. */
    public String BucketItem = "Container_Bucket";

    private static volatile ProduceTuning current = new ProduceTuning().normalise();

    @Nonnull
    public static ProduceTuning get() {
        return current;
    }

    static void set(@Nonnull ProduceTuning tuning) {
        current = tuning.normalise();
    }

    @Nonnull
    ProduceTuning normalise() {
        PassSeconds = Math.clamp(PassSeconds, 5.0, 600.0);
        HouseCap = Math.max(1, HouseCap);
        CatchUpCapMinutes = Math.max(0.0, CatchUpCapMinutes);
        HappinessRateAtZero = Math.clamp(HappinessRateAtZero, 0.0, 1.0);
        MinRateFactor = Math.clamp(MinRateFactor, 0.01, 1.0);
        HeartRateBonus = Math.max(0.0, HeartRateBonus);
        PremiumFeedMultiplier = Math.max(1.0, PremiumFeedMultiplier);
        if (BucketItem == null || BucketItem.isBlank()) BucketItem = "Container_Bucket";
        return this;
    }

    static synchronized void load(@Nonnull Path dataDirectory) {
        ProduceTuning loaded = null;
        Path override = dataDirectory.resolve("Produce.json");
        if (Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                loaded = read(r);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + override + "; using the bundled produce tuning", e);
            }
        }
        if (loaded == null) {
            try (InputStream in = ProduceTuning.class.getResourceAsStream(RESOURCE)) {
                if (in == null) LOG.warning("Bundled Produce.json missing; using defaults");
                else loaded = read(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read bundled Produce.json", e);
            }
        }
        if (loaded != null) set(loaded);
    }

    @Nonnull
    static ProduceTuning read(@Nonnull Reader reader) {
        ProduceTuning t = GSON.fromJson(reader, ProduceTuning.class);
        if (t == null) throw new IllegalArgumentException("empty produce file");
        return t.normalise();
    }
}
