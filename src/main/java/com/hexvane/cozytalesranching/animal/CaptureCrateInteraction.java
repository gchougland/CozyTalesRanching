package com.hexvane.cozytalesranching.animal;

import com.hypixel.hytale.builtin.adventure.farming.interactions.UseCaptureCrateInteraction;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.metadata.CapturedNPCMetadata;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * {@code "Type": "CozyRanchCaptureCrate"}, on our copy of the vanilla {@code Tool_Capture_Crate} item: the vanilla crate for
 * everything but ranch animals. Left to vanilla, a crate only takes small animals and keeps only their role, so the animal's
 * record would come back by recovery while the crate let out a stranger of the same kind. Any animal with a record goes in here.
 */
final class CaptureCrateInteraction extends UseCaptureCrateInteraction {
    static final String TYPE = "CozyRanchCaptureCrate";
    static final BuilderCodec<CaptureCrateInteraction> CODEC =
        BuilderCodec.builder(CaptureCrateInteraction.class, CaptureCrateInteraction::new, UseCaptureCrateInteraction.CODEC).build();

    @Override
    protected void tick0(boolean firstRun, float time, @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> cb = context.getCommandBuffer();
        AnimalRecord record = cb == null ? null : animalInSights(context, cb);
        if (record == null) {
            super.tick0(firstRun, time, type, context, cooldownHandler);
            return;
        }
        Ref<EntityStore> ref = context.getEntity();
        Ref<EntityStore> target = context.getTargetEntity();
        PlayerRef player = cb.getComponent(ref, PlayerRef.getComponentType());
        InventoryComponent.Hotbar hotbar = cb.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        ItemStack crate = hotbar == null ? null : hotbar.getActiveItem();
        if (player == null || crate == null || target == null || cb.getComponent(target, DeathComponent.getComponentType()) != null
            || !InteractionValidation.canPlayerInteractWithEntity(ref, cb, context.getHeldItem(), target)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!CrateService.mayCapture(player, record)) {
            if (firstRun) player.sendMessage(Message.translation("cozyranch_animal.crate.not_yours"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        CrateService.capture(cb.getExternalData().getWorld(), cb, player, record, target, hotbar.getInventory(), hotbar.getActiveSlot(), crate, fullIcon);
        cb.removeEntity(target, RemoveReason.REMOVE);
    }

    /** The record of the ranch animal an empty crate is pointed at, or null. */
    @Nullable
    private static AnimalRecord animalInSights(@Nonnull InteractionContext context, @Nonnull CommandBuffer<EntityStore> cb) {
        ItemStack held = context.getHeldItem();
        Ref<EntityStore> target = context.getTargetEntity();
        ComponentType<EntityStore, AnimalMarker> markerType = AnimalMarker.type();
        if (held == null || target == null || !target.isValid() || markerType == null) return null;
        if (held.getFromMetadataOrNull(CapturedNPCMetadata.KEY, CapturedNPCMetadata.CODEC) != null) return null;
        AnimalMarker marker = cb.getComponent(target, markerType);
        UUIDComponent uuid = cb.getComponent(target, UUIDComponent.getComponentType());
        return marker == null || uuid == null ? null : CrateService.recordOf(marker, uuid.getUuid());
    }

    @Override
    protected void interactWithBlock(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractionType type, @Nonnull InteractionContext context,
                                     @Nullable ItemStack itemInHand, @Nonnull Vector3i targetBlock, @Nonnull CooldownHandler cooldownHandler) {
        ItemStack held = context.getHeldItem();
        if (held == null || held.getFromMetadataOrNull(CrateMetadata.KEY, CrateMetadata.CODEC) == null) {
            super.interactWithBlock(world, commandBuffer, type, context, itemInHand, targetBlock, cooldownHandler);
            return;
        }
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        InventoryComponent.Hotbar hotbar = commandBuffer.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (player == null || hotbar == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        // on the face that was hit, as vanilla does
        Vector3d near = new Vector3d(targetBlock.x + 0.5, targetBlock.y, targetBlock.z + 0.5);
        if (context.getClientState() != null) {
            BlockFace face = BlockFace.fromProtocolFace(context.getClientState().blockFace);
            if (face != null) near.add(face.getDirection().x(), face.getDirection().y(), face.getDirection().z());
        }
        short slot = hotbar.getActiveSlot();
        world.execute(() -> CrateService.release(world, world.getEntityStore().getStore(), ref, player, slot, near));
    }
}
