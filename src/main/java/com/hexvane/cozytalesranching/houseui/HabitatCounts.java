package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.care.HabitatConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What counts for a habitat feature, in a short line built from its definition in {@code Habitat.json} (and the species
 * fragments): its blocks, block families and fluids, its {@code MinCount}, whether it must be uncovered, or its special
 * rule. "2 or more blocks in the yard: wooden fences, Bundle of Hay, Toy Log". Block families share a name
 * ({@code counts.group.*}: every {@code Wood_*_Fence} is "wooden fences"); a single block is called by its item name. The
 * house page shows it as the tooltip of each need and want; the journal lists it under a species' home. Pure: no
 * engine types, so it can be checked in unit tests.
 */
public final class HabitatCounts {
    /** Names in a list before the rest are summed up as "and more". */
    public static final int MAX_NAMES = 4;

    /** Item names from the server's asset map (or test files). */
    public interface Names {
        /** Translation key of the item with this id, or null when there is no such item. */
        @Nullable
        String itemNameKey(@Nonnull String itemId);
    }

    /**
     * A family of blocks with one name; first match wins, so the narrower families come first. A {@code single} family
     * met through just one of its blocks is called by that block's item name ("Deadwood Fence", not "wooden fences").
     */
    private record Group(@Nonnull String key, @Nonnull Pattern pattern, boolean single) {}

    private static final List<Group> GROUPS = List.of(
        single("fences", "^Wood_[A-Za-z]+_Fence$"),
        group("deadwood", "^Wood_(Dry_Trunk|Deadwood_(Planks|Beam))"),
        single("trunks", "^Wood_[A-Za-z]+_Trunk$"),
        group("jungle_wood", "^Wood_(Jungle|Palm|Banyan|Bamboo)_?$"),
        group("jungle_leaves", "^Plant_Leaves_(Jungle|Palm|Banyan|Bamboo)"),
        group("snowy_leaves", "^Plant_Leaves_Fir.*Snow"),
        group("leaves", "^Plant_Leaves_?"),
        group("vines", "^Plant_Vine_?"),
        group("water_lilies", "^Plant_Flower_Water_?"),
        group("flowers", "^Plant_(Flower_?|Sunflower)"),
        group("reeds", "^Plant_Reeds_?"),
        group("tall_grass", "^Plant_(Grass_[A-Za-z]+_(Tall|Overgrown|Wild)|Fern_Tall)$"),
        group("dry_grass", "^Plant_Grass_(Arid|Dry)"),
        group("wet_grass", "^(Plant|Soil)_Grass_Wet"),
        group("snowy_grass", "^Plant_Grass_Snowy"),
        group("grass", "^(Soil_Grass|Plant_Grass_?)"),
        group("berries", "^Plant_Crop_Berry"),
        group("mushrooms", "^Plant_Crop_Mushroom"),
        group("carrots", "^Plant_Crop_Carrot"),
        group("lettuce", "^Plant_Crop_Lettuce"),
        group("wheat", "^Plant_Crop_Wheat"),
        group("corn", "^Plant_Crop_Corn"),
        group("rice", "^Plant_Crop_Rice"),
        group("chilli", "^Plant_Crop_Chilli"),
        group("cauliflower", "^Plant_Crop_Cauliflower"),
        group("pumpkins", "^Plant_Crop_Pumpkin"),
        group("crops", "^Plant_Crop_?$"),
        group("dry_bushes", "^Plant_Bush_(Arid|Dead)"),
        group("snowy_bushes", "^Plant_Bush_Winter"),
        group("bushes", "^Plant_Bush"),
        group("cacti", "^Plant_Cactus"),
        group("moss", "^Plant_Moss"),
        group("ferns", "^Plant_Fern"),
        group("seaweed", "^Plant_Seaweed"),
        group("shells", "^Deco_Coral_Shell"),
        group("red_sand", "^Soil_(Sand_Red|Gravel_Sand_Red)"),
        group("red_clay", "^Soil_Clay_(Red|Orange)"),
        group("sand", "^Soil_(Sand|Gravel_Sand)"),
        group("gravel", "^Soil_Gravel"),
        group("cobble", "^Rock_Stone_Cobble"),
        group("ice", "^Rock_Ice"),
        group("stone", "^Rock_"),
        group("dry_dirt", "^Soil_Dirt_Dry"),
        group("wet_dirt", "^Soil_Dirt_Wet"),
        group("dirt", "^Soil_Dirt"),
        group("mud", "^Soil_Mud"),
        group("snow", "^Soil_Snow"),
        group("lights", "(Torch|Lantern|Brazier|Campfire)"),
        group("water", "^Water")
    );

