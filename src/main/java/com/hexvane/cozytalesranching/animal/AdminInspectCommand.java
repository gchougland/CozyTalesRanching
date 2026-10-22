package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;

/** {@code /ranch admin inspect}: the record of the animal the admin is looking at, and whether that entity is the real one. */
final class AdminInspectCommand extends AbstractPlayerCommand {
    AdminInspectCommand() {
        super("inspect", "cozyranch_animal.commands.admin.inspect.desc");
        this.requirePermission(RanchPermissions.ADMIN_INSPECT);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Ref<EntityStore> target = TargetUtil.getTargetEntity(ref, store);
        if (target == null || !target.isValid()) {
            context.sendMessage(Message.translation("cozyranch_animal.admin.inspect.none"));
            return;
        }
        UUIDComponent uuid = store.getComponent(target, UUIDComponent.getComponentType());
        ComponentType<EntityStore, AnimalMarker> markerType = AnimalMarker.type();
        AnimalMarker marker = markerType == null ? null : store.getComponent(target, markerType);
        AnimalRecord r = marker != null && marker.animalId() != null
            ? Ranch.animals().get(marker.animalId()).orElse(null)
            : uuid == null ? null : Ranch.animals().byEntity(uuid.getUuid()).orElse(null);
        if (r == null) {
            context.sendMessage(Message.translation(marker != null ? "cozyranch_animal.admin.inspect.orphan" : "cozyranch_animal.admin.inspect.notRanch"));
            return;
        }
        context.sendMessage(AnimalAdminCommand.describe(r));
        boolean current = marker != null && uuid != null && Ranch.animals().isCurrent(r.animalId, uuid.getUuid(), marker.generation());
        context.sendMessage(Message.translation(current ? "cozyranch_animal.admin.inspect.current" : "cozyranch_animal.admin.inspect.stale")
            .param("generation", marker == null ? -1L : marker.generation())
            .param("recordGeneration", r.generation));
    }
}
