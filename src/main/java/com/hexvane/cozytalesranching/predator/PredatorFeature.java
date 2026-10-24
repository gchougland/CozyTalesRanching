package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;

/** Bait Bowls and Calm for wild predators (fox, wolf, bear, snow leopard and whatever species files mark Predator). */
public final class PredatorFeature {
    private static final short SAVE_ON_SHUTDOWN = ShutdownEvent.SHUTDOWN_WORLDS + 4;

    private PredatorFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        BowlStore bowls = new BowlStore(plugin.getDataDirectory().resolve("BaitBowls.json"));
        bowls.load();
        Predators.install(new Predators(bowls));
        plugin.getCodecRegistry(Interaction.CODEC).register(BaitBowlInteraction.TYPE, BaitBowlInteraction.class, BaitBowlInteraction.CODEC);
        plugin.getEntityStoreRegistry().registerSystem(new PredatorAttitude());
        plugin.getEntityStoreRegistry().registerSystem(new PredatorSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PredatorDamage());
        plugin.getEntityStoreRegistry().registerSystem(new BowlBreakSystem());
        plugin.getEventRegistry().register(SAVE_ON_SHUTDOWN, ShutdownEvent.class, event -> bowls.close());
        plugin.getLogger().atInfo().log("Predators registered: interaction %s, attitude provider, calm guard, %d baited bowl(s) remembered", BaitBowlInteraction.TYPE, bowls.size());
    }
}
