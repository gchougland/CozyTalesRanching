package com.hexvane.cozytalesranching.house;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * {@code "Type": "CozyRanchUseHouse"}, on the house blocks' Use: assigns nearby followers or opens the house
 * page ({@link HouseUse}). The interaction only finds the house; the work runs in a world task after the tick.
 */
public final class UseHouseInteraction extends SimpleBlockInteraction {
    public static final String TYPE = "CozyRanchUseHouse";
    public static final BuilderCodec<UseHouseInteraction> CODEC = BuilderCodec
        .builder(UseHouseInteraction.class, UseHouseInteraction::new, SimpleBlockInteraction.CODEC)
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
        int x = targetBlock.x, y = targetBlock.y, z = targetBlock.z;
        world.execute(() -> {
            if (!playerEntity.isValid()) return;
            Optional<HouseRecord> house = findHouse(world, x, y, z);
            if (house.isEmpty()) {
                player.sendMessage(Message.translation("cozyranch_house.use.unregistered"));
                return;
            }
            HouseUse.use(world, player, playerEntity, house.get());
        });
    }

    @Nonnull
    static Optional<HouseRecord> findHouse(@Nonnull World world, int x, int y, int z) {
        for (HouseRecord h : com.hexvane.cozytalesranching.animal.Ranch.houses().inWorld(world.getName())) {
            if (h.x == x && h.y == y && h.z == z) return Optional.of(h);
            if (HouseGeometry.covers(HouseBlocks.bounds(h.blockId, h.rotation), h.x, h.y, h.z, x, y, z)) return Optional.of(h);
        }
        return Optional.empty();
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand,
                                             @Nonnull World world, @Nonnull Vector3i targetBlock) {}
}
