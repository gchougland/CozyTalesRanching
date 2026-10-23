package com.hexvane.cozytalesranching.behaviour;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Plays a toy block's own animation while an animal is playing with it. Each toy item has a block state {@code Play}
 * whose {@code CustomModelAnimation} loops; the vanilla call {@link BlockOperations#setBlockInteractionState} swaps
 * the placed block to that state (the client starts the clip) and back to {@code default} (rest pose), as chests,
 * benches and our trough do. Once per planner pass the toys that should play (acting toy sessions) are compared with
 * the ones playing, and only the difference is sent. Toys left in {@code Play} by a restart are found by the toy scan
 * and put back. World thread, outside system ticks.
 */
final class ToyAnimator {
    static final String STATE_PLAY = "Play";
    static final String STATE_DEFAULT = "default";
    private static final String PLAY_SUFFIX = "_State_Definitions_" + STATE_PLAY;

    record Cell(int x, int y, int z) {}

    /** What one pass changes. */
    record Plan(@Nonnull Set<Cell> start, @Nonnull Set<Cell> stop) {}

    private final Map<String, Set<Cell>> playing = new ConcurrentHashMap<>();
    private final Map<String, Set<Cell>> stale = new ConcurrentHashMap<>();
    /** Counters for {@code /ranch behaviour status}. */
    volatile int startedCount;
    volatile int resetCount;

    /** Start what is wanted and not playing; stop what is playing (or left playing) and no longer wanted. */
    @Nonnull
    static Plan plan(@Nonnull Set<Cell> playing, @Nonnull Set<Cell> wanted, @Nonnull Set<Cell> stale) {
        Set<Cell> start = new HashSet<>(wanted);
        start.removeAll(playing);
        Set<Cell> stop = new HashSet<>(playing);
        stop.addAll(stale);
        stop.removeAll(wanted);
        return new Plan(start, stop);
    }

    static boolean isPlayState(@Nullable String blockTypeId) {
        return blockTypeId != null && blockTypeId.endsWith(PLAY_SUFFIX);
    }

    int playingIn(@Nonnull String worldName) {
        Set<Cell> s = playing.get(worldName);
        return s == null ? 0 : s.size();
    }

    /** A toy the scan found in its Play state; it is put back on the next pass unless an animal is playing with it. */
    void noteStale(@Nonnull String worldName, @Nonnull Cell cell) {
        stale.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).add(cell);
    }

    /** Brings the world's toys in line with {@code wanted}. {@code isToy} takes a base block id. */
    void reconcile(@Nonnull World world, @Nonnull Set<Cell> wanted, @Nonnull Predicate<String> isToy) {
        String name = world.getName();
        Set<Cell> now = playing.computeIfAbsent(name, k -> ConcurrentHashMap.newKeySet());
        Set<Cell> left = stale.remove(name);
        if (wanted.isEmpty() && now.isEmpty() && (left == null || left.isEmpty())) return;
        Plan p = plan(now, wanted, left == null ? Set.of() : left);
        for (Cell c : p.stop()) {
            now.remove(c);
            if (show(world, c, false, isToy)) {
                resetCount++;
                BehaviourPlanner.debug("Ranch toy at " + c.x() + " " + c.y() + " " + c.z() + " back to rest");
            }
        }
        for (Cell c : p.start()) {
            if (show(world, c, true, isToy)) {
                now.add(c);
                startedCount++;
                BehaviourPlanner.debug("Ranch toy at " + c.x() + " " + c.y() + " " + c.z() + " plays");
            }
        }
    }

    /** Puts every toy this world has playing back to rest (reload, shutdown). */
    void stopAll(@Nonnull World world, @Nonnull Predicate<String> isToy) {
        reconcile(world, Set.of(), isToy);
    }

    @Nonnull
    List<Cell> playingList(@Nonnull String worldName) {
        Set<Cell> s = playing.get(worldName);
        return s == null ? List.of() : List.copyOf(s);
    }

    /**
     * Sets the toy at the cell to Play or back to default. True when the block now shows that state; false when its
     * section is not loaded, it is no longer a toy, or its block has no Play state (a test toy).
     */
    private static boolean show(@Nonnull World world, @Nonnull Cell c, boolean play, @Nonnull Predicate<String> isToy) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(c.x(), c.y(), c.z());
        if (section == null || !section.isValid()) return false;
        BlockType current = typeAt(chunks, section, c);
        if (current == null || current.isUnknown() || !isToy.test(BehaviourConfig.baseBlockId(current.getId()))) return false;
        if (isPlayState(current.getId()) == play) return true;
        BlockOperations.setBlockInteractionState(chunks, section, c.x(), c.y(), c.z(), current, play ? STATE_PLAY : STATE_DEFAULT, false);
        BlockType after = typeAt(chunks, section, c);
        return after != null && isPlayState(after.getId()) == play;
    }

    @Nullable
    private static BlockType typeAt(@Nonnull ChunkStore chunks, @Nonnull Ref<ChunkStore> section, @Nonnull Cell c) {
        BlockSection blocks = chunks.getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null) return null;
        return BlockType.getAssetMap().getAsset(blocks.get(c.x(), c.y(), c.z()));
    }
}
