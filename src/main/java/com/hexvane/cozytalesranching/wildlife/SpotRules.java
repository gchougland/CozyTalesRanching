package com.hexvane.cozytalesranching.wildlife;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Where a visitor may appear: a column in a ring around the house, on solid ground with room above, under open sky
 * (so never inside a build), only where everything is loaded, and near water for species that need it. The engine's
 * own spawn test then checks the role's model fits (in {@link WildlifeWorld}).
 */
public final class SpotRules {
    /** What spot selection reads. The world version reads loaded sections only and never loads a chunk. */
    public interface Grid {
        boolean loaded(int x, int y, int z);

        /** A full block that can be stood on or walled with; leaves count as solid. */
        boolean solid(int x, int y, int z);

        boolean leaves(int x, int y, int z);

        boolean fluid(int x, int y, int z);

        boolean water(int x, int y, int z);
    }

    /** A column and the height of the cell a visitor's feet are in. */
    public record Spot(int x, int y, int z) {}

    public enum Reject { UNLOADED, NO_GROUND, ROOFED, NO_WATER }

    private static final int SEARCH_UP = 6;
    private static final int SEARCH_DOWN = 10;

    private SpotRules() {}

    /** Random columns in the ring {@code min..max} blocks from the centre, {@code attempts} of them. */
    @Nonnull
    public static List<int[]> ring(int cx, int cz, int min, int max, int attempts, @Nonnull RandomGenerator rng) {
        List<int[]> out = new ArrayList<>(attempts);
        for (int i = 0; i < attempts; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double r = min + (max - min) * rng.nextDouble();
            out.add(new int[] {cx + (int) Math.round(Math.cos(angle) * r), cz + (int) Math.round(Math.sin(angle) * r)});
        }
        return out;
    }

    /**
     * The ground in this column nearest the house's height: a solid, non-leaf block with two empty, dry cells above.
     * Returns the feet cell's y, or {@code Integer.MIN_VALUE} when there is none or part of the column is unloaded.
     */
    public static int feetY(@Nonnull Grid g, int x, int z, int nearY) {
        if (!g.loaded(x, nearY - SEARCH_DOWN, z) || !g.loaded(x, nearY + SEARCH_UP + 2, z)) return Integer.MIN_VALUE;
        for (int d = 0; d <= SEARCH_DOWN; d++) {
            for (int sign : d == 0 ? new int[] {1} : new int[] {1, -1}) {
                int y = nearY + sign * d;
                if (sign > 0 && d > SEARCH_UP) continue;
                if (standable(g, x, y, z)) return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean standable(@Nonnull Grid g, int x, int groundY, int z) {
        if (!g.solid(x, groundY, z) || g.leaves(x, groundY, z) || g.fluid(x, groundY, z)) return false;
        for (int dy = 1; dy <= 2; dy++) {
            if (g.solid(x, groundY + dy, z) || g.fluid(x, groundY + dy, z)) return false;
        }
        return true;
    }

    /** Three of the four side neighbours have ground within a step: open ground, not the top of a wall, fence or pillar. */
    static boolean walkableAround(@Nonnull Grid g, int x, int feetY, int z) {
        int ok = 0;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] s : sides) {
            for (int dy = -1; dy <= 1; dy++) {
                if (standable(g, x + s[0], feetY - 1 + dy, z + s[1])) {
                    ok++;
                    break;
                }
            }
        }
        return ok >= 3;
    }

    /** Nothing solid but leaves over the feet, up to {@code height} cells: not under a roof or in a cave. */
    public static boolean openSky(@Nonnull Grid g, int x, int feetY, int z, int height) {
        for (int dy = 2; dy < height; dy++) {
            int y = feetY + dy;
            if (!g.loaded(x, y, z)) return true;
            if (g.solid(x, y, z) && !g.leaves(x, y, z)) return false;
        }
        return true;
    }

    public static boolean waterNear(@Nonnull Grid g, int x, int feetY, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    if (g.loaded(x + dx, feetY + dy, z + dz) && g.water(x + dx, feetY + dy, z + dz)) return true;
                }
            }
        }
        return false;
    }

    /** The column's spot, or why it cannot be one. */
    @Nonnull
    public static Object judge(@Nonnull Grid g, int x, int z, int nearY, int skyHeight, boolean needWater, int waterRadius) {
        if (!g.loaded(x, nearY, z)) return Reject.UNLOADED;
        int feet = feetY(g, x, z, nearY);
        if (feet == Integer.MIN_VALUE || !walkableAround(g, x, feet, z)) return Reject.NO_GROUND;
        if (!openSky(g, x, feet, z, skyHeight)) return Reject.ROOFED;
        if (needWater && !waterNear(g, x, feet, z, waterRadius)) return Reject.NO_WATER;
        return new Spot(x, feet, z);
    }

    /** Candidate spots in the ring, in random order, that pass every rule here. */
    @Nonnull
    public static List<Spot> candidates(@Nonnull Grid g, int cx, int cy, int cz, @Nonnull WildlifeConfig config, boolean needWater,
                                        @Nonnull RandomGenerator rng, @Nullable StringBuilder why) {
        List<Spot> out = new ArrayList<>();
        for (int[] c : ring(cx, cz, config.SpawnMinDistance, config.SpawnMaxDistance, config.SpawnAttempts, rng)) {
            Object r = judge(g, c[0], c[1], cy, config.OpenSkyAbove, needWater, config.WaterSearchRadius);
            if (r instanceof Spot s) out.add(s);
            else if (why != null && why.length() < 300) why.append(' ').append(c[0]).append(',').append(c[1]).append(':').append(r);
        }
        return out;
    }
}
