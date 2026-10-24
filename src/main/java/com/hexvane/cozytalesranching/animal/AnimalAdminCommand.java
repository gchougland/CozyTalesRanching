package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.api.AnimalView;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** {@code /ranch admin ...}: inspect, list and resummon ranch animals. */
public final class AnimalAdminCommand extends AbstractCommandCollection {
    public AnimalAdminCommand() {
        super("admin", "cozyranch_animal.commands.admin.desc");
        // each sub-command requires its own admin node; a node on the collection would also be demanded of every sub-command
        this.requireNoPermission();
        this.addSubCommand(new AdminInspectCommand());
        this.addSubCommand(new AdminListCommand());
        this.addSubCommand(new AdminResummonCommand());
        this.addSubCommand(new AdminRelinkCommand());
        this.addSubCommand(new AdminBefriendCommand());
        this.addSubCommand(new AdminHurtCommand());
        this.addSubCommand(new com.hexvane.cozytalesranching.predator.PredatorAdminCommands.Bait());
        this.addSubCommand(new com.hexvane.cozytalesranching.predator.PredatorAdminCommands.Feed());
        this.addSubCommand(new com.hexvane.cozytalesranching.predator.PredatorAdminCommands.Calm());
    }

    /** One line describing a record, shared by inspect and list. */
    @Nonnull
    static Message describe(@Nonnull AnimalRecord r) {
        AnimalView v = r.view();
        return Message.translation("cozyranch_animal.admin.line")
            .param("id", v.animalId().toString())
            .param("species", v.speciesId())
            .param("name", v.name() == null ? "-" : v.name())
            .param("owner", playerName(v.ownerId()))
            .param("hearts", v.hearts())
            .param("state", r.state.name())
            .param("world", v.worldName())
            .param("x", (long) Math.floor(r.lastX))
            .param("y", (long) Math.floor(r.lastY))
            .param("z", (long) Math.floor(r.lastZ));
    }

    @Nonnull
    static String playerName(@Nullable UUID player) {
        if (player == null) return "-";
        Universe universe = Universe.get();
        PlayerRef online = universe == null ? null : universe.getPlayer(player);
        return online != null ? online.getUsername() : player.toString();
    }
}
