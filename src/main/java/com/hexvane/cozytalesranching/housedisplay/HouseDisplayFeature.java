package com.hexvane.cozytalesranching.housedisplay;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.api.event.RanchHouseRemovedEvent;
import com.hexvane.cozytalesranching.house.HouseDayHooks;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Residents shown on their house while they are inside (Gibson's note 15): birds on roosts and perches, snails and
 * geckos in their terrariums, frogs on lily pads. Points per house block in {@code HouseDisplay.json}.
 * <p>
 * Driven by the house's own day: the nightfall hook (after the residents went in) shows them, the morning hook
 * (just before they come out) takes them down, a removed house takes its shown animals with it, and a slow check
 * ({@link HouseDisplaySystem}) catches the rest. Needs no change to the house code.
 */
public final class HouseDisplayFeature {
    @Nullable private static volatile Path dataDirectory;

    private HouseDisplayFeature() {}

    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        dataDirectory = plugin.getDataDirectory();
        HouseDisplayData.load(plugin.getDataDirectory());
        plugin.getEntityStoreRegistry().registerSystem(new HouseDisplaySystem());
        HouseDayHooks.register(new HouseDayHooks.Hook() {
            @Override
            public void onNightfall(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {
                HouseDisplayService.refresh(world, house);
            }

            @Override
            public void onMorning(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {
                HouseDisplayService.hideAll(world, house.houseId);
            }
        });
        plugin.getEventRegistry().register(RanchHouseRemovedEvent.class, e -> {
            World world = Universe.get().getWorld(e.getWorldName());
            if (world != null) world.execute(() -> HouseDisplayService.hideAll(world, e.getHouseId()));
        });
        plugin.getLogger().atInfo().log("House display registered: %d house blocks with points", HouseDisplayData.get().Houses.size());
    }

    /** Re-reads the points; the next check moves, adds or takes down shown animals to match. */
    public static void reload() {
        Path dir = dataDirectory;
        if (dir != null) HouseDisplayData.load(dir);
        HouseDisplayService.clearCaches();
    }
}
