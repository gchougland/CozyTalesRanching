package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.taming.command.NameCommand;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The naming page: a text field prefilled with the current name, Save and Not now. Opened right after an animal is
 * tamed or claimed, and by the house page's rename button. Same name rules as {@code /ranch name}. Under the field it
 * says how to make the animal stay or follow (Crouch + Use, the player's real keys) and how to rename it later.
 * World thread.
 */
public final class NamePage extends InteractiveCustomUIPage<NamePage.Event> {
    public static final String DOCUMENT = "CozyRanch/NamePage.ui";
    static final String ACTION_SAVE = "Save";
    static final String ACTION_CANCEL = "Cancel";

    private final UUID animalId;
    /** Shown under the field after a refused name; null for none. */
    @Nullable private String noticeKey;

    private NamePage(@Nonnull PlayerRef playerRef, @Nonnull UUID animalId) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.animalId = animalId;
    }

    /** Opens the page when the player may name this animal; otherwise does nothing. Player's world thread. */
    static void open(@Nonnull PlayerRef player, @Nonnull UUID animalId) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        Optional<AnimalRecord> record = Ranch.animals().get(animalId);
        if (record.isEmpty() || !mayName(player.getUuid(), record.get())) return;
        Store<EntityStore> store = ref.getStore();
        Player p = store.getComponent(ref, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(ref, store, new NamePage(player, animalId));
    }

    private static boolean mayName(@Nonnull UUID playerId, @Nonnull AnimalRecord record) {
        if (!RanchPermissions.has(playerId, RanchPermissions.NAME)) return false;
        return playerId.equals(record.ownerId) || RanchPermissions.has(playerId, RanchPermissions.ADMIN_MANAGE_ANY);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        AnimalRecord record = Ranch.animals().get(animalId).orElse(null);
        commands.append(DOCUMENT);
        boolean named = record != null && record.name != null && !record.name.isBlank();
        Message species = record == null ? Message.raw("")
            : SpeciesRegistry.byId(record.speciesId).map(AnimalControl::speciesName).orElse(Message.raw(record.speciesId));
        commands.set("#NameTitle.TextSpans", Message.translation(named ? "cozyranch_taming.namepage.title.rename" : "cozyranch_taming.namepage.title"));
        commands.set("#NamePrompt.TextSpans", named
            ? Message.translation("cozyranch_taming.namepage.prompt.rename").param("name", record.name)
            : Message.translation("cozyranch_taming.namepage.prompt").param("animal", species));
        commands.set("#NameField.Value", named ? record.name : "");
        commands.set("#NameHint.TextSpans", Message.translation("cozyranch_taming.namepage.limit").param("max", NameCommand.MAX_NAME_LENGTH));
        // how to make it stay or follow, and how to rename it later
        commands.set("#StayText.TextSpans", Message.translation("cozyranch_taming.namepage.stay"));
        commands.set("#RenameLater.TextSpans", Message.translation("cozyranch_taming.namepage.rename_later"));
        commands.set("#NameNotice.TextSpans", noticeKey == null ? Message.raw("") : Message.translation(noticeKey));
        commands.set("#SaveButton.TextSpans", Message.translation("cozyranch_taming.namepage.save"));
        commands.set("#CancelButton.TextSpans", Message.translation("cozyranch_taming.namepage.cancel"));
        EventData save = EventData.of("Action", ACTION_SAVE).append("@Name", "#NameField.Value");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", save, false);
        events.addEventBinding(CustomUIEventBindingType.Validating, "#NameField", save, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", EventData.of("Action", ACTION_CANCEL), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Event data) {
        if (!ACTION_SAVE.equals(data.action)) {
            close();
            return;
        }
        AnimalRecord record = Ranch.animals().get(animalId).orElse(null);
        if (record == null || !mayName(playerRef.getUuid(), record)) {
            playerRef.sendMessage(Message.translation("cozyranch_taming.command.not_yours"));
            close();
            return;
        }
        String clean = TamingRules.cleanName(data.name, NameCommand.MAX_NAME_LENGTH);
        if (clean == null) {
            noticeKey = "cozyranch_taming.name.invalid";
            rebuild();
            return;
        }
        record.name = clean;
        Ranch.animals().markDirty(record);
        World world = record.worldName == null || record.worldName.isEmpty() ? null : Universe.get().getWorld(record.worldName);
        if (world != null) world.execute(() -> Nameplates.applyLoaded(record));
        RanchChat.tell(playerRef, Message.translation("cozyranch_taming.name.done").param("name", clean));
        close();
    }

    public static final class Event {
        public static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
            .append(new KeyedCodec<>("@Name", Codec.STRING), (e, v) -> e.name = v, e -> e.name).add()
            .build();

        @Nullable String action;
        @Nullable String name;
    }
}
