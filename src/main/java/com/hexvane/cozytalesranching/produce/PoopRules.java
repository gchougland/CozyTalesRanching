package com.hexvane.cozytalesranching.produce;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pure rules for poop piles: which block state holds which count, when an animal may make a pile, where, and whether a
 * new dropping grows a pile nearby instead. No engine types, so all of it is unit tested.
 * <p>
 * The count lives in the block itself: {@code CozyRanch_Poop_Pile} is one dropping and its states {@code Count2} to
 * {@code Count8} hold two to eight. Each state draws the small, medium or large mound and drops that many
 * {@code Ingredient_Poop} when broken, so a pile keeps its count across restarts with the chunk and needs no save file.
 */
public final class PoopRules {
    public static final String BLOCK_ID = "CozyRanch_Poop_Pile";
    public static final String ITEM_DROP = "Ingredient_Poop";
    /** Counts with a block state of their own: 1 (the default state) to 8. */
    public static final int MAX_STATES = 8;
    static final String STATE_PREFIX = "Count";
    private static final String STATE_MARK = "_State_Definitions_";

    public enum Size { SMALL, MEDIUM, LARGE }

    public enum Kind { PLACE, MERGE, SKIP }

    /** A pile in the world. */
    public record Pile(int x, int y, int z, int count) {}

    /** What one new dropping does: a new pile at the animal, one more in {@code target}, or nothing ({@code reason}). */
    public record Decision(@Nonnull Kind kind, @Nullable Pile target, @Nonnull String reason) {
        static Decision place() { return new Decision(Kind.PLACE, null, "place"); }
        static Decision merge(@Nonnull Pile p) { return new Decision(Kind.MERGE, p, "merge"); }
        static Decision skip(@Nonnull String why) { return new Decision(Kind.SKIP, null, why); }
    }

    private PoopRules() {}

