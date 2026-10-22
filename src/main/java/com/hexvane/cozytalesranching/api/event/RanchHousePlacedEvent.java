package com.hexvane.cozytalesranching.api.event;

import com.hypixel.hytale.event.IEvent;
import java.util.UUID;
import javax.annotation.Nonnull;

/** A player placed an animal house. */
public final class RanchHousePlacedEvent implements IEvent<Void> {
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID houseId;
    @Nonnull private final String speciesId;
    @Nonnull private final String worldName;

    public RanchHousePlacedEvent(@Nonnull UUID playerId, @Nonnull UUID houseId, @Nonnull String speciesId, @Nonnull String worldName) {
        this.playerId = playerId;
        this.houseId = houseId;
        this.speciesId = speciesId;
        this.worldName = worldName;
    }

    @Nonnull public UUID getPlayerId() { return playerId; }
    @Nonnull public UUID getHouseId() { return houseId; }
    @Nonnull public String getSpeciesId() { return speciesId; }
    @Nonnull public String getWorldName() { return worldName; }
}
