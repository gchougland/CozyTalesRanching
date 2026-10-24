package com.hexvane.cozytalesranching.items;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.houseui.Txt;
import com.hexvane.cozytalesranching.houseui.UiOut;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Rancher's Ledger page, opened by using the placed ledger: the reader's houses, who lives in each, where they
 * are and what waits in each house, then their animals with no house. Read only (the house page manages); one button
 * opens the Rancher's Journal. Built from the records when it opens. World thread.
 */
public final class LedgerPage extends InteractiveCustomUIPage<LedgerPage.Event> {
    public LedgerPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
    }

    /** World thread, outside ticks. False if the player is gone or may not read it. */
    public static boolean open(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!ref.isValid()) return false;
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return false;
        if (!RanchPermissions.has(playerRef.getUuid(), RanchPermissions.JOURNAL)) {
            playerRef.sendMessage(Message.translation(LedgerView.LANG + "denied"));
            return false;
        }
        player.getPageManager().openCustomPage(ref, store, new LedgerPage(playerRef));
        return true;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        UUID me = playerRef.getUuid();
        LedgerView view = LedgerView.of(Ranch.houses().ownedBy(me), Ranch.animals().ownedBy(me), Ranch.houseService()::isInside,
            id -> Ranch.produce().stored(id), id -> SpeciesRegistry.byId(id).orElse(null), LedgerPage::houseNameKey,
            store.getExternalData().getWorld().getName());
        LedgerLayout.render(view, new Out(commands, events));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Event data) {
        if (!LedgerLayout.ACTION_JOURNAL.equals(data.action)) return;
        if (!Ranch.journal().open(ref, store, null)) {
            playerRef.sendMessage(Message.translation("cozyranch_houseui.journal.unavailable"));
        }
    }

    /** The house item's own name key (e.g. Chicken Coop), from the placed block or the species' house block. */
    @Nullable
    static String houseNameKey(@Nonnull HouseRecord house) {
        SpeciesDefinition species = SpeciesRegistry.byId(house.speciesId).orElse(null);
        for (String id : new String[] {house.blockId, species == null ? "" : species.HouseBlock}) {
            if (id == null || id.isBlank()) continue;
            Item item = Item.getAssetMap().getAsset(id);
            if (item != null) return item.getTranslationKey();
        }
        return null;
    }

    /** {@link UiOut} onto the server's builders. */
    static final class Out implements UiOut {
        private final UICommandBuilder commands;
        private final UIEventBuilder events;

        Out(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
            this.commands = commands;
            this.events = events;
        }

        @Override public void append(@Nonnull String document) { commands.append(document); }

        @Override public void append(@Nonnull String container, @Nonnull String document) { commands.append(container, document); }

        @Override public void clear(@Nonnull String selector) { commands.clear(selector); }

        @Override public void set(@Nonnull String selectorAndProperty, boolean value) { commands.set(selectorAndProperty, value); }

        @Override public void set(@Nonnull String selectorAndProperty, @Nonnull String value) { commands.set(selectorAndProperty, value); }

        @Override public void text(@Nonnull String selector, @Nonnull Txt text) { commands.set(selector + ".TextSpans", message(text)); }

        @Override public void tooltip(@Nonnull String selector, @Nonnull Txt text) { commands.set(selector + ".TooltipText", message(text)); }

        @Override
        public void click(@Nonnull String selector, @Nonnull String action, @Nonnull String animal, @Nonnull String house) {
            events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
        }

        @Override
        public void clickWithField(@Nonnull String selector, @Nonnull String action, @Nonnull String fieldSelector) {
            events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("@Name", fieldSelector + ".Value"), false);
        }

        @Nonnull
        static Message message(@Nonnull Txt text) {
            if (text.key() == null) return Message.raw(text.raw() == null ? "" : text.raw());
            Message m = Message.translation(text.key());
            for (Map.Entry<String, Object> p : text.params().entrySet()) {
                Object v = p.getValue();
                switch (v) {
                    case Txt t -> m = m.param(p.getKey(), message(t));
                    case Integer i -> m = m.param(p.getKey(), i);
                    case Long l -> m = m.param(p.getKey(), l);
                    case Number n -> m = m.param(p.getKey(), n.doubleValue());
                    default -> m = m.param(p.getKey(), String.valueOf(v));
                }
            }
            return m;
        }
    }

    public static final class Event {
        public static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
            .build();

        @Nullable String action;
    }
}
