package com.hexvane.cozytalesranching.items;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * {@code "Type": "CozyRanchMossGrowCheck"} on the Moss Tuft, first in its chain: succeeds only when the targeted
 * block is a planted crop that still has a stage to grow ({@link GrowthRules}). The item's chain then runs vanilla's
 * {@code ChangeFarmingStage} (+1) and takes one tuft, so a tuft is never spent on dirt, a fully grown crop or a wild
 * one. Reads only; no permission, as vanilla's fertilizer. Runs inside the interaction tick and writes nothing.
 */
public final class MossGrowthInteraction extends SimpleBlockInteraction {
    public static final String TYPE = "CozyRanchMossGrowCheck";
    public static final BuilderCodec<MossGrowthInteraction> CODEC = BuilderCodec
        .builder(MossGrowthInteraction.class, MossGrowthInteraction::new, SimpleBlockInteraction.CODEC)
        .documentation("Succeeds when the target block is a growing crop with a stage left to grow.")
        .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

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
        GrowthRules.Verdict verdict = judge(world, targetBlock.x, targetBlock.y, targetBlock.z);
        if (verdict == GrowthRules.Verdict.GROW) return;
        context.getState().state = InteractionState.Failed;
        if (verdict == GrowthRules.Verdict.FULLY_GROWN) {
            Ref<EntityStore> player = context.getEntity();
            PlayerRef playerRef = player == null ? null : commandBuffer.getComponent(player, PlayerRef.getComponentType());
            if (playerRef != null) playerRef.sendMessage(Message.translation("cozyranch_items.moss_tuft.grown"));
        }
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull World world, @Nonnull Vector3i targetBlock) {}

    @Nonnull
    static GrowthRules.Verdict judge(@Nonnull World world, int x, int y, int z) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) return GrowthRules.Verdict.NOT_A_CROP;
        Store<ChunkStore> store = chunks.getStore();
        BlockSection blocks = store.getComponent(section, BlockSection.getComponentType());
        if (blocks == null) return GrowthRules.Verdict.NOT_A_CROP;
        BlockType block = BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
        FarmingData farming = block == null ? null : block.getFarming();
        if (farming == null || farming.getStages() == null) return GrowthRules.Verdict.NOT_A_CROP;
        Ref<ChunkStore> entity = BlockModule.getBlockEntity(store, section, x, y, z);
        FarmingBlock growth = entity == null || !entity.isValid() ? null : store.getComponent(entity, FarmingBlock.getComponentType());
        String set = growth != null && growth.getCurrentStageSet() != null ? growth.getCurrentStageSet() : farming.getStartingStageSet();
        FarmingStageData[] stages = set == null ? null : farming.getStages().get(set);
        int count = stages == null ? 0 : stages.length;
        return GrowthRules.judge(count, growth != null, growth == null ? 0f : growth.getGrowthProgress());
    }
}
