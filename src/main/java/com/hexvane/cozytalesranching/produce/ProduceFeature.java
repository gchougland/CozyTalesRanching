package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import javax.annotation.Nonnull;

/** Owned by the produce role: products in houses and automatic collection. */
public final class ProduceFeature {
    private ProduceFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {}

    /** Called from /ranch reload. */
    public static void reload() {}
}