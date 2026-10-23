package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.AnimalProducedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.api.event.RanchHouseRemovedEvent;
import com.hexvane.cozytalesranching.breeding.TraitBook;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Production passes over a world's houses, collecting into a player's inventory, and dropping the products of a
 * house that is gone. Installed as {@link Ranch#produce()}. Passes and collecting run on the world thread, outside
 * system ticks; {@link #stored} may be called from any thread.
 */
public final class ProduceService implements Ranch.ProduceStorage {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private final ProduceStore store;

    ProduceService(@Nonnull ProduceStore store) {
        this.store = store;
    }

    @Nonnull
    ProduceStore store() {
        return store;
    }

    // ---------------------------------------------------------------- reads

    @Nonnull
    @Override
    public Map<String, Integer> stored(@Nonnull UUID houseId) {
        return store.stored(houseId);
    }

    int total(@Nonnull UUID houseId) {
        return store.total(houseId);
    }

    /** How a product of this house's species is collected; {@code HOUSE} for an item the species no longer lists. */
    @Nonnull
    static ProduceRules.Mode modeOf(@Nonnull String speciesId, @Nonnull String itemId) {
        SpeciesDefinition s = SpeciesRegistry.byId(speciesId).orElse(null);
        if (s != null) {
            for (SpeciesDefinition.Product p : s.Products) {
                if (itemId.equals(p.Item)) {
                    ProduceRules.Mode m = ProduceRules.mode(p.Collect);
                    return m == ProduceRules.Mode.NONE ? ProduceRules.Mode.HOUSE : m;
                }
            }
        }
        return ProduceRules.Mode.HOUSE;
    }

    // ---------------------------------------------------------------- production

    /** What one pass did, for the log and the test command. */
    record PassResult(int houses, int animals, int items, double minutes) {}

    /**
     * One pass over the world's houses. {@code extraMinutes} below 0 is a normal pass (real time since the last one,
     * capped by {@code CatchUpCapMinutes}); 0 or more credits exactly that many minutes (capped the same) without
     * touching the pass clock, which is what {@code /ranch produce tick} does.
     */
    @Nonnull
    PassResult pass(@Nonnull World world, long nowMs, double extraMinutes) {
        ProduceTuning t = ProduceTuning.get();
        String w = world.getName();
        double minutes;
        if (extraMinutes >= 0) {
            minutes = Math.min(extraMinutes, t.CatchUpCapMinutes);
        } else {
            long last = store.lastPass(w);
            minutes = ProduceRules.catchUpMinutes(last, nowMs, t.CatchUpCapMinutes);
            store.setLastPass(w, nowMs);
            if (last > 0 && (nowMs - last) / 60_000.0 > t.CatchUpCapMinutes) {
                LOG.info(String.format(Locale.ROOT, "Produce catch-up in %s: %.1f minutes since the last pass, credited %.1f (cap)",
                    w, (nowMs - last) / 60_000.0, minutes));
            }
        }

        TraitBook traits = TraitRegistry.get();
        List<HouseRecord> houses = Ranch.houses().inWorld(w);
        Set<UUID> liveHouses = new HashSet<>();
        Set<UUID> producing = new HashSet<>();
        List<AnimalProducedEvent> events = new ArrayList<>();
        int producedHouses = 0;
        int items = 0;
        for (HouseRecord house : houses) {
            liveHouses.add(house.houseId);
            store.track(w, house.houseId, house.doorX, house.doorY, house.doorZ);
            int room = ProduceRules.room(store.total(house.houseId), t.HouseCap);
            int madeHere = 0;
            for (AnimalRecord r : Ranch.animals().inHouse(house.houseId)) {
                if (!ProduceRules.produces(r) || !house.houseId.equals(r.houseId)) continue;
                SpeciesDefinition species = SpeciesRegistry.byId(r.speciesId).orElse(null);
                if (species == null) continue;
                producing.add(r.animalId);
                if (minutes <= 0) continue;
                double traitMultiplier = traits.multiplier(r.traits, ProduceRules.PRODUCTION_MULTIPLIER);
                double bonusChance = traits.chance(r.traits, ProduceRules.BONUS_PRODUCT_CHANCE);
                double premium = ProduceRules.premiumMinutes(r.feedBonus, r.feedBonusUntilMs, nowMs, minutes);
                for (SpeciesDefinition.Product p : species.Products) {
                    if (ProduceRules.mode(p.Collect) == ProduceRules.Mode.NONE || !itemExists(p.Item)) continue;
                    double before = store.progress(w, r.animalId, p.Item);
                    ProduceRules.Step step = ProduceRules.step(t, before, minutes, p.IntervalMinutes, p.Quantity, r.happiness, r.hearts,
                        traitMultiplier, bonusChance, premium, room, ThreadLocalRandom.current());
                    if (step.progress() != before) store.setProgress(w, r.animalId, p.Item, step.progress());
                    if (step.added() <= 0) continue;
                    store.add(house.houseId, p.Item, step.added());
                    room -= step.added();
                    madeHere += step.added();
                    events.add(new AnimalProducedEvent(r.view(), p.Item, step.added()));
                }
            }
            if (madeHere > 0) {
                producedHouses++;
                items += madeHere;
            }
        }
        store.keepProgress(w, producing::contains);
        for (UUID id : store.houses(w)) {
            if (!liveHouses.contains(id) && Ranch.houses().get(id).isEmpty()) retire(id, "its house record is gone");
        }
        dropPending(world);
        if (store.hasDirty()) store.scheduleSave();
        if (items > 0) {
            LOG.info(String.format(Locale.ROOT, "Produce pass in %s: %.1f min, %d item(s) from %d animal batch(es) in %d house(s)",
                w, minutes, items, events.size(), producedHouses));
        }
        for (AnimalProducedEvent e : events) RanchEvents.fire(e);
        return new PassResult(producedHouses, events.size(), items, minutes);
    }

    private static final Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static boolean itemExists(@Nonnull String itemId) {
        boolean ok = !itemId.isEmpty() && Item.getAssetMap().getAsset(itemId) != null;
        if (!ok && WARNED.add(itemId)) LOG.warning("Product item '" + itemId + "' is not a known item; it is not produced");
        return ok;
    }

    // ---------------------------------------------------------------- a house goes

    /** Fired by the house role on the world thread after the record is removed. The products drop at the door. */
    void onHouseRemoved(@Nonnull RanchHouseRemovedEvent event) {
        retire(event.getHouseId(), "the house was removed");
        World world = Universe.get().getWorld(event.getWorldName());
        if (world != null) world.execute(() -> dropPending(world));
    }

    private void retire(@Nonnull UUID houseId, @Nonnull String why) {
        ProduceStore.Stock s = store.retire(houseId);
        if (s != null) {
            LOG.info("House " + houseId + " is gone (" + why + "); " + s.total() + " product(s) will drop at "
                + fmt(s.X) + " " + fmt(s.Y) + " " + fmt(s.Z));
        }
        store.scheduleSave();
    }

    /** Drops waiting products of gone houses whose drop point is in a loaded chunk. World thread, outside ticks. */
    void dropPending(@Nonnull World world) {
        List<ProduceStore.Stock> drops = store.pendingDrops(world.getName());
        if (drops.isEmpty()) return;
        SectionReader reader = new SectionReader(world.getChunkStore());
        Store<EntityStore> es = world.getEntityStore().getStore();
        for (ProduceStore.Stock s : drops) {
            int bx = (int) Math.floor(s.X), by = (int) Math.floor(s.Y), bz = (int) Math.floor(s.Z);
            if (!reader.hasStorageInMemory(bx, by, bz)) continue;
            List<ItemStack> stacks = stacks(s.Items);
            if (!stacks.isEmpty()) {
                Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(es, stacks, new Vector3d(s.X, s.Y + 0.5, s.Z), Rotation3f.IDENTITY);
                es.addEntities(holders, AddReason.SPAWN);
            }
            store.dropped(s);
            LOG.info("Dropped " + s.total() + " product(s) of house " + s.HouseId + " in " + s.World + " at "
                + fmt(s.X) + " " + fmt(s.Y) + " " + fmt(s.Z) + ": " + s.Items);
        }
        store.scheduleSave();
    }

    @Nonnull
    private static List<ItemStack> stacks(@Nonnull Map<String, Integer> items) {
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            Item item = Item.getAssetMap().getAsset(e.getKey());
            if (item == null) {
                LOG.warning("Cannot drop unknown product item " + e.getKey() + " x" + e.getValue());
                continue;
            }
            int max = Math.max(1, item.getMaxStack());
            for (int left = e.getValue(); left > 0; left -= max) out.add(new ItemStack(e.getKey(), Math.min(max, left)));
        }
        return out;
    }

    // ---------------------------------------------------------------- collecting

    /**
     * What a collect did. {@code moved} items went into the inventory, {@code left} still wait in the house, of which
     * {@code needBucket} are bucket products waiting for an empty bucket. {@code denied} when the player may not.
     */
    public record CollectResult(boolean denied, int moved, int left, int needBucket, @Nonnull Map<String, Integer> movedItems) {
        static final CollectResult DENIED = new CollectResult(true, 0, 0, 0, Map.of());
    }

    @Override
    public int collect(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> es, @Nonnull HouseRecord house) {
        return collectDetailed(playerRef, es, house).moved();
    }

    /** World thread. Moves what fits into the player's inventory (owner, or {@code cozyranch.admin.manageany}). */
    @Nonnull
    public CollectResult collectDetailed(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> es, @Nonnull HouseRecord house) {
        PlayerRef player = es.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || !mayCollect(player.getUuid(), house)) return CollectResult.DENIED;
        String bucketItem = ProduceTuning.get().BucketItem;
        Map<String, Integer> movedItems = new LinkedHashMap<>();
        int needBucket = 0;
        for (Map.Entry<String, Integer> e : store.stored(house.houseId).entrySet()) {
            String itemId = e.getKey();
            int waiting = e.getValue();
            if (!itemExists(itemId)) continue;
            int moved;
            if (modeOf(house.speciesId, itemId) == ProduceRules.Mode.BUCKET) {
                moved = giveIntoBuckets(playerRef, es, itemId, bucketItem, waiting);
                needBucket += waiting - moved;
            } else {
                moved = give(playerRef, es, itemId, waiting);
            }
            if (moved <= 0) continue;
            store.take(house.houseId, itemId, moved);
            movedItems.put(itemId, moved);
            Player.notifyPickupItem(playerRef, new ItemStack(itemId, moved), null, es);
        }
        int moved = movedItems.values().stream().mapToInt(Integer::intValue).sum();
        int left = store.total(house.houseId);
        if (moved > 0) {
            RanchSounds.play(RanchSounds.PRODUCE_COLLECT, house.doorX, house.doorY, house.doorZ, es);
            store.scheduleSave();
        }
        LOG.info("Collect from house " + house.houseId + " by " + player.getUsername() + ": moved " + moved + " " + movedItems
            + ", " + left + " left, " + needBucket + " waiting for a bucket");
        return new CollectResult(false, moved, left, needBucket, movedItems);
    }

    static boolean mayCollect(@Nonnull UUID playerId, @Nonnull HouseRecord house) {
        if (!RanchPermissions.has(playerId, RanchPermissions.HOUSE_MANAGE)) return false;
        return playerId.equals(house.ownerId) || RanchPermissions.has(playerId, RanchPermissions.ADMIN_MANAGE_ANY);
    }

    /** Gives up to {@code count}, a stack at a time, stopping when the inventory is full. Returns how many went in. */
    private static int give(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> es, @Nonnull String itemId, int count) {
        Item item = Item.getAssetMap().getAsset(itemId);
        int max = item == null ? 1 : Math.max(1, item.getMaxStack());
        int given = 0;
        while (given < count) {
            int n = Math.min(max, count - given);
            ItemStack rest = Player.giveItem(new ItemStack(itemId, n), playerRef, es).getRemainder();
            int in = rest == null || rest.isEmpty() ? n : n - rest.getQuantity();
            given += Math.max(0, in);
            if (in < n) break;
        }
        return given;
    }

    /** One empty bucket out of the inventory, one filled one in, per item. Returns how many were filled. */
    private static int giveIntoBuckets(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> es, @Nonnull String itemId,
                                       @Nonnull String bucketItem, int waiting) {
        CombinedItemContainer inv = InventoryComponent.getCombined(es, playerRef, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        List<Short> slots = new ArrayList<>();
        int[] buckets = {0};
        inv.forEach((slot, stack) -> {
            if (bucketItem.equals(stack.getItemId())) {
                slots.add(slot);
                buckets[0] += stack.getQuantity();
            }
        });
        ProduceRules.BucketPlan plan = ProduceRules.bucketPlan(waiting, buckets[0]);
        int filled = 0;
        int slotIndex = 0;
        while (filled < plan.convert() && slotIndex < slots.size()) {
            short slot = slots.get(slotIndex);
            ItemStack inSlot = inv.getItemStack(slot);
            if (inSlot == null || inSlot.isEmpty() || !bucketItem.equals(inSlot.getItemId())) {
                slotIndex++;
                continue;
            }
            if (!inv.removeItemStackFromSlot(slot, 1).succeeded()) {
                slotIndex++;
                continue;
            }
            ItemStack rest = Player.giveItem(new ItemStack(itemId, 1), playerRef, es).getRemainder();
            if (rest != null && !rest.isEmpty()) {
                ItemStack back = Player.giveItem(new ItemStack(bucketItem, 1), playerRef, es).getRemainder();
                if (back != null && !back.isEmpty()) ItemUtils.dropItem(playerRef, back, es);
                break;
            }
            filled++;
        }
        return filled;
    }

    @Nonnull
    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** For the status command: the house's product items and how each is collected. */
    @Nonnull
    static String describe(@Nonnull Map<String, Integer> items) {
        if (items.isEmpty()) return "-";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (!b.isEmpty()) b.append(", ");
            b.append(e.getValue()).append(' ').append(e.getKey());
        }
        return b.toString();
    }
}
