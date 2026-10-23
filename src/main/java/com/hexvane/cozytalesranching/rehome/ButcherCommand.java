package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code /ranch butcher}: the same as using a Butcher's Block within five blocks of you. For players who prefer
 * typing, and for the headless test player, who cannot use blocks.
 */
public final class ButcherCommand extends AbstractPlayerCommand {
    public ButcherCommand() {
        super("butcher", "cozyranch_rehome.commands.butcher.desc");
        this.setPermissionGroups("hytale:Adventurer");
        this.requirePermission(RanchPermissions.BUTCHER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Vector3d at = RehomeAnimals.position(ref, store);
        Vector3d block = at == null ? null : ButcherService.findBlockNear(world, at);
        if (block == null) {
            playerRef.sendMessage(Message.translation("cozyranch_rehome.butcher.no_block"));
            return;
        }
        ButcherService.process(world, playerRef, ref, block);
    }
}
