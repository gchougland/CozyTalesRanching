package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
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
 * {@code "Type": "CozyRanchBaitBowl"} on the Bait Bowl's Use. With a bait in hand and the bowl empty: one item goes in.
 * Crouching with an empty hand: the bait comes back. Otherwise a line says what is in it. The interaction only reads;
 * the change is queued with {@link World#execute} and the held item is checked again there.
 */
public final class BaitBowlInteraction extends SimpleBlockInteraction {
    public static final String TYPE = "CozyRanchBaitBowl";
    public static final BuilderCodec<BaitBowlInteraction> CODEC = BuilderCodec
        .builder(BaitBowlInteraction.class, BaitBowlInteraction::new, SimpleBlockInteraction.CODEC)
        .build();

    @Override
    protected void interactWithBlock(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractionType type,
                                     @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull Vector3i targetBlock,
                                     @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> player = context.getEntity();
        PlayerRef playerRef = commandBuffer.getComponent(player, PlayerRef.getComponentType());
        if (playerRef == null) return;
        MovementStatesComponent moves = commandBuffer.getComponent(player, MovementStatesComponent.getComponentType());
        boolean crouching = moves != null && moves.getMovementStates().crouching;
        String itemId = ItemStack.isEmpty(itemInHand) ? null : itemInHand.getItemId();
        Vector3i pos = new Vector3i(targetBlock);
        world.execute(() -> use(world, playerRef, pos, itemId, crouching, false));
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull World world, @Nonnull Vector3i targetBlock) {}

    /**
     * World thread. {@code free} is the admin test path: the item is not taken from the hand.
     *
     * @return true when bait went in
     */
    static boolean use(@Nonnull World world, @Nonnull PlayerRef player, @Nonnull Vector3i pos, @Nullable String itemId, boolean crouching, boolean free) {
        Predators p = Predators.get();
        Ref<EntityStore> ref = player.getReference();
        if (p == null || ref == null || !ref.isValid()) return false;
        if (!RanchPermissions.has(player.getUuid(), RanchPermissions.TAME)) {
            player.sendMessage(Message.translation("cozyranch_predator.bowl.no_permission"));
            return false;
        }
        if (BaitBowls.presence(world, pos.x, pos.y, pos.z) != BaitBowls.Presence.BOWL) return false;
        Store<EntityStore> store = world.getEntityStore().getStore();
        String w = world.getName();
        String inside = p.bowls.bait(w, pos.x, pos.y, pos.z);

        if (itemId != null && !(crouching && inside != null)) {
            if (inside != null) {
                player.sendMessage(Message.translation("cozyranch_predator.bowl.full").param("item", BaitBowls.itemName(inside)));
                return false;
            }
            if (!BaitBowls.isAnyBait(itemId)) {
                player.sendMessage(Message.translation("cozyranch_predator.bowl.not_bait").param("item", BaitBowls.itemName(itemId)));
                return false;
            }
            if (!free) {
                ItemStack inHand = InventoryComponent.getItemInHand(store, ref);
                if (ItemStack.isEmpty(inHand) || !itemId.equals(inHand.getItemId())) return false;
            }
            if (!p.bowls.fill(w, pos.x, pos.y, pos.z, itemId)) return false;
            if (!free) InventoryHelper.removeItemInHand(ref, store, 1);
            BaitBowls.show(world, pos.x, pos.y, pos.z, itemId);
            RanchSounds.play(RanchSounds.TROUGH_FILL, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, store);
            List<SpeciesDefinition> who = BaitBowls.speciesFor(itemId);
            Message animal = who.isEmpty() ? Message.raw("?") : Message.translation(who.getFirst().NameKey);
            RanchChat.tell(player, Message.translation("cozyranch_predator.bowl.filled").param("item", BaitBowls.itemName(itemId)).param("animal", animal));
            return true;
        }

        if (crouching && inside != null) {
            String taken = p.bowls.take(w, pos.x, pos.y, pos.z);
            if (taken == null) return false;
            BaitBowls.show(world, pos.x, pos.y, pos.z, null);
            ItemUtils.interactivelyPickupItem(ref, new ItemStack(taken, 1), new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5), store);
            RanchChat.tell(player, Message.translation("cozyranch_predator.bowl.taken").param("item", BaitBowls.itemName(taken)));
            return false;
        }

        player.sendMessage(inside == null
            ? Message.translation("cozyranch_predator.bowl.empty")
            : Message.translation("cozyranch_predator.bowl.holds").param("item", BaitBowls.itemName(inside)));
        return false;
    }
}
