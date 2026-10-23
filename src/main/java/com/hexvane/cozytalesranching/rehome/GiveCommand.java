package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * {@code /ranch give <animal> <player> [--force]}: offers one of your animals to another online player, who accepts
 * with {@code /ranch accept}. {@code <animal>} is {@code this} (the one you look at), its name or the start of its id.
 * {@code --force} (needs {@code cozyranch.admin.manageany}) transfers anyone's animal at once.
 */
public final class GiveCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> animalArg;
    private final RequiredArg<PlayerRef> playerArg;
    private final FlagArg force;

    public GiveCommand() {
        super("give", "cozyranch_rehome.commands.give.desc");
        this.setPermissionGroups("hytale:Adventurer");
        this.requirePermission(RanchPermissions.TRANSFER);
        this.animalArg = withRequiredArg("animal", "cozyranch_rehome.commands.arg.animal", ArgTypes.STRING);
        this.playerArg = withRequiredArg("player", "cozyranch_rehome.commands.give.arg.player", ArgTypes.PLAYER_REF);
        this.force = withFlagArg("force", "cozyranch_rehome.commands.give.force");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        boolean forced = Boolean.TRUE.equals(force.get(context));
        boolean anyOwner = forced && RanchPermissions.has(playerRef.getUuid(), RanchPermissions.ADMIN_MANAGE_ANY);
        PlayerRef recipient = context.get(playerArg);
        RehomeAnimals.resolve(context.get(animalArg), playerRef, ref, store, world, anyOwner)
            .ifPresent(record -> TransferService.give(playerRef, record, recipient, forced));
    }
}
