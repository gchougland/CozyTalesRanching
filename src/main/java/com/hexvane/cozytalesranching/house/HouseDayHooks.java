package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * The house's day, for features that act on animals while they are home. The {@code house} role calls
 * {@link #fireNightfall} once per house per in-game evening after the residents went in, and
 * {@link #fireMorning} once per house per in-game morning <b>before</b> it brings the residents out.
 * A morning hook may create new records in the house with state {@code STORED} (babies); the house brings
 * out every STORED resident after all hooks ran. World thread, outside system ticks; hooks must be cheap.
 */
public final class HouseDayHooks {
    public interface Hook {
        default void onNightfall(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {}

        default void onMorning(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {}
    }

    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final List<Hook> HOOKS = new CopyOnWriteArrayList<>();

    private HouseDayHooks() {}

    public static void register(@Nonnull Hook hook) {
        HOOKS.add(hook);
    }

    public static void fireNightfall(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {
        for (Hook h : HOOKS) {
            try {
                h.onNightfall(world, house, residents);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "House nightfall hook failed for " + house.houseId, e);
            }
        }
    }

    public static void fireMorning(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {
        for (Hook h : HOOKS) {
            try {
                h.onMorning(world, house, residents);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "House morning hook failed for " + house.houseId, e);
            }
        }
    }
}
