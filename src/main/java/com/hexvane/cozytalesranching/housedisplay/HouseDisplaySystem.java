package com.hexvane.cozytalesranching.housedisplay;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * The slow check, every {@code PassSeconds} (5 by default) per world: a float add per tick, then, only in a world
 * with a house that has display points (or something shown), one queued world task that walks those houses
 * through the house registry and their residents through the animal registry's house index. It catches what
 * the nightfall and morning hooks do not: a resident going in late, a chunk loading again, a restart, a
 * resident moved or released while inside, a reload of the points.
 */
final class HouseDisplaySystem extends TickingSystem<EntityStore> {
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total < HouseDisplayData.get().PassSeconds) return;
        elapsed.put(world.getName(), 0f);
        if (!HouseDisplayService.worthPassing(world.getName())) return;
        world.execute(() -> HouseDisplayService.pass(world));
    }
}
