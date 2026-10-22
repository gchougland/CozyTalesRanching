package com.hexvane.cozytalesranching.command;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import javax.annotation.Nonnull;

public final class ReloadCommand extends CommandBase {
    public ReloadCommand() {
        super("reload", "cozyranch_commands.commands.cozyranch.reload.desc");
        this.requirePermission(RanchPermissions.ADMIN_RELOAD);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        CozyTalesRanchingPlugin plugin = CozyTalesRanchingPlugin.get();
        if (plugin != null) plugin.reload();
        context.sendMessage(Message.translation("cozyranch_commands.commands.cozyranch.reload.done"));
    }
}
