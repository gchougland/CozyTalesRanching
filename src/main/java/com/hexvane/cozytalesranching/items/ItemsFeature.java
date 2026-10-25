package com.hexvane.cozytalesranching.items;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;

/**
 * Owned by the items role: uses for the ranch products. Everything else about the products (foods, crafts, recipes)
 * is asset data; Java covers only the Seed Pouch opening, the Bat Guano patch, opening a find item (Fox Trinket,
 * Shiny Pebble), the Rancher's Ledger page and the Raven armour's arrow refund (one event system on vanilla's projectile
 * launch, no ticks). No saved data.
 */
public final class ItemsFeature {
    private ItemsFeature() {}

    /** Called once from the plugin's setup(), before assets load. */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        plugin.getCodecRegistry(Interaction.CODEC).register(SeedPouchInteraction.TYPE, SeedPouchInteraction.class, SeedPouchInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(OpenFindInteraction.TYPE, OpenFindInteraction.class, OpenFindInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(HerdingHornInteraction.TYPE, HerdingHornInteraction.class, HerdingHornInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(GuanoSpreadInteraction.TYPE, GuanoSpreadInteraction.class, GuanoSpreadInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(OpenLedgerInteraction.TYPE, OpenLedgerInteraction.class, OpenLedgerInteraction.CODEC);
        plugin.getEntityStoreRegistry().registerSystem(new RavenFletchingSystem());
    }
}
