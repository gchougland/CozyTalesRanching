package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import javax.annotation.Nonnull;

/** Owned by the animal role: persistent records, deduplication, adopting animals tamed before this mod, protection, recovery, the compatibility guard. */
public final class AnimalFeature {
    private AnimalFeature() {}

    /** Called once from the plugin's setup(). Register components, systems, interactions and sub-commands here. */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
    }
}