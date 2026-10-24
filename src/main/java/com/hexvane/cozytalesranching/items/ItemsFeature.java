package com.hexvane.cozytalesranching.items;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;

/**
 * Owned by the items role: uses for the ranch products. Everything else about the products (foods, crafts, recipes)
 * is asset data; Java covers only the Seed Pouch opening, the Moss Tuft's crop check and opening a find item (Fox Trinket,
 * Shiny Pebble). No systems, no ticks, no
 * saved data.
 */
public final class ItemsFeature {
    private ItemsFeature() {}

    /** Called once from the plugin's setup(), before assets load. */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        plugin.getCodecRegistry(Interaction.CODEC).register(SeedPouchInteraction.TYPE, SeedPouchInteraction.class, SeedPouchInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(MossGrowthInteraction.TYPE, MossGrowthInteraction.class, MossGrowthInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(OpenFindInteraction.TYPE, OpenFindInteraction.class, OpenFindInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(HerdingHornInteraction.TYPE, HerdingHornInteraction.class, HerdingHornInteraction.CODEC);
    }
}
