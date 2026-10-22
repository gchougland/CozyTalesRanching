package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/** {@code /ranch admin list <player>}: every animal a player owns (an online name or any UUID). */
final class AdminListCommand extends CommandBase {
    private final RequiredArg<UUID> playerArg;

    AdminListCommand() {
        super("list", "cozyranch_animal.commands.admin.list.desc");
        this.requirePermission(RanchPermissions.ADMIN_INSPECT);
        this.playerArg = this.withRequiredArg("player", "cozyranch_animal.commands.admin.list.arg.player", ArgTypes.PLAYER_UUID);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        UUID player = context.get(playerArg);
        List<AnimalRecord> animals = Ranch.animals().ownedBy(player);
        String name = AnimalAdminCommand.playerName(player);
        if (animals.isEmpty()) {
            context.sendMessage(Message.translation("cozyranch_animal.admin.list.empty").param("player", name));
            return;
        }
        context.sendMessage(Message.translation("cozyranch_animal.admin.list.header").param("player", name).param("count", animals.size()));
        for (AnimalRecord r : animals) context.sendMessage(AnimalAdminCommand.describe(r));
    }
}
