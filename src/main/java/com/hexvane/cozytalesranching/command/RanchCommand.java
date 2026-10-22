package com.hexvane.cozytalesranching.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public final class RanchCommand extends AbstractCommandCollection {
    public RanchCommand() {
        super("ranch", "cozyranch_commands.commands.cozyranch.root.desc");
        this.setPermissionGroups("hytale:Adventurer");
        this.addAliases("cozyranch");
        this.addSubCommand(new PingCommand());
        this.addSubCommand(new ReloadCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.animal.AnimalAdminCommand());
    }
}
