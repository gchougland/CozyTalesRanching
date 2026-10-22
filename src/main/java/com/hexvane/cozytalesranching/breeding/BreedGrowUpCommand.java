package com.hexvane.cozytalesranching.breeding;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/** {@code /ranch breed growup [--animal=<id>]}: grows one baby, or every baby the admin owns in this world, right now. */
final class BreedGrowUpCommand extends AbstractPlayerCommand {
    private final OptionalArg<UUID> animalArg;

    BreedGrowUpCommand() {
        super("growup", "cozyranch_breeding.commands.breed.growup.desc");
        this.requirePermission(RanchPermissions.ADMIN);
        this.animalArg = withOptionalArg("animal", "cozyranch_breeding.commands.breed.growup.arg.animal", ArgTypes.UUID);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        List<AnimalRecord> targets = context.provided(animalArg)
            ? Ranch.animals().get(context.get(animalArg)).stream().toList()
            : Ranch.animals().ownedBy(playerRef.getUuid()).stream().filter(r -> r.baby && world.getName().equals(r.worldName)).toList();
        if (targets.isEmpty()) {
            context.sendMessage(Message.translation("cozyranch_breeding.debug.growup.none"));
            return;
        }
        for (AnimalRecord r : targets) {
            BreedingService.GrowResult result = BreedingFeature.service().growUp(world, r, store);
            context.sendMessage(Message.translation(key(result)).param("id", r.animalId.toString()).param("role", String.valueOf(r.spawnRole)));
        }
    }

    @Nonnull
    private static String key(@Nonnull BreedingService.GrowResult result) {
        return switch (result) {
            case GREW -> "cozyranch_breeding.debug.growup.grew";
            case NOT_BABY -> "cozyranch_breeding.debug.growup.notBaby";
            case NO_SPECIES -> "cozyranch_breeding.debug.growup.noSpecies";
            case NOT_LOADED -> "cozyranch_breeding.debug.growup.notLoaded";
            case SWAP_PENDING -> "cozyranch_breeding.debug.growup.swapPending";
            case NOT_RANCH_ROLE -> "cozyranch_breeding.debug.growup.notRanchRole";
        };
    }
}
