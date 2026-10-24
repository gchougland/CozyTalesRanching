package com.hexvane.cozytalesranching.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What a Seed Pouch holds: a weighted list of seed item ids and how many seeds come out. Pure logic, so the picks
 * can be tested without a server. Entries with no weight, a blank id, or an id {@code known} rejects are dropped.
 */
public final class SeedTable {
    /** Seeds per pouch when the item does not say. */
    public static final int DEFAULT_ROLLS = 3;
    /** A pouch never gives more than this, whatever the item says. */
    public static final int MAX_ROLLS = 16;

    private final List<String> ids = new ArrayList<>();
    private final List<Integer> weights = new ArrayList<>();
    private final int rolls;
    private final int total;

    public SeedTable(@Nullable Map<String, Integer> seeds, int rolls, @Nonnull Predicate<String> known) {
        int sum = 0;
        if (seeds != null) {
            for (Map.Entry<String, Integer> e : seeds.entrySet()) {
                String id = e.getKey();
                Integer w = e.getValue();
                if (id == null || id.isBlank() || w == null || w <= 0 || !known.test(id)) continue;
                ids.add(id);
                weights.add(w);
                sum += w;
            }
        }
        this.total = sum;
        this.rolls = Math.max(1, Math.min(MAX_ROLLS, rolls <= 0 ? DEFAULT_ROLLS : rolls));
    }

    public boolean isEmpty() {
        return total <= 0;
    }

    public int rolls() {
        return rolls;
    }

    /** One weighted pick, or null when the table is empty. */
    @Nullable
    public String pick(@Nonnull RandomGenerator random) {
        if (total <= 0) return null;
        int r = random.nextInt(total);
        for (int i = 0; i < ids.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return ids.get(i);
        }
        return ids.get(ids.size() - 1);
    }

    /** All the seeds in one pouch, merged by id in the order they first came up. Empty when the table is empty. */
    @Nonnull
    public Map<String, Integer> open(@Nonnull RandomGenerator random) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (total <= 0) return out;
        for (int i = 0; i < rolls; i++) out.merge(pick(random), 1, Integer::sum);
        return out;
    }
}
