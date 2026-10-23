package com.hexvane.cozytalesranching.care;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code Server/CozyTalesRanching/Habitat.json}: which blocks and fluids each habitat feature id of the species
 * files ({@code HabitatRequires}, {@code HabitatPrefers}) stands for. Field names are the file format.
 */
public final class HabitatConfig {
    public static final String SPECIAL_SHADE = "Shade";
    public static final String SPECIAL_OPEN_SKY = "OpenSky";
    public static final String SPECIAL_TROUGH_FOOD = "TroughFood";

    private static final Gson GSON = new Gson();

    /** Cells scanned below and above the house's origin block. */
    public int ScanBelow = 4;
    public int ScanAbove = 10;
    /** For Shade and OpenSky: the ground of a column counts only this close to the house's height. */
    public int GroundTolerance = 4;
    /** Blocks that shade the ground below them besides any solid block (tree canopies). */
    public List<String> CoverPrefixes = new ArrayList<>(List.of("Plant_Leaves_"));
    public Map<String, Feature> Features = new LinkedHashMap<>();
    /** Species id to the toy feature ids it likes; added to the species' {@code HabitatPrefers}. */
    public Map<String, List<String>> SpeciesToys = new LinkedHashMap<>();
    /** Advanced feed item id to its effect. Every species eats every feed. */
    public Map<String, Feed> Feeds = new LinkedHashMap<>();
    public FeedEffects FeedEffects = new FeedEffects();
    /** Happiness floor of an animal with a trait that slows its decline, unless the trait sets its own {@code HappinessFloor}. */
    public float DeclineTraitFloor = 0.45f;

    public static final class Feed {
        /** {@code hearty}, {@code sweet}, {@code breeders} or {@code premium} ({@code AnimalRecord.feedBonus}). */
        public String Bonus = "";
        /** Real minutes the bonus lasts after the feed is eaten. */
        public double Minutes = 60;
    }

    public static final class FeedEffects {
        /** Sweet Feed: friendship gained from meals, treats and petting is multiplied by this. */
        public float SweetFriendshipMultiplier = 1.5f;
        /** Breeder's Feed: each feed eaten moves the breeding cooldown on by this share of the species' cooldown. */
        public double BreedersCooldownCut = 0.5;
    }

    public static final class Feature {
        /** Base block ids (without state suffix) that count. */
        public List<String> Blocks = new ArrayList<>();
        public List<String> BlockPrefixes = new ArrayList<>();
        /** Fluid ids that count. */
        public List<String> Fluids = new ArrayList<>();
        /** A block whose id contains any of these never counts (e.g. "Poisoned"). */
        public List<String> Exclude = new ArrayList<>();
        /** The block counts only with an empty cell above it (an exposed patch, not buried). */
        public boolean Surface;
        /** {@code Shade}, {@code OpenSky} or {@code TroughFood}; empty for plain block or fluid matching. */
        public String Special = "";
        /** Cells (or columns, for Shade and OpenSky) needed. */
        public int MinCount = 1;
        /** OpenSky only: share of the scanned columns that must be open. */
        public double MinFraction;

