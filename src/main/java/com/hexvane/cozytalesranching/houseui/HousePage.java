package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.care.CareApi;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseAccess;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.taming.TamingApi;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The house page (design doc section 13). Rebuilt whole after every action. World thread. */
public final class HousePage extends InteractiveCustomUIPage<HousePage.Event> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    /** Refresh at most once per 2 seconds per player. */
    static final long REFRESH_LIMIT_MS = 2_000L;
    static final RateLimit REFRESH_LIMIT = new RateLimit(REFRESH_LIMIT_MS);

    private final HouseSource source;
    @Nullable private Txt notice;
    @Nonnull private HouseView.PageState state;
    /** Set when an action opened another page in this one's place, so the house page must not be sent again. */
    private boolean replaced;

    public HousePage(@Nonnull PlayerRef playerRef, @Nonnull HouseSource source) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.source = source;
        this.state = source.initialState();
    }

    /** The opener installed with {@code Ranch.installHousePages}. */
    public static boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house) {
        return open(playerRef, store, HouseSource.live(house.houseId));
    }

    /** The opener for using the house with followers none of whom could move in: the page opens with the reason. */
    public static boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house,
                               @Nullable HouseService.Result refusal) {
        return open(playerRef, store, HouseSource.live(house.houseId), HouseView.refusalNotice(refusal));
    }

    public static boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseSource source) {
        return open(playerRef, store, source, null);
    }

    public static boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseSource source,
                               @Nullable Txt notice) {
        if (!playerRef.isValid()) return false;
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef ref = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || ref == null || source.house() == null) return false;
        HousePage page = new HousePage(ref, source);
        page.notice = notice;
        player.getPageManager().openCustomPage(playerRef, store, page);
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
            notice = act(house, data, ref, store);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "House page action " + data.action + " failed", e);
            notice = Txt.ui("result.error");
        }
        if (replaced) return;
        rebuild();
    }

    @Nullable
    private Txt act(@Nonnull HouseRecord house, @Nonnull Event data, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        HouseService service = source.service();
        String action = data.action == null ? "" : data.action;
        switch (action) {
            case HousePageLayout.ACTION_BREEDING -> {
                boolean enable = !house.breedingEnabled;
                HouseService.Result result = service.setBreeding(playerRef, house, enable);
                return result == HouseService.Result.OK ? Txt.ui(enable ? "result.breeding.on" : "result.breeding.off") : failure(result);
            }
            case HousePageLayout.ACTION_VISITORS -> {
                boolean enable = !house.visitorsEnabled;
                HouseService.Result result = service.setVisitors(playerRef, house, enable);
                return result == HouseService.Result.OK ? Txt.ui(enable ? "result.visitors.on" : "result.visitors.off") : failure(result);
            }
            case HousePageLayout.ACTION_RENAME -> {
                String name = HouseView.cleanName(data.name);
                HouseService.Result result = service.rename(playerRef, house, name);
                return result == HouseService.Result.OK ? Txt.ui(name == null ? "result.renamed.reset" : "result.renamed") : failure(result);
            }
            case HousePageLayout.ACTION_COLLECT -> {
                return collect(house, ref, store);
            }
            case HousePageLayout.ACTION_JOURNAL -> {
                if (!RanchPermissions.has(playerRef.getUuid(), RanchPermissions.JOURNAL)) return failure(HouseService.Result.NO_PERMISSION);
                if (source.openJournal(ref, store, house.speciesId)) {
                    replaced = true;
                    return null;
                }
                playerRef.sendMessage(ServerUiOut.message(Txt.ui("journal.unavailable")));
                return notice;
            }
            case HousePageLayout.ACTION_REFRESH -> {
                return refresh(house, store);
            }
            case HousePageLayout.ACTION_BORDER -> {
                return border(house, store);
            }
            case HousePageLayout.ACTION_CANCEL_MOVE -> {
                state = new HouseView.PageState(state.selected(), false);
                return null;
            }
            default -> {
                // every other action names one of this house's animals
            }
        }
        AnimalRecord animal = data.animal == null ? null : source.animal(data.animal);
        if (animal == null) return Txt.ui("result.not_found");
        Txt name = HouseView.animalName(animal, HouseView.speciesName(species(house), house.speciesId));
        if (action.equals(HousePageLayout.ACTION_NAME)) {
            if (!house.houseId.equals(animal.houseId)) return Txt.ui("result.not_found");
            if (!canName(animal)) return failure(HouseService.Result.NOT_OWNER);
            replaced = true;
            TamingApi.openNamePage(playerRef, animal.animalId);
            return null;
        }
        if (action.equals(HousePageLayout.ACTION_MOVE_IN)) {
            if (!HouseSource.isFollower(animal, house, playerRef.getUuid())) return Txt.ui("result.not_found");
            HouseService.Result result = service.assign(playerRef, animal, house);
            return result == HouseService.Result.OK ? Txt.ui("result.assigned").with("name", name) : failure(result);
        }
        if (!house.houseId.equals(animal.houseId)) return Txt.ui("result.not_found");
        switch (action) {
            case HousePageLayout.ACTION_SELECT -> {
                state = new HouseView.PageState(animal.animalId, false);
                return null;
            }
            case HousePageLayout.ACTION_PICK_HOUSE -> {
                state = new HouseView.PageState(animal.animalId, true);
                return null;
            }
            case HousePageLayout.ACTION_LOCATE -> {
                Txt denied = manageCheck(house);
                if (denied != null) return denied;
                state = new HouseView.PageState(animal.animalId, false);
                playerRef.sendMessage(ServerUiOut.message(source.locate(animal, name, ref, store)));
                return null;
            }
            case HousePageLayout.ACTION_MOVE_TO -> {
                state = new HouseView.PageState(animal.animalId, false);
                HouseRecord target = data.house == null ? null : source.moveTargets(house).stream()
                    .filter(h -> h.houseId.equals(data.house)).findFirst().orElse(null);
                if (target == null) return Txt.ui("result.move.gone");
                HouseService.Result result = service.assign(playerRef, animal, target);
                if (result != HouseService.Result.OK) return failure(result);
                state = HouseView.PageState.NONE;
                return Txt.ui("result.moved").with("name", name).with("house", houseName(target));
            }
            case HousePageLayout.ACTION_MOVE_OUT, HousePageLayout.ACTION_CALL_BACK -> {
                state = new HouseView.PageState(animal.animalId, false);
                boolean out = action.equals(HousePageLayout.ACTION_MOVE_OUT);
                HouseService.Result result = out ? service.unassign(playerRef, animal) : service.resummon(playerRef, animal);
                if (result == HouseService.Result.OK && out) state = HouseView.PageState.NONE;
                return result == HouseService.Result.OK ? Txt.ui(out ? "result.unassigned" : "result.resummoned").with("name", name) : failure(result);
            }
            default -> {
                return null;
            }
        }
    }

    /** Collect, then a short line: how many items came, or why nothing did. */
    @Nonnull
    private Txt collect(@Nonnull HouseRecord house, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        HouseService.Result access = HouseAccess.collect(playerRef.getUuid(), house);
        if (access != HouseService.Result.OK) return failure(access);
        Map<String, Integer> before = source.stored(house);
        if (before.values().stream().noneMatch(n -> n != null && n > 0)) return Txt.ui("result.collect.nothing");
        int moved = source.collect(ref, store, house);
        boolean bucketsLeft = source.stored(house).entrySet().stream()
            .anyMatch(e -> HouseView.needsBucket(e.getKey()) && e.getValue() != null && e.getValue() > 0);
        if (moved > 0) return Txt.ui(bucketsLeft ? "result.collected.buckets" : "result.collected").with("count", moved);
        boolean onlyBuckets = before.entrySet().stream().allMatch(e -> HouseView.needsBucket(e.getKey()) || e.getValue() == null || e.getValue() <= 0);
        return Txt.ui(onlyBuckets ? "result.collect.buckets" : "result.collect.full");
    }

    /** Looks around this house again now (habitat and the troughs near it), at most once per 2 s per player. */
    @Nonnull
    private Txt refresh(@Nonnull HouseRecord house, @Nonnull Store<EntityStore> store) {
        if (!REFRESH_LIMIT.tryAcquire(playerRef.getUuid(), System.currentTimeMillis())) return Txt.ui("result.refresh.wait");
        World world = store.getExternalData().getWorld();
        if (!world.getName().equals(house.worldName) && !source.demo()) return Txt.ui("result.refresh.elsewhere");
        if (!source.rescan(house, world)) return Txt.ui("result.unavailable");
        HouseBorder.refresh(playerRef, world, house);
        return Txt.ui("result.refreshed");
    }

    /** Turns this house's area lights on or off for the viewer. */
    @Nonnull
    private Txt border(@Nonnull HouseRecord house, @Nonnull Store<EntityStore> store) {
        if (HouseBorder.isOn(playerRef.getUuid(), house.houseId)) {
            HouseBorder.stop(playerRef.getUuid());
            return Txt.ui("result.border.off");
        }
        World world = store.getExternalData().getWorld();
        if (!world.getName().equals(house.worldName) && !source.demo()) return Txt.ui("result.refresh.elsewhere");
        HouseBorder.start(playerRef, world, house, System.currentTimeMillis());
        int radius = RanchConfig.get().habitatScanRadius();
        boolean trough = HouseBorder.showTroughRing(radius, RanchConfig.get().troughRange());
        return Txt.ui(trough ? "result.border.on.trough" : "result.border.on");
    }

    /** The viewer owns the animal, or may manage anyone's. */
    private boolean canName(@Nonnull AnimalRecord animal) {
        UUID id = playerRef.getUuid();
        return id.equals(animal.ownerId) || RanchPermissions.has(id, RanchPermissions.ADMIN_MANAGE_ANY);
    }

    /** The house rule ({@link HouseAccess}: owner, a land claim allowing it, or manageany); null when allowed. */
    @Nullable
    private Txt manageCheck(@Nonnull HouseRecord house) {
        HouseService.Result result = HouseAccess.manage(playerRef.getUuid(), house);
        return result == HouseService.Result.OK ? null : failure(result);
    }

    @Nonnull
    private HouseView view(@Nonnull HouseRecord house) {
        SpeciesDefinition species = species(house);
        List<AnimalRecord> residents = source.residents(house);
        List<AnimalRecord> followers = source.followers(house, playerRef.getUuid());
        HouseService service = source.service();
        List<HouseView.MoveTarget> targets = source.moveTargets(house).stream()
            .map(h -> new HouseView.MoveTarget(h.houseId, houseName(h), source.residents(h).size(), Math.max(1, h.capacity)))
            .toList();
        return HouseView.of(house, species, houseNameKey(house, species), residents, service::isInside, followers, notice,
            source.stored(house), state, targets, this::canName, HouseBorder.isOn(playerRef.getUuid(), house.houseId),
            System.currentTimeMillis(), HousePage::counts);
    }

    /** What counts for a habitat feature, from care's habitat definitions and the item names. */
    @Nonnull
    static Txt counts(@Nonnull String featureId) {
        return HabitatCounts.explain(CareApi.habitatFeature(featureId).orElse(null), HousePage::itemNameKey);
    }

    /** An item's name key from the asset map, or null when there is no such item. */
    @Nullable
    static String itemNameKey(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? null : item.getTranslationKey();
    }

    @Nonnull
    private HouseView emptyView() {
        HouseRecord gone = new HouseRecord();
        gone.houseId = new UUID(0, 0);
        return HouseView.of(gone, null, null, List.of(), id -> false, List.of(), Txt.ui("result.gone"),
            Map.of(), HouseView.PageState.NONE, List.of(), r -> false, false, System.currentTimeMillis(), id -> null);
    }

    /** A house's name in the move picker: its own name, else its item's name with where it stands. */
    @Nonnull
    static Txt houseName(@Nonnull HouseRecord house) {
        SpeciesDefinition species = species(house);
        return HouseView.pickerName(house, HouseView.speciesName(species, house.speciesId), houseNameKey(house, species));
    }

    @Nullable
    private static SpeciesDefinition species(@Nonnull HouseRecord house) {
        return SpeciesRegistry.byId(house.speciesId).orElse(null);
    }

    /** The house item's own name key (e.g. Chicken Coop), from the placed block or the species' house block. */
    @Nullable
    static String houseNameKey(@Nonnull HouseRecord house, @Nullable SpeciesDefinition species) {
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
            .append(new KeyedCodec<>("House", Codec.STRING), (e, v) -> e.house = parseUuid(v), e -> e.house == null ? null : e.house.toString()).add()
            .append(new KeyedCodec<>("@Name", Codec.STRING), (e, v) -> e.name = v, e -> e.name).add()
            .build();

        @Nullable String action;
        @Nullable UUID animal;
        @Nullable UUID house;
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
