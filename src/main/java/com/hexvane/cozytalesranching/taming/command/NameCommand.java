package com.hexvane.cozytalesranching.taming.command;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.taming.TamingRules;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** {@code /ranch name <name>}: names the owned animal the player looks at. */
public final class NameCommand extends AbstractPlayerCommand {
    public static final int MAX_NAME_LENGTH = 24;
    private final RequiredArg<String> name;

    public NameCommand() {
        super("name", "cozyranch_taming.commands.name.desc");
        this.setPermissionGroups("hytale:Adventurer");
        this.requirePermission(RanchPermissions.NAME);
        this.name = withRequiredArg("name", "cozyranch_taming.commands.name.arg", ArgTypes.GREEDY_STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String clean = TamingRules.cleanName(name.get(context), MAX_NAME_LENGTH);
        if (clean == null) {
            playerRef.sendMessage(Message.translation("cozyranch_taming.name.invalid"));
            return;
        }
        TamingCommands.lookedAtOwned(ref, playerRef, store).ifPresent(target -> {
            target.record().name = clean;
            Ranch.animals().markDirty(target.record());
            store.ensureAndGetComponent(target.entity(), Nameplate.getComponentType()).setText(clean);
            playerRef.sendMessage(Message.translation("cozyranch_taming.name.done").param("name", clean));
        });
    }
}
