package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
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

/** The Bait Bowl block in the world. World thread, outside system ticks; reads loaded sections only. */
public final class BaitBowls {
    public static final String BLOCK_ID = "CozyRanch_Bait_Bowl";
    public static final String STATE_EMPTY = "default";

    enum Presence { BOWL, GONE, UNLOADED }

    private BaitBowls() {}

    public static boolean isBowl(@Nullable BlockType type) {
        if (type == null || type.isUnknown()) return false;
        return BLOCK_ID.equals(baseId(type.getId())) || BLOCK_ID.equals(type.getDefaultStateKey());
    }

    @Nonnull
    static String baseId(@Nonnull String id) {
        String s = id.startsWith("*") ? id.substring(1) : id;
        int state = s.indexOf("_State_Definitions_");
        return state >= 0 ? s.substring(0, state) : s;
    }

    /** The state the block shows now ("default" when empty). */
    @Nonnull
    static String stateOf(@Nonnull BlockType type) {
        String id = type.getId();
        int i = id.indexOf("_State_Definitions_");
        return i >= 0 ? id.substring(i + "_State_Definitions_".length()) : STATE_EMPTY;
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
        return isBowl(typeAt(world, x, y, z)) ? Presence.BOWL : Presence.GONE;
    }

    /** Shows the bait (or the empty bowl when {@code bait} is null). False when not loaded or not a bowl. */
    static boolean show(@Nonnull World world, int x, int y, int z, @Nullable String bait) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) return false;
        BlockType current = typeAt(world, x, y, z);
        if (!isBowl(current)) return false;
        String want = bait == null ? STATE_EMPTY : CalmRules.bowlLook(bait).state;
        if (stateOf(current).equals(want)) return true;
        BlockOperations.setBlockInteractionState(chunks, section, x, y, z, current, want, false);
        BlockType after = typeAt(world, x, y, z);
        return after != null && stateOf(after).equals(want);
    }

    /** Whether some predator species takes this item as bait. */
    static boolean isAnyBait(@Nullable String itemId) {
        if (itemId == null) return false;
        for (SpeciesDefinition s : SpeciesRegistry.all()) if (CalmRules.isBait(s, itemId)) return true;
        return false;
    }

    /** Predator species that take this bait, for the chat line after filling. */
    @Nonnull
    static List<SpeciesDefinition> speciesFor(@Nonnull String itemId) {
        List<SpeciesDefinition> out = new ArrayList<>();
        for (SpeciesDefinition s : SpeciesRegistry.all()) if (CalmRules.isBait(s, itemId)) out.add(s);
        return out;
    }

    static void drop(@Nonnull Store<EntityStore> store, int x, int y, int z, @Nonnull String itemId) {
        if (Item.getAssetMap().getAsset(itemId) == null) return;
        Holder<EntityStore>[] items = ItemComponent.generateItemDrops(store, List.of(new ItemStack(itemId, 1)), new Vector3d(x + 0.5, y + 0.5, z + 0.5), Rotation3f.IDENTITY);
        store.addEntities(items, AddReason.SPAWN);
    }

    @Nonnull
    static Message itemName(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId) : Message.translation(item.getTranslationKey());
    }
}
