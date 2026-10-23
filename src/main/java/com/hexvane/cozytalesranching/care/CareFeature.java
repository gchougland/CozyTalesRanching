package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.api.event.RanchHousePlacedEvent;
import com.hexvane.cozytalesranching.house.HouseDayHooks;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import javax.annotation.Nonnull;

/** Owned by the care role: troughs, feeding from troughs, advanced feed, habitat and toys, happiness. */
public final class CareFeature {
    /** With the animal registry's own flush: after the worlds shut down. */
    private static final short SAVE_ON_SHUTDOWN = ShutdownEvent.SHUTDOWN_WORLDS + 4;

    private CareFeature() {}

    /**
     * Called once from the plugin's setup(), before {@code BreedingFeature.register}: the morning hook added here runs
     * before breeding's, so a house that was never scanned is scanned before breeding decides.
     */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        CareService care = new CareService(plugin.getDataDirectory());
        care.load();
        CareService.install(care);
        plugin.getCodecRegistry(Interaction.CODEC).register(TroughInteraction.TYPE, TroughInteraction.class, TroughInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(GiveFeedInteraction.TYPE, GiveFeedInteraction.class, GiveFeedInteraction.CODEC);
        plugin.getEntityStoreRegistry().registerSystem(new TroughBreakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CareSystem());
        plugin.getEventRegistry().register(RanchHousePlacedEvent.class, event -> CareWorld.of(event.getWorldName()).housePlaced(event.getHouseId()));
        HouseDayHooks.register(new HouseDayHooks.Hook() {
            @Override
            public void onMorning(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {
                if (CareWorld.of(world).scanIfNeverScanned(world, care, house, System.currentTimeMillis())) {
                    plugin.getLogger().atInfo().log("Care: first habitat scan of house %s at morning: requirements met %s",
                        house.houseId, house.habitatRequirementsMet);
                }
            }
        });
        plugin.getEventRegistry().register(SAVE_ON_SHUTDOWN, ShutdownEvent.class, event -> care.close());
        Runtime.getRuntime().addShutdownHook(new Thread(care::close, "CozyRanch-CareShutdown"));
        plugin.getLogger().atInfo().log("Care registered: interactions %s and %s, trough break and care systems, house placed listener, morning scan hook",
            TroughInteraction.TYPE, GiveFeedInteraction.TYPE);
    }

    /** Re-reads Habitat.json (bundled, or the server's override in the plugin data folder). */
    public static void reload() {
        CareService care = CareService.get();
        if (care != null) care.reloadHabitat();
    }
}