        public boolean matchesBlock(@Nonnull String baseId) {
            boolean hit = Blocks.contains(baseId);
            if (!hit) {
                for (String p : BlockPrefixes) {
                    if (baseId.startsWith(p)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (!hit) return false;
            for (String e : Exclude) if (!e.isEmpty() && baseId.contains(e)) return false;
            return true;
        }

        public boolean matchesFluid(@Nonnull String fluidId) {
            return Fluids.contains(fluidId);
        }

        boolean isSpecial() {
            return !Special.isEmpty();
        }

        @Nonnull
        Feature normalise() {
            if (Blocks == null) Blocks = new ArrayList<>();
            if (BlockPrefixes == null) BlockPrefixes = new ArrayList<>();
            if (Fluids == null) Fluids = new ArrayList<>();
            if (Exclude == null) Exclude = new ArrayList<>();
            if (Special == null) Special = "";
            MinCount = Math.max(1, MinCount);
            MinFraction = Math.clamp(MinFraction, 0.0, 1.0);
            return this;
        }
    }

    @Nullable
    public Feature feature(@Nonnull String id) {
        return Features.get(id);
    }

    /** The species' own preferences followed by the toys it likes, without repeats. */
    @Nonnull
    public List<String> prefers(@Nonnull String speciesId, @Nonnull List<String> speciesPrefers) {
        List<String> out = new ArrayList<>(speciesPrefers);
        for (String toy : SpeciesToys.getOrDefault(speciesId, List.of())) if (!out.contains(toy)) out.add(toy);
        return out;
    }

    /** The bonus id of an advanced feed item, or null when the item is not a feed. */
    @Nullable
    public String feedBonus(@Nullable String itemId) {
        Feed f = itemId == null ? null : Feeds.get(itemId);
        return f == null || f.Bonus.isEmpty() ? null : f.Bonus;
    }

    /** How long a feed's bonus lasts, in milliseconds; 0 when the item is not a feed. */
    public long feedDurationMs(@Nullable String itemId) {
        Feed f = itemId == null ? null : Feeds.get(itemId);
        return f == null ? 0L : Math.round(f.Minutes * 60_000.0);
    }

    /** Feed item id to bonus id, in file order, for picking a meal. */
    @Nonnull
    public Map<String, String> feedItems() {
        Map<String, String> out = new LinkedHashMap<>();
        Feeds.forEach((item, f) -> {
            if (!f.Bonus.isEmpty()) out.put(item, f.Bonus);
        });
        return out;
    }

    public boolean isCover(@Nonnull String baseId) {
        for (String p : CoverPrefixes) if (baseId.startsWith(p)) return true;
        return false;
    }

    @Nonnull
    public static HabitatConfig read(@Nonnull Reader reader) {
        HabitatConfig c = GSON.fromJson(reader, HabitatConfig.class);
        return (c == null ? new HabitatConfig() : c).normalise();
    }

    @Nonnull
    HabitatConfig normalise() {
        if (Features == null) Features = new LinkedHashMap<>();
        if (CoverPrefixes == null) CoverPrefixes = new ArrayList<>();
        Features.values().removeIf(f -> f == null);
        Features.values().forEach(Feature::normalise);
        if (SpeciesToys == null) SpeciesToys = new LinkedHashMap<>();
        SpeciesToys.values().removeIf(l -> l == null);
        if (Feeds == null) Feeds = new LinkedHashMap<>();
        Feeds.values().removeIf(f -> f == null);
        for (Feed f : Feeds.values()) {
            if (f.Bonus == null) f.Bonus = "";
            f.Minutes = Math.clamp(f.Minutes, 0.0, 24 * 60.0);
        }
        if (FeedEffects == null) FeedEffects = new FeedEffects();
        FeedEffects.SweetFriendshipMultiplier = Math.clamp(FeedEffects.SweetFriendshipMultiplier, 1f, 5f);
        FeedEffects.BreedersCooldownCut = Math.clamp(FeedEffects.BreedersCooldownCut, 0.0, 1.0);
        DeclineTraitFloor = Math.clamp(DeclineTraitFloor, CareRules.FLOOR, 1f);
        ScanBelow = Math.clamp(ScanBelow, 0, 16);
        ScanAbove = Math.clamp(ScanAbove, 2, 24);
        GroundTolerance = Math.clamp(GroundTolerance, 0, 16);
        return this;
    }

    /** Strips a state suffix: {@code *Plant_Crop_Wheat_Block_State_Definitions_Stage3} becomes {@code Plant_Crop_Wheat_Block}. */
    @Nonnull
    public static String baseBlockId(@Nonnull String id) {
        String s = id.startsWith("*") ? id.substring(1) : id;
        int state = s.indexOf("_State_Definitions_");
        return state >= 0 ? s.substring(0, state) : s;
    }
}
