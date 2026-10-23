package com.hexvane.cozytalesranching.api.event;

import com.hypixel.hytale.event.IEvent;
import java.util.UUID;
import javax.annotation.Nonnull;

/** A wild animal was attracted to a player's ranch and spawned as a visitor. It is not owned. */
public final class RanchVisitorArrivedEvent implements IEvent<Void> {
    @Nonnull private final UUID ranchOwnerId;
    @Nonnull private final UUID houseId;
    @Nonnull private final String speciesId;
    @Nonnull private final String roleId;
    private final boolean rare;
    @Nonnull private final String worldName;

    public RanchVisitorArrivedEvent(@Nonnull UUID ranchOwnerId, @Nonnull UUID houseId, @Nonnull String speciesId,
                                    @Nonnull String roleId, boolean rare, @Nonnull String worldName) {
        this.ranchOwnerId = ranchOwnerId;
        this.houseId = houseId;
        this.speciesId = speciesId;
        this.roleId = roleId;
        this.rare = rare;
        this.worldName = worldName;
    }

    /** the owner of the house whose habitat attracted it */
    @Nonnull public UUID getRanchOwnerId() { return ranchOwnerId; }
    /** the house whose habitat attracted it */
    @Nonnull public UUID getHouseId() { return houseId; }
    @Nonnull public String getSpeciesId() { return speciesId; }
    /** the wild NPC role it spawned with (a rare variant has its own role) */
    @Nonnull public String getRoleId() { return roleId; }
    public boolean isRare() { return rare; }
    @Nonnull public String getWorldName() { return worldName; }
}
