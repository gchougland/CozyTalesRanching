package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Owned by the rehome role: ownership transfer, moving between houses, the Butcher's Block, hostile-mob protection. */
public final class RehomeFeature {
    @Nullable private static volatile Path dataDirectory;

    private RehomeFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        dataDirectory = plugin.getDataDirectory();
        RehomeSettings.load(dataDirectory);
        plugin.getCodecRegistry(Interaction.CODEC).register(ButcherInteraction.TYPE, ButcherInteraction.class, ButcherInteraction.CODEC);
        plugin.getLogger().atInfo().log("Rehome registered: interaction %s, hostile-mob protection %s (Rehome.json fallback)",
            ButcherInteraction.TYPE, RehomeSettings.protectFromHostileMobs() ? "on" : "off");
    }

    /** Called from /ranch reload. */
    public static void reload() {
        RehomeSettings.load(dataDirectory);
    }
}
