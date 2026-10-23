package com.hexvane.cozytalesranching.behaviour;

import com.hexvane.cozytalesranching.behaviour.BehaviourRules.GroundSample;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Block reads for one planner pass, only from sections already in memory ({@link SectionReader} loads nothing).
 * World thread, outside system ticks. Ids are cached per pass.
 */
final class WorldBlocks {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final SectionReader reader;
    private final Map<Integer, String> baseIds = new HashMap<>();
    private final Map<Integer, Boolean> covers = new HashMap<>();
    private final Map<Integer, String> fluids = new HashMap<>();
    /** Cells read this pass, for the cost line in {@code /ranch behaviour status}. */
    int reads;

    WorldBlocks(@Nonnull World world) {
        this.reader = new SectionReader(world.getChunkStore());
    }

    boolean loaded(int x, int y, int z) {
        return reader.canHoldY(y) && reader.hasStorageInMemory(x, y, z);
    }

    /** Base block id (no state, no {@code *}) or null for empty, unknown or unloaded. */
    @Nullable
    String block(int x, int y, int z) {
        if (!loaded(x, y, z)) return null;
        reads++;
        int id = reader.getBlock(x, y, z);
        if (id == BlockType.EMPTY_ID) return null;
        String s = baseIds.computeIfAbsent(id, i -> {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            return type == null || type.isUnknown() ? "" : BehaviourConfig.baseBlockId(type.getId());
        });
        return s.isEmpty() ? null : s;
    }

    /** The cell holds a block in a toy's {@code Play} state (its own animation running). */
    boolean inPlayState(int x, int y, int z) {
        if (!loaded(x, y, z)) return false;
        BlockType type = BlockType.getAssetMap().getAsset(reader.getBlock(x, y, z));
        return type != null && ToyAnimator.isPlayState(type.getId());
    }

    /** The cell is a filler of a multi-cell block (not its origin). */
    boolean filler(int x, int y, int z) {
        return loaded(x, y, z) && reader.getFiller(x, y, z) != 0;
    }

    @Nullable
    String fluid(int x, int y, int z) {
        if (!loaded(x, y, z)) return null;
        reads++;
        int id = reader.getFluidId(x, y, z);
        if (id == Fluid.EMPTY_ID) return null;
        String s = fluids.computeIfAbsent(id, i -> {
            Fluid f = Fluid.getAssetMap().getAsset(i);
            return f == null ? "" : f.getId();
        });
        return s.isEmpty() ? null : s;
    }

    /** Solid blocks and tree leaves cast shade. */
    boolean covers(int x, int y, int z) {
        if (!loaded(x, y, z)) return false;
        reads++;
        int id = reader.getBlock(x, y, z);
        if (id == BlockType.EMPTY_ID) return false;
        return covers.computeIfAbsent(id, i -> {
            BlockType type = BlockType.getAssetMap().getAsset(i);
            if (type == null || type.isUnknown()) return false;
            return type.getMaterial() == BlockMaterial.Solid || BehaviourConfig.baseBlockId(type.getId()).startsWith("Plant_Leaves_");
        });
    }

    /** The cells under, at and beside the animal's feet: 2 + 2 + 4 x 3 reads. */
    @Nonnull
    GroundSample sample(double px, double py, double pz) {
        int x = (int) Math.floor(px), y = (int) Math.floor(py + 0.05), z = (int) Math.floor(pz);
        Set<String> nearBlocks = new LinkedHashSet<>();
        Set<String> nearFluids = new LinkedHashSet<>();
        for (int[] s : SIDES) {
            String b = block(x + s[0], y, z + s[1]);
            if (b != null) nearBlocks.add(b);
            String f = fluid(x + s[0], y, z + s[1]);
            if (f != null) nearFluids.add(f);
            f = fluid(x + s[0], y - 1, z + s[1]);
            if (f != null) nearFluids.add(f);
        }
        return new GroundSample(block(x, y - 1, z), block(x, y, z), fluid(x, y, z), fluid(x, y - 1, z), nearBlocks, nearFluids);
    }

    /** Anything that casts shade within {@code height} cells above the animal's head (from 2 cells up). */
    boolean shaded(double px, double py, double pz, int height) {
        int x = (int) Math.floor(px), y = (int) Math.floor(py + 0.05), z = (int) Math.floor(pz);
        for (int dy = 2; dy <= height + 1; dy++) {
            if (covers(x, y + dy, z)) return true;
        }
        return false;
    }
}
