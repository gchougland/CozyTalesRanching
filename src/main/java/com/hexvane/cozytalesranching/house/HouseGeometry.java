package com.hexvane.cozytalesranching.house;

import javax.annotation.Nonnull;

/**
 * Where a house's door is and which cells it covers, from its hitbox bounds. Block units relative to the
 * origin cell's corner, as the engine's {@code BlockBoundingBoxes} store them. At rotation 0 every house
 * faces +Z (its door side); {@link #rotate} turns bounds exactly as the engine turns hitboxes for a yaw.
 */
final class HouseGeometry {
    /** How far outside the front face the door point sits. */
    static final double DOOR_GAP = 0.6;

    record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    record Door(double x, double y, double z) {}

    private HouseGeometry() {}

    /** Same mapping as {@code BlockBoundingBoxes.rotate90Y/180Y/270Y}: yaw 1 sends (x, z) to (z, 1 - x). */
    @Nonnull
    static Bounds rotate(@Nonnull Bounds b, int yaw) {
        double minX, minZ, maxX, maxZ;
        switch (Math.floorMod(yaw, 4)) {
            case 1 -> { minX = b.minZ; minZ = 1 - b.minX; maxX = b.maxZ; maxZ = 1 - b.maxX; }
            case 2 -> { minX = 1 - b.minX; minZ = 1 - b.minZ; maxX = 1 - b.maxX; maxZ = 1 - b.maxZ; }
            case 3 -> { minX = 1 - b.minZ; minZ = b.minX; maxX = 1 - b.maxZ; maxZ = b.maxX; }
            default -> { return b; }
        }
        return new Bounds(Math.min(minX, maxX), b.minY, Math.min(minZ, maxZ), Math.max(minX, maxX), b.maxY, Math.max(minZ, maxZ));
    }

    /** The door side for a yaw: 0 +Z, 1 +X, 2 -Z, 3 -X (the +Z front turned like the hitbox). */
    @Nonnull
    static int[] front(int yaw) {
        return switch (Math.floorMod(yaw, 4)) {
            case 1 -> new int[] {1, 0};
            case 2 -> new int[] {0, -1};
            case 3 -> new int[] {-1, 0};
            default -> new int[] {0, 1};
        };
    }

    /** Just outside the middle of the front face, on the house's floor level. {@code rotated} is already turned for {@code yaw}. */
    @Nonnull
    static Door door(@Nonnull Bounds rotated, int yaw, int ox, int oy, int oz) {
        double cx = (rotated.minX + rotated.maxX) / 2;
        double cz = (rotated.minZ + rotated.maxZ) / 2;
        int[] f = front(yaw);
        double dx = f[0] > 0 ? rotated.maxX + DOOR_GAP : f[0] < 0 ? rotated.minX - DOOR_GAP : cx;
        double dz = f[1] > 0 ? rotated.maxZ + DOOR_GAP : f[1] < 0 ? rotated.minZ - DOOR_GAP : cz;
        return new Door(ox + dx, oy, oz + dz);
    }

    /** True if block cell (px, py, pz) is one of the cells the rotated bounds occupy around origin (ox, oy, oz). */
    static boolean covers(@Nonnull Bounds rotated, int ox, int oy, int oz, int px, int py, int pz) {
        return inCells(px - ox, rotated.minX, rotated.maxX)
            && inCells(py - oy, rotated.minY, rotated.maxY)
            && inCells(pz - oz, rotated.minZ, rotated.maxZ);
    }

    private static boolean inCells(int cell, double min, double max) {
        int lo = (int) Math.floor(min + 1e-6);
        int hi = Math.max(lo, (int) Math.ceil(max - 1e-6) - 1);
        return cell >= lo && cell <= hi;
    }
}