    private HabitatCounts() {}

    private static Group group(@Nonnull String key, @Nonnull String regex) {
        return new Group(key, Pattern.compile(regex), false);
    }

    private static Group single(@Nonnull String key, @Nonnull String regex) {
        return new Group(key, Pattern.compile(regex), true);
    }

    /** The line for one feature; a feature with no definition gets a stock line. */
    @Nonnull
    public static Txt explain(@Nullable HabitatConfig.Feature feature, @Nonnull Names names) {
        if (feature == null) return Txt.ui("counts.unknown");
        String special = feature.Special == null ? "" : feature.Special;
        int min = Math.max(1, feature.MinCount);
        switch (special) {
            case HabitatConfig.SPECIAL_TROUGH_FOOD -> {
                return Txt.ui("counts.food");
            }
            case HabitatConfig.SPECIAL_SHADE -> {
                return Txt.ui("counts.shade").with("count", min);
            }
            case HabitatConfig.SPECIAL_OPEN_SKY -> {
                int percent = (int) Math.round(Math.clamp(feature.MinFraction, 0.0, 1.0) * 100);
                return percent > 0 ? Txt.ui("counts.open").with("percent", percent) : Txt.ui("counts.open.count").with("count", min);
            }
            default -> {
                // plain block and fluid matching below
            }
        }
        List<Txt> things = things(feature, names);
        if (things.isEmpty()) return Txt.ui("counts.unknown");
        String form = (feature.Surface ? "counts.surface." : "counts.yard.") + (min == 1 ? "one" : "many");
        return Txt.ui(form).with("count", min).with("things", list(things));
    }

    /** The distinct names of what the feature counts, families first in the order they appear. */
    @Nonnull
    static List<Txt> things(@Nonnull HabitatConfig.Feature f, @Nonnull Names names) {
        Map<String, List<String>> members = new LinkedHashMap<>();
        Map<String, Txt> out = new LinkedHashMap<>();
        List<String[]> ids = new ArrayList<>();
        for (String id : nonNull(f.Blocks)) ids.add(new String[] {id, "block"});
        for (String id : nonNull(f.BlockPrefixes)) ids.add(new String[] {id, "prefix"});
        for (String id : nonNull(f.Fluids)) ids.add(new String[] {id, "fluid"});
        for (String[] e : ids) {
            String id = e[0];
            if (id == null || id.isBlank()) continue;
            Group g = groupOf(id);
            if (g != null) {
                if (g.single()) members.computeIfAbsent(g.key(), k -> new ArrayList<>()).add(id);
                out.putIfAbsent("g:" + g.key(), Txt.ui("counts.group." + g.key()));
            } else {
                out.putIfAbsent("i:" + id, itemName(id, names));
            }
        }
        for (Map.Entry<String, List<String>> m : members.entrySet()) {
            if (m.getValue().size() != 1) continue;
            String key = names.itemNameKey(m.getValue().getFirst());
            if (key != null) out.put("g:" + m.getKey(), Txt.key(key));
        }
        return new ArrayList<>(out.values());
    }

    /** "a, b, c and more" from the names, at most {@link #MAX_NAMES} of them. */
    @Nonnull
    static Txt list(@Nonnull List<Txt> things) {
        int shown = Math.min(things.size(), MAX_NAMES);
        Txt out = things.getFirst();
        for (int i = 1; i < shown; i++) out = Txt.ui("counts.list").with("a", out).with("b", things.get(i));
        return things.size() > shown ? Txt.ui("counts.more").with("list", out) : out;
    }

    @Nullable
    private static Group groupOf(@Nonnull String id) {
        for (Group g : GROUPS) if (g.pattern().matcher(id).find()) return g;
        return null;
    }

    @Nonnull
    private static Txt itemName(@Nonnull String id, @Nonnull Names names) {
        String key = names.itemNameKey(id);
        return key != null ? Txt.key(key) : Txt.raw(humanise(id));
    }

    /** {@code Deco_Coral_Thing_} to {@code coral thing}: the last resort for an id with no family and no item. */
    @Nonnull
    static String humanise(@Nonnull String id) {
        String s = id.replaceFirst("^(Plant|Soil|Rock|Wood|Deco|Furniture|CozyRanch)_", "").replace('_', ' ').trim();
        return s.isEmpty() ? id : s.toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private static List<String> nonNull(@Nullable List<String> l) {
        return l == null ? List.of() : l;
    }
}
