package com.hexvane.cozytalesranching.houseui;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** Every {@link HouseBorder#PULSE_MS} queues the house borders of a world on its own thread, outside the tick. Idle when none are on. */
final class HouseBorderSystem extends TickingSystem<EntityStore> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (!HouseBorder.anyIn(world.getName())) {
            elapsed.remove(world.getName());
            return;
        }
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total * 1000f < HouseBorder.PULSE_MS) return;
        elapsed.put(world.getName(), 0f);
        world.execute(() -> {
            try {
                HouseBorder.pulse(world, System.currentTimeMillis());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "House border pulse failed in world " + world.getName(), e);
            }
        });
    }
}
