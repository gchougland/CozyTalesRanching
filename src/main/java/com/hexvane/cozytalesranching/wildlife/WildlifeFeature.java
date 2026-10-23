package com.hexvane.cozytalesranching.wildlife;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import javax.annotation.Nonnull;

/** Owned by the wildlife role: wild visitors attracted by habitats, rare variants. */
public final class WildlifeFeature {
    private WildlifeFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {}

    /** Called from /ranch reload. */
    public static void reload() {}
}