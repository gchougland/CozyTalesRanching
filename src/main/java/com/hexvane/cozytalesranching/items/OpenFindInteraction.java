package com.hexvane.cozytalesranching.items;

import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code "Type": "CozyRanchOpenFind"} on the Fox Trinket and the Shiny Pebble: opens one from the held stack into a
 * single weighted find. The table ({@code Finds}: Item, Weight, Min, Max) and the chat line ({@code Message}, a
 * translation key with {count} and {item}) are data on the item. Like the Seed Pouch it needs no permission and
 * touches only the player's own inventory; the change runs on the world thread with {@link World#execute} after the
 * held item is checked again. No systems, no ticks, no saved data.
 */
public final class OpenFindInteraction extends SimpleInstantInteraction {
    public static final String TYPE = "CozyRanchOpenFind";
    public static final String DEFAULT_MESSAGE = "cozyranch_items.find.opened";
    public static final BuilderCodec<OpenFindInteraction> CODEC = BuilderCodec
        .builder(OpenFindInteraction.class, OpenFindInteraction::new, SimpleInstantInteraction.CODEC)
        .documentation("Opens one held find item into one weighted random find.")
        .appendInherited(
            new KeyedCodec<>("Finds", new ArrayCodec<>(FindEntry.CODEC, FindEntry[]::new)),
            (i, v) -> i.finds = v,
            i -> i.finds,
            (o, p) -> o.finds = p.finds
        )
        .documentation("Weighted finds: Item, Weight, Min, Max.")
        .add()
        .appendInherited(
            new KeyedCodec<>("Message", Codec.STRING),
            (i, v) -> i.message = v,
            i -> i.message,
            (o, p) -> o.message = p.message
        )
        .documentation("Translation key told to the player, with {count} and {item}.")
        .add()
        .build();

    private FindEntry[] finds = new FindEntry[0];
    private String message = DEFAULT_MESSAGE;

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
        FindTable table = new FindTable(finds == null ? List.of() : List.of(finds), id -> Item.getAssetMap().getAsset(id) != null);
        if (table.isEmpty()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = buffer.getExternalData().getWorld();
        String heldId = held.getItemId();
        String key = message == null || message.isBlank() ? DEFAULT_MESSAGE : message;
        world.execute(() -> open(world, playerRef, heldId, table, key));
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {}

    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new SimpleInteraction();
    }

    /** World thread, outside ticks. */
    static void open(@Nonnull World world, @Nonnull PlayerRef player, @Nonnull String heldId, @Nonnull FindTable table, @Nonnull String key) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        ItemStack inHand = InventoryComponent.getItemInHand(store, ref);
        if (ItemStack.isEmpty(inHand) || !heldId.equals(inHand.getItemId())) return;
        FindTable.Find find = table.roll(ThreadLocalRandom.current());
        if (find == null) return;
        Item item = Item.getAssetMap().getAsset(find.itemId());
        if (item == null) return;
        InventoryHelper.removeItemInHand(ref, store, 1);
        TransformComponent at = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d origin = at == null ? null : new Vector3d(at.getPosition()).add(0, 1, 0);
        ItemUtils.interactivelyPickupItem(ref, new ItemStack(find.itemId(), find.count()), origin, store);
        RanchChat.tell(player, Message.translation(key).param("count", find.count()).param("item", Message.translation(item.getTranslationKey())));
    }
}
