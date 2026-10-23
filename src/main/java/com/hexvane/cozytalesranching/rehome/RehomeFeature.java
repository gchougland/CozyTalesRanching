package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;

/** Owned by the rehome role: ownership transfer, moving between houses, the Butcher's Block, hostile-mob protection. */
public final class RehomeFeature {
    private RehomeFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        plugin.getCodecRegistry(Interaction.CODEC).register(ButcherInteraction.TYPE, ButcherInteraction.class, ButcherInteraction.CODEC);
        plugin.getLogger().atInfo().log("Rehome registered: interaction %s, hostile-mob protection %s",
            ButcherInteraction.TYPE, RanchConfig.get().protectFromHostileMobs() ? "on" : "off");
    }

    /** Called from /ranch reload. */
    public static void reload() {
    }
}
