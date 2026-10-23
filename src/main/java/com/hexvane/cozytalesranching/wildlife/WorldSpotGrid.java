package com.hexvane.cozytalesranching.wildlife;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/** Spot rules over the world: sections already in memory only ({@link SectionReader} loads nothing). World thread. */
final class WorldSpotGrid implements SpotRules.Grid {
    private final SectionReader reader;
    private final Map<Integer, Boolean> solids = new HashMap<>();
    private final Map<Integer, Boolean> leaves = new HashMap<>();
    private final Map<Integer, Boolean> waters = new HashMap<>();

    WorldSpotGrid(@Nonnull World world) {
        this.reader = new SectionReader(world.getChunkStore());
    }

    @Override
    public boolean loaded(int x, int y, int z) {
        return reader.canHoldY(y) && reader.hasStorageInMemory(x, y, z);
    }

    @Override
    public boolean solid(int x, int y, int z) {
        if (!loaded(x, y, z)) return false;
        int id = reader.getBlock(x, y, z);
        if (id == BlockType.EMPTY_ID) return false;
        return solids.computeIfAbsent(id, i -> {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            return type != null && !type.isUnknown() && type.getMaterial() == BlockMaterial.Solid;
        });
    }

    @Override
    public boolean leaves(int x, int y, int z) {
        if (!loaded(x, y, z)) return false;
        int id = reader.getBlock(x, y, z);
        if (id == BlockType.EMPTY_ID) return false;
        return leaves.computeIfAbsent(id, i -> {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            return type != null && type.getId() != null && type.getId().startsWith("Plant_Leaves");
        });
    }

    @Override
    public boolean fluid(int x, int y, int z) {
        return loaded(x, y, z) && reader.getFluidId(x, y, z) != Fluid.EMPTY_ID;
    }

    @Override
    public boolean water(int x, int y, int z) {
        if (!loaded(x, y, z)) return false;
        int id = reader.getFluidId(x, y, z);
        if (id == Fluid.EMPTY_ID) return false;
        return waters.computeIfAbsent(id, i -> {
            Fluid f = Fluid.getAssetMap().getAsset(i);
            return f != null && f.getId() != null && f.getId().contains("Water");
        });
    }
}
