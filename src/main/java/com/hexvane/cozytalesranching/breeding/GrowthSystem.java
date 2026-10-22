package com.hexvane.cozytalesranching.breeding;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Adds up time per world and once a second queues {@link BreedingService#pass} on the world thread, outside the tick. */
final class GrowthSystem extends TickingSystem<EntityStore> {
    private static final float PASS_SECONDS = 1f;

    private final BreedingService service;
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();
    private final Map<String, Long> passes = new ConcurrentHashMap<>();

    GrowthSystem(@Nonnull BreedingService service) {
        this.service = service;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total < PASS_SECONDS) return;
        elapsed.put(world.getName(), 0f);
        long n = passes.merge(world.getName(), 1L, Long::sum);
        world.execute(() -> service.pass(world, n));
    }
}
