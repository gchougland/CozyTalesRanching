package com.hexvane.cozytalesranching.items;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

/**
 * Raven armour's perk ({@link RavenFletchingRules}): when a player in Adventure mode looses an arrow from a bow or
 * crossbow, the worn Raven pieces may give the arrow back. Runs only on vanilla's {@link ProjectileLaunchEvent}
 * (once per shot, never per tick); reads the four armour slots; the refund itself waits for {@link World#execute}.
 */
final class RavenFletchingSystem extends EntityEventSystem<EntityStore, ProjectileLaunchEvent> {
    private final Map<UUID, Long> lastRoll = new ConcurrentHashMap<>();

    RavenFletchingSystem() {
        super(ProjectileLaunchEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull ProjectileLaunchEvent event) {
        if (event.getInteractionType() != InteractionType.Primary || !event.isLaunchedByWeapon()) return;
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null || player.getGameMode() != GameMode.Adventure) return;
        ItemStack held = InventoryComponent.getItemInHand(commandBuffer, ref);
        if (ItemStack.isEmpty(held) || !RavenFletchingRules.shootsArrows(held.getItemId())) return;
        int pieces = RavenFletchingRules.piecesWorn(worn(commandBuffer.getComponent(ref, InventoryComponent.Armor.getComponentType())));
        if (pieces == 0) return;
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        long now = System.currentTimeMillis();
        Long last = lastRoll.get(playerRef.getUuid());
        if (!RavenFletchingRules.mayRoll(now, last == null ? -1 : last)) return;
        lastRoll.put(playerRef.getUuid(), now);
        if (ThreadLocalRandom.current().nextDouble() >= RavenFletchingRules.refundChance(pieces)) return;
        World world = store.getExternalData().getWorld();
        world.execute(() -> refund(world, playerRef));
    }

    private static List<String> worn(InventoryComponent.Armor armor) {
        List<String> ids = new ArrayList<>(4);
        if (armor == null) return ids;
        ItemContainer slots = armor.getInventory();
        for (short i = 0; i < slots.getCapacity(); i++) {
            ItemStack stack = slots.getItemStack(i);
            if (!ItemStack.isEmpty(stack)) ids.add(stack.getItemId());
        }
        return ids;
    }

    /** World thread, outside ticks. */
    private static void refund(@Nonnull World world, @Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        ItemUtils.interactivelyPickupItem(ref, new ItemStack(RavenFletchingRules.ARROW, 1), null, world.getEntityStore().getStore());
    }
}
