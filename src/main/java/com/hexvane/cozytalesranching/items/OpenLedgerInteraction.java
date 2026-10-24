package com.hexvane.cozytalesranching.items;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * {@code "Type": "CozyRanchOpenLedger"}, on the placed Rancher's Ledger's Use: opens {@link LedgerPage}. The page is
 * built in a world task after the tick. No systems, no ticks, no saved data.
 */
public final class OpenLedgerInteraction extends SimpleBlockInteraction {
    public static final String TYPE = "CozyRanchOpenLedger";
    public static final BuilderCodec<OpenLedgerInteraction> CODEC = BuilderCodec
        .builder(OpenLedgerInteraction.class, OpenLedgerInteraction::new, SimpleBlockInteraction.CODEC)
        .documentation("Opens the Rancher's Ledger page: the player's houses, animals and products waiting.")
        .build();

    @Override
    protected void interactWithBlock(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractionType type,
                                     @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull Vector3i targetBlock,
                                     @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> player = context.getEntity();
        PlayerRef playerRef = player != null ? commandBuffer.getComponent(player, PlayerRef.getComponentType()) : null;
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        world.execute(() -> {
            if (player.isValid()) LedgerPage.open(player, world.getEntityStore().getStore());
        });
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand,
                                             @Nonnull World world, @Nonnull Vector3i targetBlock) {}
}
