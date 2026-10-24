package com.hexvane.cozytalesranching.items;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Where a handful of Bat Guano goes. Pure logic; {@link GuanoSpreadInteraction} feeds it the aimed block and applies
 * vanilla's fertilized state to each tilled soil it names.
 */
public final class GuanoRules {
    /** Default half-width of the patch: 1 is the aimed soil and its eight neighbours (3 x 3). */
    public static final int DEFAULT_RADIUS = 1;
    /** Largest half-width a JSON may ask for (5 x 5). */
    public static final int MAX_RADIUS = 2;

    private GuanoRules() {}

    /** The configured radius made safe: unset or below 0 is the default, above the cap is the cap. */
    public static int radius(int configured) {
        if (configured < 0) return DEFAULT_RADIUS;
        return Math.min(configured, MAX_RADIUS);
    }

    /** The (dx, dz) offsets of the patch, the aimed block first so it always gets its share. */
    @Nonnull
    public static List<int[]> offsets(int radius) {
        int r = radius(radius);
        List<int[]> out = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        out.add(new int[] {0, 0});
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx != 0 || dz != 0) out.add(new int[] {dx, dz});
            }
        }
        return out;
    }

    /**
     * The heights to look for soil in one column: the aimed height first (aiming at soil), then one below (aiming at
     * a crop, whose soil is underneath), as vanilla's {@code FertilizeSoil} does for the aimed block.
     */
    @Nonnull
    public static int[] heights(int aimedY) {
        return new int[] {aimedY, aimedY - 1};
    }

    /** Whether a handful is used up: only when at least one soil block took it. */
    public static boolean consumes(int fertilised) {
        return fertilised > 0;
    }
}
