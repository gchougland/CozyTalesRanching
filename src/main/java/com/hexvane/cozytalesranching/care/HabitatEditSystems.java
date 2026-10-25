package com.hexvane.cozytalesranching.care;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * A block placed or broken in a house's scan area queues that house for a rescan a moment later
 * ({@link CareWorld#blockEdited}), so flowers or hay put down by the coop show on the house page without waiting for
 * the slow round of scans. The work is queued with {@link World#execute}; nothing is read during the event.
 */
final class HabitatEditSystems {
    private HabitatEditSystems() {}

    private static void edited(@Nonnull Store<EntityStore> store, @Nonnull Vector3i at) {
        CareService care = CareService.get();
        if (care == null) return;
        World world = store.getExternalData().getWorld();
        int x = at.x, y = at.y, z = at.z;
        world.execute(() -> CareWorld.of(world).blockEdited(world, care, x, y, z, System.currentTimeMillis()));
    }

    static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        Place() {
            super(PlaceBlockEvent.class);
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
            if (!event.isCancelled()) edited(store, new Vector3i(event.getTargetBlock()));
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }

    static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        Break() {
            super(BreakBlockEvent.class);
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
            if (!event.isCancelled()) edited(store, new Vector3i(event.getTargetBlock()));
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }
}
