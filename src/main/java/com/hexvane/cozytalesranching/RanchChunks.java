package com.hexvane.cozytalesranching;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;

/** What the world's chunks are doing at a point. Never loads a chunk. */
public final class RanchChunks {
    private RanchChunks() {}

    /**
     * True if the section holding this point is loaded and ticking. A loaded chunk is not enough: with no player near, a
     * section stays loaded but pauses. Its entities are taken out of the world and parked in it as holders, and an entity
     * added to it is parked at once, even a never-saved one. They come back when the section ticks again.
     */
    public static boolean isTicking(@Nonnull World world, double x, double y, double z) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReference(
            ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(y), ChunkUtil.chunkCoordinate(z));
        return section != null && section.isValid()
            && !chunks.getStore().getArchetype(section).contains(ChunkStore.REGISTRY.getNonTickingComponentType());
    }
}
