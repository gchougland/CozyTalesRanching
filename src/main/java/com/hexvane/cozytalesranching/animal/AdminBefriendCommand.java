package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.taming.AnimalInteractions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * {@code /ranch admin befriend}: befriends the nearest wild ranch species within 16 blocks at once, through the same
 * code as the last feeding. For testing taming without a client (a test player cannot press Use on an animal).
 */
final class AdminBefriendCommand extends AbstractPlayerCommand {
    private static final double RADIUS = 16;

    AdminBefriendCommand() {
        super("befriend", "cozyranch_animal.commands.admin.befriend.desc");
        this.requirePermission(RanchPermissions.ADMIN);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String species = AnimalInteractions.debugBefriendNearest(playerRef, ref, world, store, RADIUS);
        context.sendMessage(species == null
            ? Message.translation("cozyranch_animal.admin.befriend.none")
            : Message.translation("cozyranch_animal.admin.befriend.done").param("species", species));
    }
}
