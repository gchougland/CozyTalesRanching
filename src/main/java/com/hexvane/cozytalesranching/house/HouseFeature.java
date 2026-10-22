package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Owned by the house role (wave 2): house blocks, assignment, home range, sleeping, the house page. */
public final class HouseFeature {
    /** With the animal registry: after the worlds have shut down. */
    private static final short SAVE_ON_SHUTDOWN = ShutdownEvent.SHUTDOWN_WORLDS + 4;

    @Nullable private static volatile PersistentHouseRegistry registry;

    private HouseFeature() {}

    /** Called once from the plugin's setup(). Register components, systems, interactions and sub-commands here. */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        PersistentHouseRegistry houses = new PersistentHouseRegistry(plugin.getDataDirectory().resolve("Houses"), () -> RanchConfig.get().saveDebounceSeconds());
        houses.load();
        registry = houses;
        Ranch.installHouses(houses);
        Ranch.installHouseService(new HouseServiceImpl());

        plugin.getCodecRegistry(Interaction.CODEC).register(UseHouseInteraction.TYPE, UseHouseInteraction.class, UseHouseInteraction.CODEC);
        plugin.getEntityStoreRegistry().registerSystem(new HousePlacement.PlaceSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HousePlacement.BreakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HouseDaySystem(houses));

        plugin.getEventRegistry().register(SAVE_ON_SHUTDOWN, ShutdownEvent.class, event -> shutdown());
        Runtime.getRuntime().addShutdownHook(new Thread(HouseFeature::shutdown, "CozyRanch-HousesShutdown"));
        plugin.getLogger().atInfo().log("Houses registered: %d houses, interaction %s", houses.size(), UseHouseInteraction.TYPE);
    }

    private static void shutdown() {
        PersistentHouseRegistry houses = registry;
        if (houses != null) houses.close();
    }
}
