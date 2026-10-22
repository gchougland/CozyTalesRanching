package com.hexvane.cozytalesranching.taming.command;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hexvane.cozytalesranching.taming.TamingRules;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

/** {@code /ranch animals}: lists the player's animals with species, hearts, world and whether they follow. */
public final class AnimalsCommand extends AbstractPlayerCommand {
    public AnimalsCommand() {
        super("animals", "cozyranch_taming.commands.animals.desc");
        this.setPermissionGroups("hytale:Adventurer");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        List<AnimalRecord> mine = Ranch.animals().ownedBy(playerRef.getUuid());
        if (mine.isEmpty()) {
            playerRef.sendMessage(Message.translation("cozyranch_taming.animals.none"));
            return;
        }
        playerRef.sendMessage(Message.translation("cozyranch_taming.animals.header").param("count", mine.size()).param("max", RanchConfig.get().maxAnimalsPerPlayer()));
        for (AnimalRecord r : mine) {
            Message species = SpeciesRegistry.byId(r.speciesId).map(s -> Message.translation(s.NameKey)).orElse(Message.raw(r.speciesId));
            playerRef.sendMessage(Message.translation(r.following ? "cozyranch_taming.animals.line_following" : "cozyranch_taming.animals.line")
                .param("animal", TamingApi.displayName(r))
                .param("species", species)
                .param("hearts", r.hearts)
                .param("max", TamingRules.MAX_HEARTS)
                .param("world", r.worldName));
        }
    }
}
