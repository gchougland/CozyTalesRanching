package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code /ranch admin crate status|take|open}: for server tests, since a test player cannot swing at an animal. {@code status}
 * says whether the crate item runs our interaction; {@code take} runs the capture our interaction runs on the nearest ranch
 * animal, and {@code open} the release.
 */
final class AdminCrateCommand extends AbstractCommandCollection {
    AdminCrateCommand() {
        super("crate", "cozyranch_animal.commands.admin.crate.desc");
        this.requireNoPermission();
        this.addSubCommand(new Status());
        this.addSubCommand(new Take());
        this.addSubCommand(new Open());
    }

    private static final class Status extends AbstractPlayerCommand {
        Status() {
            super("status", "cozyranch_animal.commands.admin.crate.status.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            int ours = 0;
            int vanilla = 0;
            for (Interaction i : Interaction.getAssetMap().getAssetMap().values()) {
                if (i instanceof CaptureCrateInteraction) ours++;
                else if (i instanceof com.hypixel.hytale.builtin.adventure.farming.interactions.UseCaptureCrateInteraction) vanilla++;
            }
            context.sendMessage(Message.translation("cozyranch_animal.admin.crate.status").param("ours", ours).param("vanilla", vanilla));
        }
    }

    private static final class Take extends AbstractPlayerCommand {
        Take() {
            super("take", "cozyranch_animal.commands.admin.crate.take.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            var markerType = AnimalMarker.type();
            if (t == null || hotbar == null || markerType == null) return;
            Ref<EntityStore> target = null;
            AnimalRecord record = null;
            double best = Double.MAX_VALUE;
            for (Ref<EntityStore> e : List.copyOf(TargetUtil.getAllEntitiesInSphere(new Vector3d(t.getPosition()), 16.0, store))) {
                AnimalMarker marker = store.getComponent(e, markerType);
                UUIDComponent uuid = store.getComponent(e, UUIDComponent.getComponentType());
                TransformComponent et = store.getComponent(e, TransformComponent.getComponentType());
                if (marker == null || uuid == null || et == null) continue;
                AnimalRecord r = CrateService.recordOf(marker, uuid.getUuid());
                double d = et.getPosition().distanceSquared(t.getPosition());
                if (r != null && d < best) {
                    best = d;
                    target = e;
                    record = r;
                }
            }
            if (record == null) {
                context.sendMessage(Message.translation("cozyranch_animal.admin.crate.none"));
                return;
            }
            if (!CrateService.mayCapture(playerRef, record)) {
                context.sendMessage(Message.translation("cozyranch_animal.crate.not_yours"));
                return;
            }
            short slot = hotbar.getActiveSlot();
            ItemStack crate = new ItemStack("Tool_Capture_Crate", 1);
            hotbar.getInventory().setItemStackForSlot(slot, crate);
            CrateService.capture(world, store, playerRef, record, target, hotbar.getInventory(), slot, crate, "Icons/ItemsGenerated/Tool_Capture_Crate_Full.png");
            store.removeEntity(target, RemoveReason.REMOVE);
            context.sendMessage(Message.translation("cozyranch_animal.admin.crate.taken")
                .param("id", record.animalId.toString()).param("tooltip", CrateService.tooltip(record, playerRef.getLanguage())));
        }
    }

    private static final class Open extends AbstractPlayerCommand {
        Open() {
            super("open", "cozyranch_animal.commands.admin.crate.open.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            if (t == null || hotbar == null) return;
            CrateService.release(world, store, ref, playerRef, hotbar.getActiveSlot(), new Vector3d(t.getPosition()));
            context.sendMessage(Message.translation("cozyranch_animal.admin.crate.opened"));
        }
    }
}
