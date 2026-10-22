package com.hexvane.cozytalesranching.breeding;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
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

/**
 * {@code /ranch breed morning [--house=<id>]}: runs the morning breeding check now on one house (default: every house
 * in this world) with its current residents, exactly as the house's morning would, and says what happened.
 */
final class BreedMorningCommand extends AbstractPlayerCommand {
    private final OptionalArg<UUID> houseArg;

    BreedMorningCommand() {
        super("morning", "cozyranch_breeding.commands.breed.morning.desc");
        this.requirePermission(RanchPermissions.ADMIN);
        this.houseArg = withOptionalArg("house", "cozyranch_breeding.commands.breed.morning.arg.house", ArgTypes.UUID);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        List<HouseRecord> houses = context.provided(houseArg)
            ? Ranch.houses().get(context.get(houseArg)).stream().toList()
            : Ranch.houses().inWorld(world.getName());
        if (houses.isEmpty()) {
            context.sendMessage(Message.translation("cozyranch_breeding.debug.noHouse"));
            return;
        }
        for (HouseRecord house : houses) run(context, world, house);
    }

    private static void run(@Nonnull CommandContext context, @Nonnull World world, @Nonnull HouseRecord house) {
        List<AnimalRecord> residents = Ranch.animals().inHouse(house.houseId);
        BreedingRules.Plan plan = BreedingFeature.service().runMorning(world, house, residents);
        context.sendMessage(Message.translation(key(plan.outcome()))
            .param("house", house.houseId.toString())
            .param("residents", residents.size())
            .param("capacity", house.capacity)
            .param("eligible", plan.eligible())
            .param("babies", plan.babies()));
        if (plan.habitatUnscanned()) context.sendMessage(Message.translation("cozyranch_breeding.debug.morning.habitatUnscanned"));
    }

    @Nonnull
    private static String key(@Nonnull BreedingRules.Outcome outcome) {
        return switch (outcome) {
            case BRED -> "cozyranch_breeding.debug.morning.bred";
            case SERVER_DISABLED -> "cozyranch_breeding.debug.morning.serverDisabled";
            case HOUSE_DISABLED -> "cozyranch_breeding.debug.morning.houseDisabled";
            case NO_OWNER -> "cozyranch_breeding.debug.morning.noOwner";
            case NO_PERMISSION -> "cozyranch_breeding.debug.morning.noPermission";
            case UNKNOWN_SPECIES -> "cozyranch_breeding.debug.morning.unknownSpecies";
            case HABITAT -> "cozyranch_breeding.debug.morning.habitat";
            case FULL -> "cozyranch_breeding.debug.morning.full";
            case NO_PAIR -> "cozyranch_breeding.debug.morning.noPair";
        };
    }
}
