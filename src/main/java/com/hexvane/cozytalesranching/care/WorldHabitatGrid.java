package com.hexvane.cozytalesranching.care;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Reads only sections already in memory ({@link SectionReader} loads nothing). World thread, outside ticks. */
final class WorldHabitatGrid implements HabitatGrid {
    private final SectionReader reader;
    private final Map<Integer, String> blockIds = new HashMap<>();
    private final Map<Integer, Boolean> solids = new HashMap<>();
    private final Map<Integer, String> fluidIds = new HashMap<>();

    WorldHabitatGrid(@Nonnull World world) {
        this.reader = new SectionReader(world.getChunkStore());
    }

    @Override
    public boolean loaded(int x, int y, int z) {
        return reader.canHoldY(y) && reader.hasStorageInMemory(x, y, z);
    }

    @Nullable
    @Override
    public String block(int x, int y, int z) {
        int id = reader.getBlock(x, y, z);
        if (id == BlockType.EMPTY_ID) return null;
        return blockIds.computeIfAbsent(id, i -> {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            return type == null || type.isUnknown() ? "" : HabitatConfig.baseBlockId(type.getId());
        }).transform(s -> s.isEmpty() ? null : s);
    }

    @Override
    public boolean solid(int x, int y, int z) {
        int id = reader.getBlock(x, y, z);
        if (id == BlockType.EMPTY_ID) return false;
        return solids.computeIfAbsent(id, i -> {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            return type != null && !type.isUnknown() && type.getMaterial() == BlockMaterial.Solid;
        });
    }

    @Nullable
    @Override
    public String fluid(int x, int y, int z) {
        int id = reader.getFluidId(x, y, z);
        if (id == Fluid.EMPTY_ID) return null;
        String name = fluidIds.computeIfAbsent(id, i -> {
            Fluid f = Fluid.getAssetMap().getAsset(i);
            return f == null ? "" : f.getId();
        });
        return name.isEmpty() ? null : name;
    }
}
