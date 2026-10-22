package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code /ranch admin resummon <animalId>}: makes a fresh incarnation of the animal beside the admin,
 * removing the old one if it is loaded (an unloaded one is removed as stale when its chunk loads).
 */
final class AdminResummonCommand extends AbstractPlayerCommand {
    private final RequiredArg<UUID> animalArg;

    AdminResummonCommand() {
        super("resummon", "cozyranch_animal.commands.admin.resummon.desc");
        this.requirePermission(RanchPermissions.ADMIN_MANAGE_ANY);
        this.animalArg = this.withRequiredArg("animalId", "cozyranch_animal.commands.admin.resummon.arg.animal", ArgTypes.UUID);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID animalId = context.get(animalArg);
        AnimalRecord r = Ranch.animals().get(animalId).orElse(null);
        AnimalRecovery recovery = AnimalFeature.recovery();
        if (r == null || recovery == null) {
            context.sendMessage(Message.translation("cozyranch_animal.admin.resummon.unknown").param("id", animalId.toString()));
            return;
        }
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t == null) return;
        if (recovery.resummon(world, r, new Vector3d(t.getPosition()))) {
            context.sendMessage(Message.translation("cozyranch_animal.admin.resummon.done").param("id", animalId.toString()));
        } else {
            context.sendMessage(Message.translation("cozyranch_animal.admin.resummon.failed").param("id", animalId.toString()));
        }
    }
}
