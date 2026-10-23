package com.hexvane.cozytalesranching.wildlife;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** Adds up time per world and every pass interval queues that world's wildlife pass on its own thread, outside the tick. */
final class WildlifeSystem extends TickingSystem<EntityStore> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        WildlifeService service = WildlifeService.get();
        if (service == null) return;
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total * 1000f < service.config().passMs()) return;
        elapsed.put(world.getName(), 0f);
        world.execute(() -> {
            try {
                WildlifeWorld.pass(world, service, System.currentTimeMillis(), false);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Wildlife pass failed in world " + world.getName(), e);
            }
        });
    }
}
