package com.hexvane.cozytalesranching.behaviour;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Adds up time per world and queues one planner pass a second as a world task (outside the tick). */
final class BehaviourSystem extends TickingSystem<EntityStore> {
    private static final float PASS_SECONDS = 1f;

    private final BehaviourPlanner planner;
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();

    BehaviourSystem(@Nonnull BehaviourPlanner planner) {
        this.planner = planner;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total < PASS_SECONDS) return;
        elapsed.put(world.getName(), 0f);
        world.execute(() -> planner.pass(world));
    }
}
