package com.hexvane.cozytalesranching.breeding;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/** {@code /ranch breed ...}: admin test tools for breeding and growing up. Every leaf requires {@code cozyranch.admin}. */
public final class BreedAdminCommand extends AbstractCommandCollection {
    public BreedAdminCommand() {
        super("breed", "cozyranch_breeding.commands.breed.desc");
        // each leaf requires the admin node itself
        this.requireNoPermission();
        this.addSubCommand(new BreedTestHouseCommand());
        this.addSubCommand(new BreedMorningCommand());
        this.addSubCommand(new BreedGrowUpCommand());
    }
}
