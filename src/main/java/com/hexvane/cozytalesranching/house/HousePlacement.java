package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.AnimalHouseAssignedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.api.event.RanchHousePlacedEvent;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Placing and breaking house blocks. The engine's place and break events are cancellable and fire before
 * the block changes, so the handlers only decide (permission, ownership) and the record work runs in a
 * world task afterwards, once the world shows the block placed or gone.
 */
final class HousePlacement {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private HousePlacement() {}

    /** Player places a block: a house block needs {@code cozyranch.house.place}. */
    static final class PlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        PlaceSystem() {
            super(PlaceBlockEvent.class);
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
            if (event.isCancelled()) return;
            ItemStack item = event.getItemInHand();
            String blockId = item != null ? item.getBlockKey() : null;
            Optional<SpeciesDefinition> species = HouseBlocks.speciesForBlock(blockId);
            if (species.isEmpty()) return;
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player == null) return;
            if (!RanchPermissions.has(player.getUuid(), RanchPermissions.HOUSE_PLACE)) {
                event.setCancelled(true);
                player.sendMessage(Message.translation("cozyranch_house.place.no_permission"));
                return;
            }
            World world = store.getExternalData().getWorld();
            Vector3i pos = new Vector3i(event.getTargetBlock());
            UUID playerId = player.getUuid();
            String speciesId = species.get().Id;
            world.execute(() -> confirmPlaced(world, playerId, speciesId, blockId, pos.x, pos.y, pos.z));
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }

    /** Player breaks a block: a house may only be broken by its owner or {@code cozyranch.admin.manageany}. */
    static final class BreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        BreakSystem() {
            super(BreakBlockEvent.class);
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
            if (event.isCancelled()) return;
            String blockId = event.getBlockType().getId();
            if (HouseBlocks.speciesForBlock(blockId).isEmpty()) return;
            World world = store.getExternalData().getWorld();
            Vector3i pos = event.getTargetBlock();
            Optional<HouseRecord> found = HouseBlocks.covering(world.getName(), blockId, pos.x, pos.y, pos.z);
            if (found.isEmpty()) return;
            HouseRecord house = found.get();
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && !player.getUuid().equals(house.ownerId) && !RanchPermissions.has(player.getUuid(), RanchPermissions.ADMIN_MANAGE_ANY)) {
                event.setCancelled(true);
                player.sendMessage(Message.translation("cozyranch_house.break.not_owner").param("owner", ownerName(house.ownerId)));
                return;
            }
            UUID breaker = player != null ? player.getUuid() : null;
            world.execute(() -> {
                if (Ranch.houses().get(house.houseId).isEmpty()) return;
                if (blockStillThere(world, house)) return;
                houseBroken(world, world.getEntityStore().getStore(), house, breaker);
            });
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }

    /** The block is in the world now: make the record. World thread, outside ticks. */
    static void confirmPlaced(@Nonnull World world, @Nonnull UUID playerId, @Nonnull String speciesId, @Nonnull String blockId, int x, int y, int z) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        int expected = BlockType.getAssetMap().getIndex(blockId);
        if (!reader.hasStorageInMemory(x, y, z) || reader.getBlock(x, y, z) != expected) return;
        if (Ranch.houses().at(world.getName(), x, y, z).isPresent()) return;
        int yaw = RotationTuple.get(reader.getRotationIndex(x, y, z)).yaw().ordinal();
        HouseRecord house = Ranch.houses().create(playerId, speciesId, blockId, world.getName(), x, y, z, yaw);
        house.capacity = HouseBlocks.speciesForBlock(blockId).map(s -> Math.max(1, s.HouseCapacity)).orElse(house.capacity);
        HouseBlocks.applyDoor(house);
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        if (time != null) {
            house.lastMorningDay = HouseRules.cycleDay(time.getGameDateTime().toLocalDate().toEpochDay(), time.getDayProgress());
        }
        Ranch.houses().markDirty(house);
        LOG.info("House " + house.houseId + " (" + blockId + ") placed by " + playerId + " in " + world.getName()
            + " at " + x + " " + y + " " + z + " rotation " + yaw + ", door " + fmt(house.doorX) + " " + fmt(house.doorY) + " " + fmt(house.doorZ));
        RanchEvents.fire(new RanchHousePlacedEvent(playerId, house.houseId, speciesId, world.getName()));
        PlayerRef player = Universe.get().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Message.translation("cozyranch_house.place.done")
                .param("house", HouseBlocks.displayName(house)).param("capacity", house.capacity));
        }
    }

    static boolean blockStillThere(@Nonnull World world, @Nonnull HouseRecord house) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        if (!reader.hasStorageInMemory(house.x, house.y, house.z)) return true;
        return reader.getBlock(house.x, house.y, house.z) == BlockType.getAssetMap().getIndex(house.blockId);
    }

    /**
     * The house is gone: residents inside come out at the door first, every resident loses its house
     * ({@link AnimalHouseAssignedEvent}), then the record goes. No animal record is ever removed.
     */
    static void houseBroken(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house, @Nullable UUID breaker) {
        List<AnimalRecord> residents = Ranch.animals().inHouse(house.houseId);
        for (AnimalRecord r : residents) {
            if (r.state == AnimalRecord.State.STORED && !HouseResidents.bringOut(world, store, house, r)) {
                // no room at the door: recovery brings it back beside its owner
                r.state = AnimalRecord.State.MISSING;
            }
            Ref<EntityStore> ref = HouseResidents.live(world, r);
            if (ref != null) HouseResidents.releaseHome(ref, store);
            HouseDaySystem.forget(r.animalId);
            r.houseId = null;
            Ranch.animals().markDirty(r);
            RanchEvents.fire(new AnimalHouseAssignedEvent(r.view(), house.houseId, null));
        }
        Ranch.houses().remove(house.houseId);
        LOG.info("House " + house.houseId + " removed (block gone" + (breaker != null ? ", broken by " + breaker : "") + "); " + residents.size() + " resident(s) unassigned");
        PlayerRef owner = house.ownerId != null ? Universe.get().getPlayer(house.ownerId) : null;
        if (owner != null) {
            owner.sendMessage(Message.translation("cozyranch_house.break.done").param("house", HouseBlocks.displayName(house)).param("count", residents.size()));
        }
    }

    @Nonnull
    static String ownerName(@Nullable UUID owner) {
        if (owner == null) return "-";
        PlayerRef online = Universe.get().getPlayer(owner);
        return online != null ? online.getUsername() : owner.toString();
    }

    @Nonnull
    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