    /** Droppings held by a block with this type id: 1 to 8 for a pile, 0 for anything else. */
    public static int countOf(@Nullable String typeId) {
        if (typeId == null) return 0;
        String id = typeId.startsWith("*") ? typeId.substring(1) : typeId;
        if (id.equals(BLOCK_ID)) return 1;
        String prefix = BLOCK_ID + STATE_MARK;
        if (!id.startsWith(prefix)) return 0;
        String state = id.substring(prefix.length());
        if (state.equalsIgnoreCase("default")) return 1;
        if (!state.startsWith(STATE_PREFIX)) return 0;
        try {
            int n = Integer.parseInt(state.substring(STATE_PREFIX.length()));
            return n >= 1 && n <= MAX_STATES ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The block type id that shows this many droppings (clamped to 1..8). */
    @Nonnull
    public static String typeIdFor(int count) {
        int n = Math.clamp(count, 1, MAX_STATES);
        return n == 1 ? BLOCK_ID : "*" + BLOCK_ID + STATE_MARK + STATE_PREFIX + n;
    }

    /** The mound drawn for a count: 1 small, 2 to 3 medium, 4 and up large. */
    @Nonnull
    public static Size sizeOf(int count) {
        if (count <= 1) return Size.SMALL;
        return count <= 3 ? Size.MEDIUM : Size.LARGE;
    }

    /** Chance for one pass of {@code passSeconds} from a chance per minute, so the rate does not depend on the pass length. */
    public static double passChance(double chancePerMinute, double passSeconds) {
        double p = Math.clamp(chancePerMinute, 0.0, 1.0);
        if (p >= 1.0) return 1.0;
        return 1.0 - Math.pow(1.0 - p, Math.max(0.0, passSeconds) / 60.0);
    }

    public static boolean cooledDown(long lastMs, long nowMs, double cooldownMinutes) {
        return lastMs <= 0 || nowMs - lastMs >= (long) (cooldownMinutes * 60_000.0) || nowMs < lastMs;
    }

    /** The animal side: who may make a pile right now. */
    public record Animal(boolean owned, boolean housed, boolean active, boolean baby, boolean speciesOn, boolean loaded, boolean day) {}

    @Nonnull
    public static String mayPoop(@Nonnull Animal a, boolean babies) {
        if (!a.owned()) return "not_owned";
        if (!a.housed()) return "no_house";
        if (!a.active()) return "inside";
        if (a.baby() && !babies) return "baby";
        if (!a.speciesOn()) return "species_off";
        if (!a.loaded()) return "not_loaded";
        if (!a.day()) return "night";
        return "ok";
    }

    /** What is at and under the cell a pile would go in. Ids are base ids (no state), null for empty. */
    public record Ground(@Nullable String below, boolean belowSolid, boolean belowFiller, @Nullable String at, boolean atFiller,
                         @Nullable String fluidAt, @Nullable String fluidBelow) {}

    /**
     * A pile goes in an empty, dry cell on top of a whole solid block that is not farmland, a path, a floor or one of our
     * blocks ({@code excluded} are id fragments) and not part of a multi-cell block.
     */
    @Nonnull
    public static String groundCheck(@Nonnull Ground g, @Nonnull List<String> excluded) {
        if (g.at() != null || g.atFiller()) return "cell_taken";
        if (g.fluidAt() != null || g.fluidBelow() != null) return "water";
        if (g.below() == null || !g.belowSolid()) return "no_ground";
        if (g.belowFiller()) return "on_building";
        String below = g.below().toLowerCase(Locale.ROOT);
        for (String e : excluded) if (below.contains(e.toLowerCase(Locale.ROOT))) return "excluded_ground";
        return "ok";
    }

    public static boolean nearDoor(double x, double z, double doorX, double doorZ, double clearance) {
        double dx = x + 0.5 - doorX, dz = z + 0.5 - doorZ;
        return dx * dx + dz * dz < clearance * clearance;
    }

    /** A pile counts as near when it is within {@code radius} on x and z (a square) and one cell up or down. */
    public static boolean within(@Nonnull Pile p, int x, int y, int z, int radius) {
        return Math.abs(p.x() - x) <= radius && Math.abs(p.z() - z) <= radius && Math.abs(p.y() - y) <= 1;
    }

    /**
     * The merge rule for a dropping at {@code (x,y,z)}. The nearest pile within the merge radius that is not full takes
     * it. When every pile near is full, nothing happens (the land does not fill up). With none near, a new pile is
     * placed unless the house already has its most piles. {@code housePiles} &lt; 0 skips that limit (a player's pile).
     * An animal standing in a pile's own cell (piles have no collision) adds to that pile; {@code ownCell} false leaves
     * the pile at {@code (x,y,z)} out, for a pile a player has just placed there.
     */
    @Nonnull
    public static Decision decide(int x, int y, int z, @Nonnull List<Pile> near, int housePiles, @Nonnull PoopTuning t) {
        return decide(x, y, z, near, housePiles, t, true);
    }

    @Nonnull
    public static Decision decide(int x, int y, int z, @Nonnull List<Pile> near, int housePiles, @Nonnull PoopTuning t, boolean ownCell) {
        Pile best = null;
        double bestD = Double.MAX_VALUE;
        boolean anyNear = false;
        for (Pile p : near) {
            if (!ownCell && p.x() == x && p.y() == y && p.z() == z) continue;
            if (!within(p, x, y, z, t.MergeRadius)) continue;
            anyNear = true;
            if (p.count() >= t.MaxCount) continue;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy * 4 + dz * dz;
            if (d < bestD || (d == bestD && best != null && p.count() < best.count())) {
                best = p;
                bestD = d;
            }
        }
        if (best != null) return Decision.merge(best);
        if (anyNear) return Decision.skip("piles_full");
        if (housePiles >= 0 && housePiles >= t.MaxPilesPerHouse) return Decision.skip("house_full");
        return Decision.place();
    }
}
