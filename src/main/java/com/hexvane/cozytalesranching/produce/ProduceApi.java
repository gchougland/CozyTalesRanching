package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Read methods for the house page (and anyone else) beyond {@link Ranch#produce()}. Thread-safe except
 * {@link #collect}, which is world thread like {@link Ranch.ProduceStorage#collect}.
 */
public final class ProduceApi {
    private ProduceApi() {}

    /** Item id to count waiting in the house, in display order. Same as {@code Ranch.produce().stored(houseId)}. */
    @Nonnull
    public static Map<String, Integer> stored(@Nonnull UUID houseId) {
        return Ranch.produce().stored(houseId);
    }

    /** Items waiting in the house, all products together. */
    public static int total(@Nonnull UUID houseId) {
        return stored(houseId).values().stream().mapToInt(Integer::intValue).sum();
    }

    /** How many items a house holds before production pauses. */
    public static int cap() {
        return ProduceTuning.get().HouseCap;
    }

    /** True when the house is full and its animals have paused. */
    public static boolean isFull(@Nonnull UUID houseId) {
        return total(houseId) >= cap();
    }

    /** True when collecting this product takes one empty {@link #bucketItem()} per item (cow milk). */
    public static boolean needsBucket(@Nonnull String speciesId, @Nonnull String itemId) {
        return ProduceService.modeOf(speciesId, itemId) == ProduceRules.Mode.BUCKET;
    }

    /** The empty bucket item id ({@code Container_Bucket}). */
    @Nonnull
    public static String bucketItem() {
        return ProduceTuning.get().BucketItem;
    }

    /** Bucket products waiting in the house (they need an empty bucket each to be collected). */
    public static int waitingForBuckets(@Nonnull HouseRecord house) {
        int n = 0;
        for (Map.Entry<String, Integer> e : stored(house.houseId).entrySet()) {
            if (needsBucket(house.speciesId, e.getKey())) n += e.getValue();
        }
        return n;
    }

    /**
     * World thread. Collects like {@code Ranch.produce().collect} and says what happened: moved, left, how many wait
     * for a bucket, or denied. Null result fields never; an uninstalled feature reports denied.
     */
    @Nonnull
    public static ProduceService.CollectResult collect(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house) {
        if (Ranch.produce() instanceof ProduceService service) return service.collectDetailed(playerRef, store, house);
        return ProduceService.CollectResult.DENIED;
    }
}
