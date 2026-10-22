package com.hexvane.cozytalesranching.api.event;

import com.hypixel.hytale.event.IEvent;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** An animal house is gone (its block was broken or removed). Its residents have already been unassigned. */
public final class RanchHouseRemovedEvent implements IEvent<Void> {
    @Nonnull private final UUID houseId;
    @Nullable private final UUID ownerId;
    @Nullable private final UUID breakerId;
    @Nonnull private final String speciesId;
    @Nonnull private final String worldName;

    public RanchHouseRemovedEvent(@Nonnull UUID houseId, @Nullable UUID ownerId, @Nullable UUID breakerId, @Nonnull String speciesId, @Nonnull String worldName) {
        this.houseId = houseId;
        this.ownerId = ownerId;
        this.breakerId = breakerId;
        this.speciesId = speciesId;
        this.worldName = worldName;
    }

    @Nonnull public UUID getHouseId() { return houseId; }
    /** Null for a house nobody owns. */
    @Nullable public UUID getOwnerId() { return ownerId; }
    /** The player who broke it; null when the block went another way (an explosion, a command, a builder tool). */
    @Nullable public UUID getBreakerId() { return breakerId; }
    @Nonnull public String getSpeciesId() { return speciesId; }
    @Nonnull public String getWorldName() { return worldName; }
}
