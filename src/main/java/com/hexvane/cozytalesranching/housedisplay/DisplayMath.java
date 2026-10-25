package com.hexvane.cozytalesranching.housedisplay;

import javax.annotation.Nonnull;

/**
 * Where a display point lands in the world. Pure math, no engine types, so it is unit tested.
 * <p>
 * A point is written in the house model's own units (32 a block, as in Blockbench) from the bottom centre of the
 * origin cell, for the house as it stands at rotation 0 (door side +Z). A placed house turns its model about that
 * cell's centre by quarter turns; yaw 1 sends a vector (x, z) to (z, -x), the same mapping as the hitboxes
 * ({@code BlockBoundingBoxes.rotate90Y}) and the house door ({@code HouseGeometry}).
 * <p>
 * A point's {@code Yaw} is the way the animal looks, in degrees in the house frame: 0 looks out of the door side
 * (+Z), 90 towards the house's +X side. Entity rotation is a heading whose forward vector is (-sin yaw, 0, -cos yaw)
 * ({@code PhysicsMath.headingX/Z}), so the heading is {@code atan2(-dx, -dz)} of the turned look direction.
 */
public final class DisplayMath {
    /** Model units per block for block models. */
    public static final double UNITS_PER_BLOCK = 32.0;

    public record Placement(double x, double y, double z, float yawRadians) {}

    private DisplayMath() {}

    /** Turns a horizontal vector by a block yaw index (0..3, any int is taken mod 4). */
    @Nonnull
    static double[] turn(double u, double v, int rotation) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new double[] {v, -u};
            case 2 -> new double[] {-u, -v};
            case 3 -> new double[] {-v, u};
            default -> new double[] {u, v};
        };
    }

    /**
     * The world position (entity feet) and heading of a point on a house whose origin block is (ox, oy, oz) and
     * whose block rotation index is {@code rotation}.
     *
     * @param px, py, pz point position in model units at rotation 0
     * @param yawDegrees look direction in the house frame (0 = door side)
     */
    @Nonnull
    public static Placement place(double px, double py, double pz, double yawDegrees, int ox, int oy, int oz, int rotation) {
        double[] p = turn(px / UNITS_PER_BLOCK, pz / UNITS_PER_BLOCK, rotation);
        double a = Math.toRadians(yawDegrees);
        double[] d = turn(Math.sin(a), Math.cos(a), rotation);
        float heading = (float) Math.atan2(-d[0], -d[1]);
        return new Placement(ox + 0.5 + p[0], oy + py / UNITS_PER_BLOCK, oz + 0.5 + p[1], heading);
    }

    /**
     * The model scale a display uses: the point's scale times the middle of the model's own random scale range, so
     * a baby (whose model asset is scaled down) stays smaller than an adult on the same perch.
     */
    public static float scale(double pointScale, float modelMinScale, float modelMaxScale) {
        float natural = (modelMinScale > 0 && modelMaxScale > 0) ? (modelMinScale + modelMaxScale) / 2f : 1f;
        return (float) Math.max(0.05, pointScale * natural);
    }
}
