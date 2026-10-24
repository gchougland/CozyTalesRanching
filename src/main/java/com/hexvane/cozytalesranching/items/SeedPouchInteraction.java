package com.hexvane.cozytalesranching.items;

import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SimpleInteraction;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code "Type": "CozyRanchOpenSeedPouch"} on the Seed Pouch: opens one pouch from the held stack into a few random
 * vanilla seeds. The seed list and how many come out are data on the item ({@code Seeds}: item id to weight,
 * {@code Rolls}). Like eating food, it needs no permission and touches only the player's own inventory. The
 * interaction only reads; the change runs with {@link World#execute} on the world thread, outside ticks, and checks
 * the held item again first.
 */
public final class SeedPouchInteraction extends SimpleInstantInteraction {
    public static final String TYPE = "CozyRanchOpenSeedPouch";
    public static final BuilderCodec<SeedPouchInteraction> CODEC = BuilderCodec
        .builder(SeedPouchInteraction.class, SeedPouchInteraction::new, SimpleInstantInteraction.CODEC)
        .documentation("Opens one Seed Pouch from the held stack into random seeds.")
        .appendInherited(
            new KeyedCodec<>("Seeds", new MapCodec<>(Codec.INTEGER, HashMap<String, Integer>::new)),
            (i, v) -> i.seeds = v,
            i -> i.seeds,
            (o, p) -> o.seeds = p.seeds
        )
        .documentation("Seed item id to weight. Ids that are not items are skipped.")
        .add()
        .appendInherited(
            new KeyedCodec<>("Rolls", Codec.INTEGER),
            (i, v) -> i.rolls = v,
            i -> i.rolls,
            (o, p) -> o.rolls = p.rolls
        )
        .documentation("How many seeds one pouch gives (1 to 16, default 3).")
        .add()
        .build();

    private Map<String, Integer> seeds = Map.of();
    private Integer rolls = SeedTable.DEFAULT_ROLLS;

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> player = context.getEntity();
        ItemStack held = context.getHeldItem();
        if (buffer == null || player == null || !player.isValid() || ItemStack.isEmpty(held)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRef = buffer.getComponent(player, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        SeedTable table = new SeedTable(seeds, rolls == null ? 0 : rolls, id -> Item.getAssetMap().getAsset(id) != null);
        if (table.isEmpty()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = buffer.getExternalData().getWorld();
        String pouchId = held.getItemId();
        world.execute(() -> open(world, playerRef, pouchId, table));
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {}

    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new SimpleInteraction();
    }

    /** World thread, outside ticks. */
    static void open(@Nonnull World world, @Nonnull PlayerRef player, @Nonnull String pouchId, @Nonnull SeedTable table) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        ItemStack inHand = InventoryComponent.getItemInHand(store, ref);
        if (ItemStack.isEmpty(inHand) || !pouchId.equals(inHand.getItemId())) return;
        Map<String, Integer> got = table.open(ThreadLocalRandom.current());
        if (got.isEmpty()) return;
        InventoryHelper.removeItemInHand(ref, store, 1);
        TransformComponent at = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d origin = at == null ? null : new Vector3d(at.getPosition()).add(0, 1, 0);
        for (Map.Entry<String, Integer> e : got.entrySet()) {
            ItemUtils.interactivelyPickupItem(ref, new ItemStack(e.getKey(), e.getValue()), origin, store);
        }
        RanchChat.tell(player, Message.translation("cozyranch_items.seed_pouch.opened").param("count", table.rolls()));
    }
}
