package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hexvane.cozytalesranching.taming.TamingRules;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SimpleInteraction;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * {@code "Type": "CozyRanchGiveFeed"} on the advanced feed items: gives one feed to the owned ranch animal the player
 * looks at. The feed's bonus starts (replacing any other), it counts as a preferred meal, and it gives the friendship
 * of a preferred hand meal when the animal has not been fed in the last few seconds. Reads only here; the change is
 * queued with {@link World#execute} and the held item checked again.
 */
public final class GiveFeedInteraction extends SimpleInstantInteraction {
    public static final String TYPE = "CozyRanchGiveFeed";
    public static final BuilderCodec<GiveFeedInteraction> CODEC = BuilderCodec
        .builder(GiveFeedInteraction.class, GiveFeedInteraction::new, SimpleInstantInteraction.CODEC)
        .build();
    private static final float REACH = 6f;

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> player = context.getEntity();
        ItemStack held = context.getHeldItem();
        if (buffer == null || player == null || !player.isValid() || ItemStack.isEmpty(held)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> target = TargetUtil.getTargetEntity(player, REACH, buffer);
        NPCEntity npc = target == null || !target.isValid() ? null : buffer.getComponent(target, NPCEntity.getComponentType());
        String role = npc == null ? null : npc.getRoleName();
        if (role == null || SpeciesRegistry.byRole(role).isEmpty() || !CompatGuard.isManageable(target, buffer, role)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRef = buffer.getComponent(player, PlayerRef.getComponentType());
        UUIDComponent uuid = buffer.getComponent(target, UUIDComponent.getComponentType());
        if (playerRef == null || uuid == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = buffer.getExternalData().getWorld();
        UUID animal = uuid.getUuid();
        String itemId = held.getItemId();
        world.execute(() -> give(world, playerRef, animal, itemId));
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {}

    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new SimpleInteraction();
    }

    /** World thread, outside ticks. */
    static void give(@Nonnull World world, @Nonnull PlayerRef player, @Nonnull UUID animalUuid, @Nonnull String itemId) {
        CareService care = CareService.get();
        Ref<EntityStore> playerEntity = player.getReference();
        if (care == null || playerEntity == null || !playerEntity.isValid()) return;
        HabitatConfig config = care.habitat();
        if (config.feedBonus(itemId) == null) return;
        AnimalRecord record = Ranch.animals().byEntity(animalUuid).orElse(null);
        if (record == null || record.ownerId == null) {
            player.sendMessage(Message.translation("cozyranch_care.feed.not_owned"));
            return;
        }
        if (!record.ownerId.equals(player.getUuid()) && !RanchPermissions.has(player.getUuid(), RanchPermissions.ADMIN_MANAGE_ANY)) {
            player.sendMessage(Message.translation("cozyranch_care.feed.not_yours").param("animal", TamingApi.displayName(record)));
            return;
        }
        if (!RanchPermissions.has(player.getUuid(), RanchPermissions.TROUGH)) {
            player.sendMessage(Message.translation("cozyranch_care.feed.no_permission"));
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        ItemStack inHand = InventoryComponent.getItemInHand(store, playerEntity);
        if (ItemStack.isEmpty(inHand) || !itemId.equals(inHand.getItemId())) return;
        InventoryHelper.removeItemInHand(playerEntity, store, 1);

        long now = System.currentTimeMillis();
        long fedBefore = record.lastFedAtMs;
        Feeding.apply(record, itemId, config, now);
        record.lastFedAtMs = now;
        care.store().recordMeal(record.animalId, record.worldName, FoodKind.PREFERRED, now);
        care.scheduleSave();
        if (TamingRules.canFeedForFriendship(fedBefore, now)) {
            CareWorld.addFriendship(record, TamingRules.feedFriendship(FoodKind.PREFERRED) * Feeding.friendshipMultiplier(record, config, now));
        }
        Ranch.animals().markDirty(record);
        Ref<EntityStore> animal = world.getEntityStore().getRefFromUUID(animalUuid);
        if (animal != null && animal.isValid()) TamingApi.cheer(animal, store);
        player.sendMessage(Message.translation("cozyranch_care.feed.given")
            .param("animal", TamingApi.displayName(record))
            .param("feed", TroughInteraction.itemName(itemId))
            .param("minutes", Math.round(config.feedDurationMs(itemId) / 60_000.0)));
    }
}
