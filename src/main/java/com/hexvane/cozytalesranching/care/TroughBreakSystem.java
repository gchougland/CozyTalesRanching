package com.hexvane.cozytalesranching.care;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * A trough being broken drops what it holds. The event can still be cancelled by someone else, so the drop
 * waits for the next {@link World#execute} and happens only if the trough is really gone by then.
 */
final class TroughBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    TroughBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
        if (!TroughBlocks.isTrough(event.getBlockType())) return;
        CareService care = CareService.get();
        if (care == null) return;
        World world = store.getExternalData().getWorld();
        Vector3i pos = TroughBlocks.base(world, event.getTargetBlock());
        if (care.store().trough(world.getName(), pos.x, pos.y, pos.z).isEmpty()) return;
        world.execute(() -> CareWorld.syncTrough(world, care, pos.x, pos.y, pos.z));
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
