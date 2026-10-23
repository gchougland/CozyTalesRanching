package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.produce.PoopRules.Ground;
import com.hexvane.cozytalesranching.produce.PoopRules.Pile;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pile blocks in the world. Reads go through a {@link SectionReader}, which loads nothing: an unloaded cell reads as
 * {@code loaded == false} and is left alone. Writes are world thread only, outside system ticks.
 */
final class PoopBlocks {
    private final World world;
    private final SectionReader reader;
    /** Cells read through this object, for the cost line of {@code /ranch produce poop status}. */
    int reads;

    PoopBlocks(@Nonnull World world) {
        this.world = world;
        this.reader = new SectionReader(world.getChunkStore());
    }

    boolean loaded(int x, int y, int z) {
        return reader.canHoldY(y) && reader.hasStorageInMemory(x, y, z);
    }

    @Nullable
    BlockType type(int x, int y, int z) {
        if (!loaded(x, y, z)) return null;
        reads++;
        int id = reader.getBlock(x, y, z);
        if (id == BlockType.EMPTY_ID) return null;
        BlockType t = BlockType.getAssetMap().getAsset(id);
        return t == null || t.isUnknown() ? null : t;
    }

    /** Droppings in the pile at this cell, 0 when it is no pile or not loaded. */
    int count(int x, int y, int z) {
        BlockType t = type(x, y, z);
        return t == null ? 0 : PoopRules.countOf(t.getId());
    }

    @Nullable
    private String fluid(int x, int y, int z) {
        if (!loaded(x, y, z)) return null;
        int id = reader.getFluidId(x, y, z);
        if (id == Fluid.EMPTY_ID) return null;
        Fluid f = Fluid.getAssetMap().getAsset(id);
        return f == null ? "fluid" : f.getId();
    }

    @Nullable
    private static String baseId(@Nullable BlockType t) {
        if (t == null) return null;
        String id = t.getId();
        if (id.startsWith("*")) id = id.substring(1);
        int s = id.indexOf("_State_");
        return s > 0 ? id.substring(0, s) : id;
    }

    /** Null when the cell or the one under it is not loaded. */
    @Nullable
    Ground ground(int x, int y, int z) {
        if (!loaded(x, y, z) || !loaded(x, y - 1, z)) return null;
        BlockType at = type(x, y, z), below = type(x, y - 1, z);
        return new Ground(baseId(below), below != null && below.getMaterial() == BlockMaterial.Solid,
            reader.getFiller(x, y - 1, z) != 0, baseId(at), reader.getFiller(x, y, z) != 0,
            fluid(x, y, z), fluid(x, y - 1, z));
    }

    /** Every loaded pile in the box {@code x±r, y-dy..y+dy, z±r}. */
    @Nonnull
    List<Pile> scan(int x, int y, int z, int r, int dy) {
        List<Pile> out = new ArrayList<>();
        for (int px = x - r; px <= x + r; px++) {
            for (int pz = z - r; pz <= z + r; pz++) {
                for (int py = y - dy; py <= y + dy; py++) {
                    int n = count(px, py, pz);
                    if (n > 0) out.add(new Pile(px, py, pz, n));
                }
            }
        }
        return out;
    }

    /** Shows {@code count} droppings at this cell (a new pile when it was empty). False when not loaded or unknown. */
    boolean setCount(int x, int y, int z, int count) {
        String id = PoopRules.typeIdFor(count);
        int index = BlockType.getAssetMap().getIndex(id);
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type == null || index == Integer.MIN_VALUE || index < 0) return false;
        return write(x, y, z, index, type);
    }

    boolean clear(int x, int y, int z) {
        return write(x, y, z, BlockType.EMPTY_ID, BlockType.EMPTY);
    }

    private boolean write(int x, int y, int z, int index, @Nonnull BlockType type) {
        if (!loaded(x, y, z)) return false;
        Ref<ChunkStore> section = reader.sectionAt(x, y, z);
        if (section == null || !section.isValid()) return false;
        BlockOperations.setBlock(world.getChunkStore(), section, x, y, z, index, type, RotationTuple.NONE_INDEX, FillerBlockUtil.NO_FILLER, SetBlockSettings.NONE);
        return true;
    }
}
