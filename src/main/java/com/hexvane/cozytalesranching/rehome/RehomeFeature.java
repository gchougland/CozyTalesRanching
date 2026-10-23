package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import javax.annotation.Nonnull;

/** Owned by the rehome role: ownership transfer, moving between houses, the Butcher's Block, hostile-mob protection. */
public final class RehomeFeature {
    private RehomeFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {}

    /** Called from /ranch reload. */
    public static void reload() {}
}