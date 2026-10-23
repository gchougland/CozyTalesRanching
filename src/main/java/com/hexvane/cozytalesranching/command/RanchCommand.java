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
        this.addSubCommand(new com.hexvane.cozytalesranching.taming.command.NameCommand());
        this.addSubCommand(com.hexvane.cozytalesranching.taming.command.FollowCommand.follow());
        this.addSubCommand(com.hexvane.cozytalesranching.taming.command.FollowCommand.stay());
        this.addSubCommand(new com.hexvane.cozytalesranching.taming.command.ReleaseCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.taming.command.AnimalsCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.houseui.HousePageTestCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.breeding.BreedAdminCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.care.CareCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.house.HouseCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.produce.ProduceCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.journal.JournalCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.behaviour.BehaviourCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.rehome.GiveCommand());
        this.addSubCommand(com.hexvane.cozytalesranching.rehome.AcceptCommand.accept());
        this.addSubCommand(com.hexvane.cozytalesranching.rehome.AcceptCommand.decline());
        this.addSubCommand(new com.hexvane.cozytalesranching.rehome.MoveCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.rehome.ButcherCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.rehome.RehomeDebugCommand());
    }
}
