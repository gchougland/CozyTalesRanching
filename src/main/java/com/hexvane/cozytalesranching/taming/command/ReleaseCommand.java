package com.hexvane.cozytalesranching.taming.command;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** {@code /ranch release [--confirm]}: lets the looked-at animal go wild again. Named animals need {@code --confirm}. */
public final class ReleaseCommand extends AbstractPlayerCommand {
    private final FlagArg confirm;

    public ReleaseCommand() {
        super("release", "cozyranch_taming.commands.release.desc");
        this.setPermissionGroups("hytale:Adventurer");
        this.requirePermission(RanchPermissions.RELEASE);
        this.confirm = withFlagArg("confirm", "cozyranch_taming.commands.release.confirm");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        TamingCommands.lookedAtOwned(ref, playerRef, store).ifPresent(target -> {
            AnimalRecord record = target.record();
            Message name = TamingApi.displayName(record);
            if (record.name != null && !record.name.isEmpty() && !Boolean.TRUE.equals(confirm.get(context))) {
                playerRef.sendMessage(Message.translation("cozyranch_taming.release.confirm").param("animal", name));
                return;
            }
            TamingApi.release(record, target.entity(), playerRef, store);
            playerRef.sendMessage(Message.translation("cozyranch_taming.release.done").param("animal", name));
        });
    }
}
