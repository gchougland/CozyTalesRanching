package com.hexvane.cozytalesranching.predator;

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
 * A baited bowl that is broken drops its bait. The break can still be cancelled by someone else, so the drop waits for
 * the next {@link World#execute} and happens only if the bowl is really gone then. A bowl that vanished another way
 * (explosion, paste) is noticed by the lure pass's presence check the same way.
 */
final class BowlBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    BowlBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
        if (!BaitBowls.isBowl(event.getBlockType())) return;
        Predators p = Predators.get();
        if (p == null) return;
        World world = store.getExternalData().getWorld();
        Vector3i pos = new Vector3i(event.getTargetBlock());
        if (p.bowls.bait(world.getName(), pos.x, pos.y, pos.z) == null) return;
        world.execute(() -> dropIfGone(p, world, pos));
    }

    static void dropIfGone(@Nonnull Predators p, @Nonnull World world, @Nonnull Vector3i pos) {
        if (BaitBowls.presence(world, pos.x, pos.y, pos.z) != BaitBowls.Presence.GONE) return;
        String bait = p.bowls.take(world.getName(), pos.x, pos.y, pos.z);
        if (bait != null) BaitBowls.drop(world.getEntityStore().getStore(), pos.x, pos.y, pos.z, bait);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
