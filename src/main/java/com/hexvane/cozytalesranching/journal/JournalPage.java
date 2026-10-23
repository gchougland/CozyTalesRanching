package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The Rancher's Journal: contents, one page per species, memories. Rebuilt whole on every turn of a page. World thread. */
public final class JournalPage extends InteractiveCustomUIPage<JournalPage.Event> {
    private final Supplier<PlayerJournal> journal;
    private String view;
    @Nullable private String speciesId;
    private int spread;

    JournalPage(@Nonnull PlayerRef playerRef, @Nonnull Supplier<PlayerJournal> journal, @Nullable String speciesId) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.journal = journal;
        this.speciesId = speciesId;
        this.view = speciesId == null ? JournalLayout.ACTION_CONTENTS : JournalLayout.ACTION_SPECIES;
    }

    /** The opener installed with {@code Ranch.installJournal}: the player's own journal. */
    public static boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable String speciesId) {
        PlayerRef ref = playerRef.isValid() ? store.getComponent(playerRef, PlayerRef.getComponentType()) : null;
        if (ref == null) return false;
        if (!RanchPermissions.has(ref.getUuid(), RanchPermissions.JOURNAL)) {
            ref.sendMessage(ServerUiOut.message(Txt.j("open.denied")));
            return false;
        }
        JournalPage page = live(ref, speciesId);
        return page != null && show(playerRef, store, page);
    }

    /** A page on the player's own journal, or null when the journal role is not running. */
    @Nullable
    static JournalPage live(@Nonnull PlayerRef ref, @Nullable String speciesId) {
        JournalService service = JournalService.get();
        if (service == null) return null;
        UUID id = ref.getUuid();
        return new JournalPage(ref, () -> service.copy(id), known(speciesId));
    }

    /** Opens a page on a fixed journal (the admin demo). */
    static boolean openFixed(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull PlayerJournal fixed, @Nullable String speciesId) {
        PlayerRef ref = playerRef.isValid() ? store.getComponent(playerRef, PlayerRef.getComponentType()) : null;
        if (ref == null) return false;
        return show(playerRef, store, new JournalPage(ref, () -> fixed, known(speciesId)));
    }

    private static boolean show(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull JournalPage page) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return false;
        player.getPageManager().openCustomPage(playerRef, store, page);
        playOpenSound(page.playerRef);
        return true;
    }

    static void playOpenSound(@Nonnull PlayerRef ref) {
        SoundUtil.playSoundEvent2dToPlayer(ref, RanchSounds.JOURNAL_OPEN, SoundCategory.UI);
    }

    @Nullable
    private static String known(@Nullable String speciesId) {
        if (speciesId == null || speciesId.isBlank()) return null;
        String id = speciesId.trim().toLowerCase(Locale.ROOT);
        for (SpeciesDefinition s : JournalBook.get().ordered()) if (s.Id.equals(id)) return id;
        return null;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        JournalView.Context context = context();
        PlayerJournal j = journal.get();
        UiOut ui = new ServerUiOut(commands, events);
        SpeciesDefinition species = view.equals(JournalLayout.ACTION_SPECIES) ? find(context.species(), speciesId) : null;
        if (species != null) {
            JournalLayout.species(JournalView.species(j, context, species), ui);
        } else if (view.equals(JournalLayout.ACTION_MEMORIES)) {
            JournalLayout.memories(JournalView.memories(j, context, spread), ui);
        } else {
            JournalLayout.contents(JournalView.contents(j, context, playerRef.getUsername()), ui);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Event data) {
        String action = data.action == null ? "" : data.action;
        switch (action) {
            case JournalLayout.ACTION_SPECIES -> {
                String id = known(data.arg);
                if (id != null) {
                    view = action;
                    speciesId = id;
                }
            }
            case JournalLayout.ACTION_MEMORIES -> {
                view = action;
                try {
                    spread = Math.max(0, Integer.parseInt(data.arg == null ? "0" : data.arg));
                } catch (NumberFormatException e) {
                    spread = 0;
                }
            }
            default -> view = JournalLayout.ACTION_CONTENTS;
        }
        rebuild();
    }

    @Nonnull
    static JournalView.Context context() {
        JournalBook book = JournalBook.get();
        return new JournalView.Context(book, book.ordered(), TraitRegistry.get(), ServerLookup.INSTANCE);
    }

    @Nullable
    private static SpeciesDefinition find(@Nonnull List<SpeciesDefinition> all, @Nullable String id) {
        if (id == null) return null;
        for (SpeciesDefinition s : all) if (s.Id.equals(id)) return s;
        return null;
    }

    /** Item names and lang keys from the running server. */
    enum ServerLookup implements JournalView.Lookup {
        INSTANCE;

        @Nullable
        @Override
        public String itemNameKey(@Nonnull String itemId) {
            Item item = Item.getAssetMap().getAsset(itemId);
            return item == null ? null : item.getTranslationKey();
        }

        @Override
        public boolean hasKey(@Nonnull String key) {
            I18nModule i18n = I18nModule.get();
            return i18n != null && i18n.getMessage("en-US", key) != null;
        }

        @Override
        public int breedingMinHearts() {
            return RanchConfig.get().breedingMinHearts();
        }

        @Nonnull
        @Override
        public ZoneId zone() {
            return ZoneId.systemDefault();
        }
    }

    public static final class Event {
        public static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
            .append(new KeyedCodec<>("Arg", Codec.STRING), (e, v) -> e.arg = v, e -> e.arg).add()
            .build();

        @Nullable String action;
        @Nullable String arg;
    }
}
