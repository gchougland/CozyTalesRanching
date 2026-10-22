package com.hexvane.cozytalesranching.breeding;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.house.HouseDayHooks;
import javax.annotation.Nonnull;

/** Owned by the breeding role: pairs in a house, babies, growing up. */
public final class BreedingFeature {
    private static final BreedingService SERVICE = new BreedingService();

    private BreedingFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        TraitRegistry.load(plugin.getDataDirectory());
        HouseDayHooks.register(SERVICE);
        plugin.getEntityStoreRegistry().registerSystem(new GrowthSystem(SERVICE));
        plugin.getLogger().atInfo().log("Breeding registered: morning hook, grow-up pass, %d traits", TraitRegistry.get().Traits.size());
    }

    @Nonnull
    static BreedingService service() {
        return SERVICE;
    }
}
