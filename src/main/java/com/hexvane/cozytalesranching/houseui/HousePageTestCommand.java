package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.RanchPermissions;
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
import javax.annotation.Nonnull;

/**
 * {@code /ranch housepage [typical|full|empty|unchecked|missing|breedingoff]}: opens the house page on a made-up house
 * ({@link HouseDemo}) so the page can be seen and clicked without placing one. Admin only; touches no saved data.
 */
public final class HousePageTestCommand extends AbstractPlayerCommand {
    private final OptionalArg<String> scenario;

    public HousePageTestCommand() {
        super("housepage", "cozyranch_houseui.commands.housepage.desc");
        this.requirePermission(RanchPermissions.ADMIN);
        this.scenario = withOptionalArg("scenario", "cozyranch_houseui.commands.housepage.arg", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        HouseDemo.Scenario chosen = HouseDemo.scenario(scenario.get(context));
        if (chosen == null) {
            context.sendMessage(Message.translation("cozyranch_houseui.commands.housepage.unknown"));
            return;
        }
        if (!HousePage.open(ref, store, HouseDemo.create(chosen, playerRef.getUuid()))) {
            context.sendMessage(Message.translation("cozyranch_houseui.commands.housepage.failed"));
        }
    }
}
