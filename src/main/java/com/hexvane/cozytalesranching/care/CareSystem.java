package com.hexvane.cozytalesranching.care;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** Adds up time per world and once a second queues that world's care step on its own thread, outside the tick. */
final class CareSystem extends TickingSystem<EntityStore> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final float STEP_SECONDS = 1f;
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total < STEP_SECONDS) return;
        elapsed.put(world.getName(), 0f);
        CareService care = CareService.get();
        if (care == null) return;
        world.execute(() -> {
            try {
                CareWorld.of(world).step(world, care, System.currentTimeMillis());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Care step failed in world " + world.getName(), e);
            }
        });
    }
}
