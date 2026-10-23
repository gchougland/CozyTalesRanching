package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * {@code "Type": "CozyRanchTrough"} on the trough block's Use. With food in hand: the held stack goes in (as much
 * as fits). Crouching with an empty hand: the contents come back. Empty hand: a chat line says what is inside.
 * The interaction only reads; the change is queued with {@link World#execute} and the held item checked again.
 */
public final class TroughInteraction extends SimpleBlockInteraction {
    public static final String TYPE = "CozyRanchTrough";
    public static final BuilderCodec<TroughInteraction> CODEC = BuilderCodec
        .builder(TroughInteraction.class, TroughInteraction::new, SimpleBlockInteraction.CODEC)
        .build();

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i targetBlock,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> player = context.getEntity();
        PlayerRef playerRef = commandBuffer.getComponent(player, PlayerRef.getComponentType());
        if (playerRef == null) return;
        MovementStatesComponent moves = commandBuffer.getComponent(player, MovementStatesComponent.getComponentType());
        boolean crouching = moves != null && moves.getMovementStates().crouching;
        String itemId = ItemStack.isEmpty(itemInHand) ? null : itemInHand.getItemId();
        Vector3i pos = new Vector3i(targetBlock);
        world.execute(() -> use(world, playerRef, pos, itemId, crouching));
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull World world, @Nonnull Vector3i targetBlock) {}

    /** World thread, outside ticks. */
    static void use(@Nonnull World world, @Nonnull PlayerRef player, @Nonnull Vector3i target, @Nullable String itemId, boolean crouching) {
        CareService care = CareService.get();
        Ref<EntityStore> ref = player.getReference();
        if (care == null || ref == null || !ref.isValid()) return;
        if (!RanchPermissions.has(player.getUuid(), RanchPermissions.TROUGH)) {
            player.sendMessage(Message.translation("cozyranch_care.trough.no_permission"));
            return;
        }
        Vector3i pos = TroughBlocks.base(world, target);
        if (TroughBlocks.presence(world, pos.x, pos.y, pos.z) != TroughBlocks.Presence.TROUGH) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        CareStore troughs = care.store();
        String w = world.getName();

        if (itemId != null) {
            if (!care.isTroughFood(itemId)) {
                player.sendMessage(Message.translation("cozyranch_care.trough.not_food").param("item", itemName(itemId)));
                return;
            }
            ItemStack inHand = InventoryComponent.getItemInHand(store, ref);
            if (ItemStack.isEmpty(inHand) || !itemId.equals(inHand.getItemId())) return;
            int put = troughs.add(w, pos.x, pos.y, pos.z, itemId, inHand.getQuantity(), TroughBlocks::maxStack);
            if (put <= 0) {
                player.sendMessage(Message.translation("cozyranch_care.trough.full").param("slots", TroughContents.MAX_SLOTS));
                return;
            }
            InventoryHelper.removeItemInHand(ref, store, put);
            CareWorld.syncTrough(world, care, pos.x, pos.y, pos.z);
            care.scheduleSave();
            RanchSounds.play(RanchSounds.TROUGH_FILL, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, store);
            RanchChat.tell(player, Message.translation("cozyranch_care.trough.added").param("count", put).param("item", itemName(itemId)));
            return;
        }

        if (crouching) {
            List<TroughContents.Slot> taken = troughs.takeAll(w, pos.x, pos.y, pos.z);
            if (taken.isEmpty()) {
                player.sendMessage(Message.translation("cozyranch_care.trough.empty"));
                return;
            }
            Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.6, pos.z + 0.5);
            for (TroughContents.Slot s : taken) {
                for (ItemStack stack : TroughBlocks.stacks(s)) ItemUtils.interactivelyPickupItem(ref, stack, origin, store);
            }
            CareWorld.syncTrough(world, care, pos.x, pos.y, pos.z);
            care.scheduleSave();
            RanchChat.tell(player, Message.translation("cozyranch_care.trough.taken"));
            return;
        }

        describe(player, troughs.trough(w, pos.x, pos.y, pos.z).map(CareStore.TroughView::contents).orElse(null));
    }

    static void describe(@Nonnull PlayerRef player, @Nullable TroughContents contents) {
        if (contents == null || contents.isEmpty()) {
            player.sendMessage(Message.translation("cozyranch_care.trough.empty"));
            return;
        }
        player.sendMessage(Message.translation("cozyranch_care.trough.header").param("used", contents.slots.size()).param("slots", TroughContents.MAX_SLOTS));
        for (TroughContents.Slot s : contents.slots) {
            player.sendMessage(Message.translation("cozyranch_care.trough.line").param("count", s.count).param("item", itemName(s.item)));
        }
    }

    @Nonnull
    static Message itemName(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId) : Message.translation(item.getTranslationKey());
    }
}
