package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import javax.annotation.Nonnull;

/**
 * {@code /ranch admin deathrules [--unprotected] [--minutes=N]}: for a server test, turns {@code ProtectOwnedAnimals} on or
 * off and sets {@code DeathRespawnCooldownMinutes} until the next restart, without touching the config file.
 */
final class AdminDeathRulesCommand extends CommandBase {
    private final FlagArg unprotectedArg;
    private final OptionalArg<Integer> minutesArg;

    AdminDeathRulesCommand() {
        super("deathrules", "cozyranch_animal.commands.admin.deathrules.desc");
        this.requirePermission(RanchPermissions.ADMIN);
        this.unprotectedArg = withFlagArg("unprotected", "cozyranch_animal.commands.admin.deathrules.arg.unprotected");
        this.minutesArg = withOptionalArg("minutes", "cozyranch_animal.commands.admin.deathrules.arg.minutes", ArgTypes.INTEGER);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        RanchConfig config = RanchConfig.get();
        boolean protect = !context.provided(unprotectedArg);
        int minutes = context.provided(minutesArg) ? context.get(minutesArg) : config.deathRespawnCooldownMinutes();
        config.setForTest(protect, minutes);
        context.sendMessage(Message.translation("cozyranch_animal.admin.deathrules.done")
            .param("protect", String.valueOf(config.protectOwnedAnimals())).param("minutes", config.deathRespawnCooldownMinutes()));
    }
}
