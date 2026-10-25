package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Owned by the houseui role: the house page, installed with {@code Ranch.installHousePages}. */
public final class HouseUiFeature {
    private HouseUiFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        Ranch.installHousePages(new Ranch.HousePageOpener() {
            @Override
            public boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house) {
                return HousePage.open(playerRef, store, house);
            }

            @Override
            public boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house,
                                @Nullable HouseService.Result refusal) {
                return HousePage.open(playerRef, store, house, refusal);
            }
        });
        plugin.getEntityStoreRegistry().registerSystem(new HouseBorderSystem());
    }
}
