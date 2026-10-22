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
