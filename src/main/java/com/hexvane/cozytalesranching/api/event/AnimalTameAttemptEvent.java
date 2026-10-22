package com.hexvane.cozytalesranching.api.event;

import com.hypixel.hytale.event.ICancellable;
import com.hypixel.hytale.event.IEvent;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * A player is about to befriend a wild animal (the last feeding before it becomes theirs). Cancel it to
 * stop the taming; the player sees the animal refuse. Fired after the mod's own checks (blocked worlds,
 * permissions, limits) have passed.
 */
public final class AnimalTameAttemptEvent implements IEvent<Void>, ICancellable {
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID entityUuid;
    @Nonnull private final String speciesId;
    @Nonnull private final String roleId;
    @Nonnull private final String worldName;
    private boolean cancelled;

    public AnimalTameAttemptEvent(@Nonnull UUID playerId, @Nonnull UUID entityUuid, @Nonnull String speciesId, @Nonnull String roleId, @Nonnull String worldName) {
        this.playerId = playerId;
        this.entityUuid = entityUuid;
        this.speciesId = speciesId;
        this.roleId = roleId;
        this.worldName = worldName;
    }

    @Nonnull public UUID getPlayerId() { return playerId; }
    @Nonnull public UUID getEntityUuid() { return entityUuid; }
    @Nonnull public String getSpeciesId() { return speciesId; }
    @Nonnull public String getRoleId() { return roleId; }
    @Nonnull public String getWorldName() { return worldName; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
