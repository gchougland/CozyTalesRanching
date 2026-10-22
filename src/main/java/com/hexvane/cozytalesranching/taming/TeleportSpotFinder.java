package com.hexvane.cozytalesranching.taming;

import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Picks where a lagging follower reappears: a few blocks behind the owner if that is safe, then to the
 * sides, then in front. Safe means a solid floor that does not hurt, and two clear cells above it with no
 * fluid. Pure apart from the {@link BlockGrid} it reads, so it is tested on a fake grid.
 */
public final class TeleportSpotFinder {
    /** What the finder needs to know about one cell. Unloaded cells must answer false to both. */
    public interface BlockGrid {
        /** A full floor the animal can stand on that does not damage it. */
        boolean isSafeFloor(int x, int y, int z);

        /** No collision, no fluid, nothing that burns or hurts. */
        boolean isClear(int x, int y, int z);
    }

    public record Spot(double x, double y, double z) {}

    private static final double[] DISTANCES = {2.5, 3.5};
    /** Degrees from straight behind the owner, in order of preference. */
    private static final double[] ANGLES = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
    private static final int[] HEIGHT_STEPS = {0, 1, -1, 2, -2, 3, -3};

    private TeleportSpotFinder() {}

    /**
     * @param lookX owner's horizontal facing, need not be normalised; (0, 0) counts as facing +Z
     */
    @Nonnull
    public static Optional<Spot> find(double ownerX, double ownerY, double ownerZ, double lookX, double lookZ, @Nonnull BlockGrid grid) {
        double len = Math.hypot(lookX, lookZ);
        double fx = len < 1e-6 ? 0 : lookX / len;
        double fz = len < 1e-6 ? 1 : lookZ / len;
        double backX = -fx;
        double backZ = -fz;
        int baseY = (int) Math.floor(ownerY + 1e-3);
        for (double angle : ANGLES) {
            double rad = Math.toRadians(angle);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double dx = backX * cos - backZ * sin;
            double dz = backX * sin + backZ * cos;
            for (double dist : DISTANCES) {
                int x = (int) Math.floor(ownerX + dx * dist);
                int z = (int) Math.floor(ownerZ + dz * dist);
                for (int step : HEIGHT_STEPS) {
                    int y = baseY + step;
                    if (isStandable(grid, x, y, z)) return Optional.of(new Spot(x + 0.5, y, z + 0.5));
                }
            }
        }
        return Optional.empty();
    }

    static boolean isStandable(@Nonnull BlockGrid grid, int x, int y, int z) {
        return grid.isSafeFloor(x, y - 1, z) && grid.isClear(x, y, z) && grid.isClear(x, y + 1, z);
    }
}
