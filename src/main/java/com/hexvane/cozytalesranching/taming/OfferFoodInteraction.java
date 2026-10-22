package com.hexvane.cozytalesranching.taming;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SimpleInteraction;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;

/**
 * {@code "Type": "CozyRanchOfferFood"}: offers the held item to the animal the player looks at. Needed for
 * animals whose vanilla role is never interactable (frogs flee and show no Use prompt).
 */
public final class OfferFoodInteraction extends SimpleInstantInteraction {
    public static final String TYPE = "CozyRanchOfferFood";
    public static final BuilderCodec<OfferFoodInteraction> CODEC = BuilderCodec
        .builder(OfferFoodInteraction.class, OfferFoodInteraction::new, SimpleInstantInteraction.CODEC)
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
        if (buffer == null || player == null || !player.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> target = TargetUtil.getTargetEntity(player, REACH, buffer);
        if (target == null || !target.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AnimalInteractions.use(player, target, context.getHeldItem(), buffer);
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {}

    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new SimpleInteraction();
    }
}
