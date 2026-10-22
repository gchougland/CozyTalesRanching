package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** House blocks in the world: which species a block houses, its footprint and its door. Reads assets only. */
final class HouseBlocks {
    private static final HouseGeometry.Bounds UNIT = new HouseGeometry.Bounds(0, 0, 0, 1, 1, 1);

    private HouseBlocks() {}

    /** The species whose {@code HouseBlock} is this block id. Species files are data, so this is not a fixed list. */
    @Nonnull
    static Optional<SpeciesDefinition> speciesForBlock(@Nullable String blockId) {
        if (blockId == null || blockId.isEmpty()) return Optional.empty();
        for (SpeciesDefinition s : SpeciesRegistry.all()) {
            if (blockId.equals(s.HouseBlock)) return Optional.of(s);
        }
        return Optional.empty();
    }

    /** The block's hitbox bounds turned for {@code yaw} (0 to 3), as the engine turns them. */
    @Nonnull
    static HouseGeometry.Bounds bounds(@Nonnull String blockId, int yaw) {
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        if (type == null) return UNIT;
        BlockBoundingBoxes boxes = BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        if (boxes == null) return UNIT;
        Box b = boxes.get(Math.floorMod(yaw, 4)).getBoundingBox();
        return new HouseGeometry.Bounds(b.min.x, b.min.y, b.min.z, b.max.x, b.max.y, b.max.z);
    }

    static void applyDoor(@Nonnull HouseRecord h) {
        HouseGeometry.Door door = HouseGeometry.door(bounds(h.blockId, h.rotation), h.rotation, h.x, h.y, h.z);
        h.doorX = door.x();
        h.doorY = door.y();
        h.doorZ = door.z();
    }

    /** The house whose footprint holds this cell: its origin first, then any house of that block in the world. */
    @Nonnull
    static Optional<HouseRecord> covering(@Nonnull String worldName, @Nonnull String blockId, int x, int y, int z) {
        HouseRegistry houses = Ranch.houses();
        Optional<HouseRecord> exact = houses.at(worldName, x, y, z);
        if (exact.isPresent()) return exact;
        for (HouseRecord h : houses.inWorld(worldName)) {
            if (!blockId.equals(h.blockId)) continue;
            if (HouseGeometry.covers(bounds(h.blockId, h.rotation), h.x, h.y, h.z, x, y, z)) return Optional.of(h);
        }
        return Optional.empty();
    }

    @Nonnull
    static Vector3d door(@Nonnull HouseRecord h) {
        return new Vector3d(h.doorX, h.doorY, h.doorZ);
    }

    static boolean isLoaded(@Nonnull World world, double x, double z) {
        return world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z)) != null;
    }

    /** The house's own name, else the house item's name. */
    @Nonnull
    static Message displayName(@Nonnull HouseRecord h) {
        if (h.name != null && !h.name.isEmpty()) return Message.raw(h.name);
        Item item = Item.getAssetMap().getAsset(h.blockId);
        return item != null ? Message.translation(item.getTranslationKey()) : Message.raw(h.blockId);
    }
}
