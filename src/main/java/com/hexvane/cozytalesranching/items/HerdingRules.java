package com.hexvane.cozytalesranching.items;

/**
 * Pure rules for the Herding Horn, kept apart from the engine so they can be unit tested: how far the call carries
 * and how often a player can blow it.
 */
public final class HerdingRules {
    public static final double DEFAULT_RADIUS = 32.0;
    public static final double MIN_RADIUS = 4.0;
    public static final double MAX_RADIUS = 64.0;
    public static final long DEFAULT_COOLDOWN_MS = 5_000L;

    private HerdingRules() {}

    /** The radius from the item data, kept between 4 and 64 blocks; anything not a positive number gives the default. */
    public static double radius(double fromData) {
        if (Double.isNaN(fromData) || fromData <= 0) return DEFAULT_RADIUS;
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, fromData));
    }

    /** True when an animal this far from the player (in blocks on each axis) hears the horn. */
    public static boolean inReach(double dx, double dy, double dz, double radius) {
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** True when the horn may be blown again; {@code lastMs} 0 means never blown. A clock that went back never locks it. */
    public static boolean ready(long lastMs, long nowMs, long cooldownMs) {
        return lastMs <= 0 || nowMs < lastMs || nowMs - lastMs >= cooldownMs;
    }
}
