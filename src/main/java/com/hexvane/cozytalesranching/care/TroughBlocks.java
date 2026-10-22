package com.hexvane.cozytalesranching.care;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** The trough block in the world. World thread, outside system ticks. Reads only loaded sections. */
final class TroughBlocks {
    static final String TROUGH_ID = "CozyRanch_Trough";
    static final String STATE_FILLED = "Filled";
    static final String STATE_DEFAULT = "default";

    enum Presence { TROUGH, GONE, UNLOADED }

    private TroughBlocks() {}

    static boolean isTrough(@Nullable BlockType type) {
        if (type == null || type.isUnknown()) return false;
        return TROUGH_ID.equals(HabitatConfig.baseBlockId(type.getId())) || TROUGH_ID.equals(type.getDefaultStateKey());
    }

    static boolean isFilledState(@Nonnull BlockType type) {
        return type.getId().endsWith("_State_Definitions_" + STATE_FILLED);
    }

    /** The origin block of a multi-block piece, when its section is loaded. */
    @Nonnull
    static Vector3i base(@Nonnull World world, @Nonnull Vector3i pos) {
        BlockPosition b = BlockOperations.resolveBaseBlockPosition(world.getChunkStore(), new BlockPosition(pos.x, pos.y, pos.z));
        return new Vector3i(b.x, b.y, b.z);
    }

    @Nullable
    static BlockType typeAt(@Nonnull World world, int x, int y, int z) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) return null;
        BlockSection blocks = chunks.getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null) return null;
        return BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
    }

    @Nonnull
    static Presence presence(@Nonnull World world, int x, int y, int z) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) return Presence.UNLOADED;
        return isTrough(typeAt(world, x, y, z)) ? Presence.TROUGH : Presence.GONE;
    }

    /**
     * Shows the Filled model while the trough has food and the plain one when empty. Returns true when the
     * block now shows the right state, false when it is not loaded or not a trough.
     */
    static boolean showState(@Nonnull World world, int x, int y, int z, boolean filled) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) return false;
        BlockType current = typeAt(world, x, y, z);
        if (!isTrough(current)) return false;
        if (isFilledState(current) == filled) return true;
        BlockOperations.setBlockInteractionState(chunks, section, x, y, z, current, filled ? STATE_FILLED : STATE_DEFAULT, false);
        BlockType after = typeAt(world, x, y, z);
        return after != null && isFilledState(after) == filled;
    }

    /** Drops stacks as item entities just above the block. */
    static void drop(@Nonnull Store<EntityStore> store, int x, int y, int z, @Nonnull List<TroughContents.Slot> slots) {
        List<ItemStack> stacks = new ArrayList<>();
        for (TroughContents.Slot s : slots) stacks.addAll(stacks(s));
        if (stacks.isEmpty()) return;
        Holder<EntityStore>[] items = ItemComponent.generateItemDrops(store, stacks, new Vector3d(x + 0.5, y + 0.6, z + 0.5), Rotation3f.IDENTITY);
        store.addEntities(items, AddReason.SPAWN);
    }

    /** One slot as item stacks no bigger than the item's own stack size. Unknown items are dropped from the list. */
    @Nonnull
    static List<ItemStack> stacks(@Nonnull TroughContents.Slot slot) {
        List<ItemStack> out = new ArrayList<>();
        Item item = Item.getAssetMap().getAsset(slot.item);
        if (item == null || slot.count <= 0) return out;
        int cap = Math.max(1, item.getMaxStack());
        for (int left = slot.count; left > 0; left -= cap) out.add(new ItemStack(slot.item, Math.min(cap, left)));
        return out;
    }

    static int maxStack(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? 1 : Math.max(1, item.getMaxStack());
    }
}
