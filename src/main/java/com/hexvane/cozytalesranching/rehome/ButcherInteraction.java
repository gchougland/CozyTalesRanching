package com.hexvane.cozytalesranching.rehome;

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
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * {@code "Type": "CozyRanchButcher"}, on the Butcher's Block's Use: processes the nearest adult animal following the
 * player ({@link ButcherService}). The interaction only notes the block; the work runs in a world task after the tick.
 */
public final class ButcherInteraction extends SimpleBlockInteraction {
    public static final String TYPE = "CozyRanchButcher";
    public static final BuilderCodec<ButcherInteraction> CODEC = BuilderCodec
        .builder(ButcherInteraction.class, ButcherInteraction::new, SimpleBlockInteraction.CODEC)
        .build();

    @Override
    protected void interactWithBlock(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractionType type,
                                     @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull Vector3i targetBlock,
                                     @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> playerEntity = context.getEntity();
        PlayerRef player = playerEntity != null ? commandBuffer.getComponent(playerEntity, PlayerRef.getComponentType()) : null;
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Vector3d block = new Vector3d(targetBlock.x + 0.5, targetBlock.y + 0.5, targetBlock.z + 0.5);
        world.execute(() -> {
            if (playerEntity.isValid()) ButcherService.process(world, player, playerEntity, block);
        });
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand,
                                             @Nonnull World world, @Nonnull Vector3i targetBlock) {}
}
