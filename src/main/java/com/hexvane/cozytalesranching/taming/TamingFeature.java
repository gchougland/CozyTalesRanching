package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;

/** Owned by the taming role: befriending wild animals, naming, following and teleporting, blocked worlds. */
public final class TamingFeature {
    private TamingFeature() {}

    /** Called once from the plugin's setup(). Register components, systems, interactions and sub-commands here. */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        plugin.getCodecRegistry(Interaction.CODEC).register(OfferFoodInteraction.TYPE, OfferFoodInteraction.class, OfferFoodInteraction.CODEC);
        plugin.getEntityStoreRegistry().registerSystem(new UseAnimalSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FollowSystem());
        plugin.getLogger().atInfo().log("Taming registered: interaction %s, use and follow systems", OfferFoodInteraction.TYPE);
    }
}
