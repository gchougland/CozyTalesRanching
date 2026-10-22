package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The house page (design doc section 13). Rebuilt whole after every action. World thread. */
public final class HousePage extends InteractiveCustomUIPage<HousePage.Event> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private final HouseSource source;
    @Nullable private Txt notice;

    public HousePage(@Nonnull PlayerRef playerRef, @Nonnull HouseSource source) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.source = source;
    }

    /** The opener installed with {@code Ranch.installHousePages}. */
    public static boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house) {
        return open(playerRef, store, HouseSource.live(house.houseId));
    }

    public static boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseSource source) {
        if (!playerRef.isValid()) return false;
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef ref = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || ref == null || source.house() == null) return false;
        player.getPageManager().openCustomPage(playerRef, store, new HousePage(ref, source));
        return true;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        HouseRecord house = source.house();
        if (house == null) {
            HousePageLayout.render(emptyView(), new ServerUiOut(commands, events));
            return;
        }
        HousePageLayout.render(view(house), new ServerUiOut(commands, events));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Event data) {
        HouseRecord house = source.house();
        if (house == null) {
            close();
            return;
        }
        try {
            notice = act(house, data);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "House page action " + data.action + " failed", e);
            notice = Txt.ui("result.error");
        }
        rebuild();
    }

    @Nullable
    private Txt act(@Nonnull HouseRecord house, @Nonnull Event data) {
        HouseService service = source.service();
        String action = data.action == null ? "" : data.action;
        if (action.equals(HousePageLayout.ACTION_BREEDING)) {
            boolean enable = !house.breedingEnabled;
            HouseService.Result result = service.setBreeding(playerRef, house, enable);
            return result == HouseService.Result.OK ? Txt.ui(enable ? "result.breeding.on" : "result.breeding.off") : failure(result);
        }
        if (action.equals(HousePageLayout.ACTION_RENAME)) {
            String name = HouseView.cleanName(data.name);
            HouseService.Result result = service.rename(playerRef, house, name);
            return result == HouseService.Result.OK ? Txt.ui(name == null ? "result.renamed.reset" : "result.renamed") : failure(result);
        }
        AnimalRecord animal = data.animal == null ? null : source.animal(data.animal);
        if (animal == null) return Txt.ui("result.not_found");
        Txt name = HouseView.animalName(animal, HouseView.speciesName(species(house), house.speciesId));
        switch (action) {
            case HousePageLayout.ACTION_MOVE_IN -> {
                if (!HouseSource.isFollower(animal, house, playerRef.getUuid())) return Txt.ui("result.not_found");
                HouseService.Result result = service.assign(playerRef, animal, house);
                return result == HouseService.Result.OK ? Txt.ui("result.assigned").with("name", name) : failure(result);
            }
            case HousePageLayout.ACTION_MOVE_OUT, HousePageLayout.ACTION_CALL_BACK -> {
                if (!house.houseId.equals(animal.houseId)) return Txt.ui("result.not_found");
                boolean out = action.equals(HousePageLayout.ACTION_MOVE_OUT);
                HouseService.Result result = out ? service.unassign(playerRef, animal) : service.resummon(playerRef, animal);
                return result == HouseService.Result.OK ? Txt.ui(out ? "result.unassigned" : "result.resummoned").with("name", name) : failure(result);
            }
            default -> {
                return null;
            }
        }
    }

    @Nonnull
    private HouseView view(@Nonnull HouseRecord house) {
        SpeciesDefinition species = species(house);
        List<AnimalRecord> residents = source.residents(house);
        List<AnimalRecord> followers = source.followers(house, playerRef.getUuid());
        HouseService service = source.service();
        return HouseView.of(house, species, houseNameKey(house, species), residents, service::isInside, followers, notice);
    }

    @Nonnull
    private HouseView emptyView() {
        HouseRecord gone = new HouseRecord();
        gone.houseId = new UUID(0, 0);
        return HouseView.of(gone, null, null, List.of(), id -> false, List.of(), Txt.ui("result.gone"));
    }

    @Nullable
    private static SpeciesDefinition species(@Nonnull HouseRecord house) {
        return SpeciesRegistry.byId(house.speciesId).orElse(null);
    }

    /** The house item's own name key (e.g. Chicken Coop), from the placed block or the species' house block. */
    @Nullable
    private static String houseNameKey(@Nonnull HouseRecord house, @Nullable SpeciesDefinition species) {
        for (String id : new String[] {house.blockId, species == null ? "" : species.HouseBlock}) {
            if (id == null || id.isBlank()) continue;
            Item item = Item.getAssetMap().getAsset(id);
            if (item != null) return item.getTranslationKey();
        }
        return null;
    }

    @Nonnull
    private static Txt failure(@Nonnull HouseService.Result result) {
        return Txt.ui("result." + result.name().toLowerCase(Locale.ROOT));
    }
    public static final class Event {
        public static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
            .append(new KeyedCodec<>("Animal", Codec.STRING), (e, v) -> e.animal = parseUuid(v), e -> e.animal == null ? null : e.animal.toString()).add()
            .append(new KeyedCodec<>("@Name", Codec.STRING), (e, v) -> e.name = v, e -> e.name).add()
            .build();

        @Nullable String action;
        @Nullable UUID animal;
        @Nullable String name;

        @Nullable
        private static UUID parseUuid(@Nullable String s) {
            if (s == null || s.isBlank()) return null;
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
