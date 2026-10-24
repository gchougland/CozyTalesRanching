package com.hexvane.cozytalesranching.items;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * {@code "Type": "CozyRanchGuanoSpread"} on Bat Guano, first in its chain: fertilises every tilled soil block in a
 * square patch round the aimed block ({@code Radius}, default 1, so 3 x 3), skipping soil already fertilised. Each
 * block gets exactly what vanilla's {@code FertilizeSoil} gives one block (the fertilized state, which the vanilla
 * {@code Fertilizer} growth modifier doubles growth for, and which lasts as long as the soil). Fails, so the item's
 * chain takes nothing, when no block took it. No permission, as vanilla's fertilizer. Runs inside the interaction
 * tick on the world thread and touches at most 18 blocks; no systems, no saved data.
 */
public final class GuanoSpreadInteraction extends SimpleBlockInteraction {
    public static final String TYPE = "CozyRanchGuanoSpread";
    public static final BuilderCodec<GuanoSpreadInteraction> CODEC = BuilderCodec
        .builder(GuanoSpreadInteraction.class, GuanoSpreadInteraction::new, SimpleBlockInteraction.CODEC)
        .documentation("Fertilises the tilled soil in a square patch round the aimed block; fails when none took it.")
        .appendInherited(
            new KeyedCodec<>("Radius", Codec.INTEGER),
            (i, v) -> i.radius = v,
            i -> i.radius,
            (o, p) -> o.radius = p.radius
        )
        .documentation("Half-width of the patch in blocks (0 to 2, default 1 for 3 x 3).")
        .add()
        .build();

    private int radius = GuanoRules.DEFAULT_RADIUS;

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
        int fertilised = 0;
        for (int[] d : GuanoRules.offsets(radius)) {
            int x = targetBlock.x + d[0];
            int z = targetBlock.z + d[1];
            for (int y : GuanoRules.heights(targetBlock.y)) {
                Soil soil = fertilise(world, x, y, z);
                if (soil == Soil.FERTILISED) fertilised++;
                if (soil != Soil.NONE) break;
            }
        }
        if (GuanoRules.consumes(fertilised)) return;
        context.getState().state = InteractionState.Failed;
        Ref<EntityStore> player = context.getEntity();
        PlayerRef playerRef = player == null ? null : commandBuffer.getComponent(player, PlayerRef.getComponentType());
        if (playerRef != null) playerRef.sendMessage(Message.translation("cozyranch_items.bat_guano.nothing"));
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull World world, @Nonnull Vector3i targetBlock) {}

    private enum Soil { NONE, ALREADY, FERTILISED }

    /** One block: tilled soil not yet fertilised becomes fertilised, as vanilla's FertilizeSoil does it. */
    @Nonnull
    private static Soil fertilise(@Nonnull World world, int x, int y, int z) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) return Soil.NONE;
        Store<ChunkStore> store = chunks.getStore();
        BlockSection blocks = store.getComponent(section, BlockSection.getComponentType());
        BlockComponentSection components = store.getComponent(section, BlockComponentSection.getComponentType());
        if (blocks == null || components == null) return Soil.NONE;
        int index = ChunkUtil.indexBlock(x, y, z);
        BlockType block = BlockType.getAssetMap().getAsset(blocks.get(index));
        if (block == null || !BlockEntity.declaresComponent(block, TilledSoilBlock.getComponentType())) return Soil.NONE;
        Ref<ChunkStore> ref = components.getBlockReference(index);
        if (ref == null || !ref.isValid()) {
            ref = BlockEntity.ensureBlockEntity(store, section, components, x, y, z, block, blocks.getFiller(index));
        }
        TilledSoilBlock soil = ref == null || !ref.isValid() ? null : store.getComponent(ref, TilledSoilBlock.getComponentType());
        if (soil == null) return Soil.NONE;
        if (soil.isFertilized()) return Soil.ALREADY;
        soil.setFertilized(true);
        blocks.setTicking(x, y, z, true);
        if (ChunkUtil.chunkCoordinate(y) == ChunkUtil.chunkCoordinate(y + 1)) {
            blocks.setTicking(x, y + 1, z, true);
        } else {
            Ref<ChunkStore> above = chunks.getChunkSectionReferenceAtBlock(x, y + 1, z);
            BlockSection aboveBlocks = above == null || !above.isValid() ? null : store.getComponent(above, BlockSection.getComponentType());
            if (aboveBlocks != null) aboveBlocks.setTicking(x, y + 1, z, true);
        }
        return Soil.FERTILISED;
    }
}
