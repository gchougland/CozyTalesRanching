package com.hexvane.cozytalesranching.taming;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Reads only sections already in memory; an unloaded cell is neither floor nor clear, so nothing is loaded. */
final class WorldBlockGrid implements TeleportSpotFinder.BlockGrid {
    private final SectionReader reader;

    WorldBlockGrid(@Nonnull World world) {
        this.reader = new SectionReader(world.getChunkStore());
    }

    @Override
    public boolean isSafeFloor(int x, int y, int z) {
        if (!reader.hasStorageInMemory(x, y, z)) return false;
        BlockType type = type(reader.getBlock(x, y, z));
        return type != null && type.getMaterial() == BlockMaterial.Solid && type.getDamageToEntities() <= 0
            && reader.getFluidId(x, y, z) == Fluid.EMPTY_ID;
    }

    @Override
    public boolean isClear(int x, int y, int z) {
        if (!reader.canHoldY(y) || !reader.hasStorageInMemory(x, y, z)) return false;
        if (reader.getFluidId(x, y, z) != Fluid.EMPTY_ID) return false;
        int id = reader.getBlock(x, y, z);
        if (id == BlockType.EMPTY_ID) return true;
        BlockType type = type(id);
        return type != null && type.getMaterial() == BlockMaterial.Empty && type.getDamageToEntities() <= 0;
    }

    @Nullable
    private static BlockType type(int id) {
        BlockType type = BlockType.getAssetMap().getAsset(id);
        return type == null || type.isUnknown() ? null : type;
    }
}
