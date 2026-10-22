package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.animal.Ranch;
import javax.annotation.Nonnull;

/** Owned by the houseui role: the house page, installed with {@code Ranch.installHousePages}. */
public final class HouseUiFeature {
    private HouseUiFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        Ranch.installHousePages(HousePage::open);
    }
}
