package com.hexvane.cozytalesranching.taming;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sees every Use a player aims at an entity. Anything that is not a supported animal returns at once, so other
 * entities, other mods and unsupported species keep their own behaviour.
 */
final class UseAnimalSystem extends EntityEventSystem<EntityStore, UseEntityEvent.Pre> {
    UseAnimalSystem() {
        super(UseEntityEvent.Pre.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UseEntityEvent.Pre event
    ) {
        if (event.isCancelled() || event.getInteractionType() != InteractionType.Use) return;
        if (AnimalInteractions.use(chunk.getReferenceTo(index), event.getTargetEntity(), event.getContext().getHeldItem(), commandBuffer)) {
            event.setCancelled(true);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
