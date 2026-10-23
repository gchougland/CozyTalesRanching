package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** {@code /ranch accept} and {@code /ranch decline}: answer the animal someone offered you in the last minute. */
public final class AcceptCommand extends AbstractPlayerCommand {
    private final boolean accept;

    private AcceptCommand(@Nonnull String name, @Nonnull String descKey, boolean accept) {
        super(name, descKey);
        this.accept = accept;
        this.setPermissionGroups("hytale:Adventurer");
        this.requirePermission(RanchPermissions.TRANSFER);
    }

    @Nonnull
    public static AcceptCommand accept() {
        return new AcceptCommand("accept", "cozyranch_rehome.commands.accept.desc", true);
    }

    @Nonnull
    public static AcceptCommand decline() {
        return new AcceptCommand("decline", "cozyranch_rehome.commands.decline.desc", false);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (accept) TransferService.accept(playerRef);
        else TransferService.decline(playerRef);
    }
}
