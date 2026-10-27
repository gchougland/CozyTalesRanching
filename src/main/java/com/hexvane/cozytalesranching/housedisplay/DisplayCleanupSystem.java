package com.hexvane.cozytalesranching.housedisplay;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Display entities are only ever made with {@link AddReason#SPAWN}. One that comes in any other way is a stale
 * copy: a display moved into a section with no players near is parked there as a holder (the game keeps it even
 * though it is never saved) and added back with {@code LOAD} when the section ticks again, by then untracked.
 * Such a copy is removed as it arrives. Runs once per marked entity added, never per tick.
 */
final class DisplayCleanupSystem extends RefSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return DisplayMarker.getComponentType();
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        if (reason != AddReason.SPAWN) cb.removeEntity(ref, RemoveReason.REMOVE);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {}
}
