package com.hexvane.cozytalesranching.produce;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** The poop pass timer, and the check on a player placing a pile. Neither writes the store in a tick. */
final class PoopSystem extends TickingSystem<EntityStore> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final int PRUNE_PASSES = 40;
    private final PoopService service;
    private final Map<String, Float> elapsed = new ConcurrentHashMap<>();
    private int passes;

    PoopSystem(@Nonnull PoopService service) {
        this.service = service;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PoopTuning t = PoopTuning.get();
        if (!t.Enabled) return;
        World world = store.getExternalData().getWorld();
        float total = elapsed.merge(world.getName(), dt, Float::sum);
        if (total < t.PassSeconds) return;
        elapsed.put(world.getName(), 0f);
        world.execute(() -> {
            try {
                service.pass(world, false);
                if (++passes % PRUNE_PASSES == 0) service.prune();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Poop pass failed in world " + world.getName(), e);
            }
        });
    }

    /**
     * A player places a pile: refused on farmland, paths, floors, in water or on a building (the block's own support
     * rule already needs a whole face below). Accepted piles may join a pile nearby, in a world task afterwards.
     */
    static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final PoopService service;

        Place(@Nonnull PoopService service) {
            super(PlaceBlockEvent.class);
            this.service = service;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
            if (event.isCancelled()) return;
            ItemStack item = event.getItemInHand();
            String blockId = item != null ? item.getBlockKey() : null;
            if (PoopRules.countOf(blockId) == 0) return;
            World world = store.getExternalData().getWorld();
            Vector3i pos = new Vector3i(event.getTargetBlock());
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            PoopRules.Ground g = new PoopBlocks(world).ground(pos.x, pos.y, pos.z);
            String why = g == null ? "ok" : PoopRules.groundCheck(new PoopRules.Ground(g.below(), g.belowSolid(), g.belowFiller(), null, false,
                g.fluidAt(), g.fluidBelow()), PoopTuning.get().ExcludedGround);
            // "no_ground" is left to the block's own support rule, which the engine checks itself.
            if (!"ok".equals(why) && !"no_ground".equals(why)) {
                event.setCancelled(true);
                if (player != null) player.sendMessage(Message.translation("cozyranch_produce.poop.place.refused"));
                return;
            }
            world.execute(() -> {
                try {
                    service.afterPlayerPlaced(world, pos.x, pos.y, pos.z);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Poop pile merge failed at " + pos, e);
                }
            });
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }
}
