package com.hexvane.cozytalesranching.behaviour;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import javax.annotation.Nonnull;

/** Owned by the behaviour role: flavour behaviours, personality, babies following parents. */
public final class BehaviourFeature {
    private BehaviourFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {}

    /** Called from /ranch reload. */
    public static void reload() {}
}