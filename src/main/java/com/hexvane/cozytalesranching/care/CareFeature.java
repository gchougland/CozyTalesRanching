package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;

/** Owned by the care role: troughs, feeding from troughs, habitat, happiness. */
public final class CareFeature {
    /** With the animal registry's own flush: after the worlds shut down. */
    private static final short SAVE_ON_SHUTDOWN = ShutdownEvent.SHUTDOWN_WORLDS + 4;

    private CareFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        CareService care = new CareService(plugin.getDataDirectory());
        care.load();
        CareService.install(care);
        plugin.getCodecRegistry(Interaction.CODEC).register(TroughInteraction.TYPE, TroughInteraction.class, TroughInteraction.CODEC);
        plugin.getEntityStoreRegistry().registerSystem(new TroughBreakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CareSystem());
        plugin.getEventRegistry().register(SAVE_ON_SHUTDOWN, ShutdownEvent.class, event -> care.close());
        Runtime.getRuntime().addShutdownHook(new Thread(care::close, "CozyRanch-CareShutdown"));
        plugin.getLogger().atInfo().log("Care registered: interaction %s, trough break and care systems", TroughInteraction.TYPE);
    }

    /** Re-reads Habitat.json (bundled, or the server's override in the plugin data folder). */
    public static void reload() {
        CareService care = CareService.get();
        if (care != null) care.reloadHabitat();
    }
}
