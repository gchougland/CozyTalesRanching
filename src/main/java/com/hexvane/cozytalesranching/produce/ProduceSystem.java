package com.hexvane.cozytalesranching.produce;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** Adds up time per world and every {@code PassSeconds} queues that world's production pass, outside the tick. */
final class ProduceSystem extends TickingSystem<EntityStore> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private final ProduceService service;
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();

    ProduceSystem(@Nonnull ProduceService service) {
        this.service = service;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total < ProduceTuning.get().PassSeconds) return;
        elapsed.put(world.getName(), 0f);
        world.execute(() -> {
            try {
                service.pass(world, System.currentTimeMillis(), -1);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Produce pass failed in world " + world.getName(), e);
            }
        });
    }
}
