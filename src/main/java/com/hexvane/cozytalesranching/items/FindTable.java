package com.hexvane.cozytalesranching.items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What a find item (Fox Trinket, Shiny Pebble) holds: weighted entries, each an item id with a count range. Opening
 * one gives exactly one entry, a count between its Min and Max. Pure logic, so it can be tested without a server.
 * Entries with no weight, a blank id, or an id {@code known} rejects are dropped; counts are clamped to 1..64.
 */
public final class FindTable {
    /** No single find gives more than this, whatever the item says. */
    public static final int MAX_COUNT = 64;

    /** One find: an item and how many. */
    public record Find(@Nonnull String itemId, int count) {}

    private record Entry(String id, int weight, int min, int max) {}

    private final List<Entry> entries = new ArrayList<>();
    private final int total;

    public FindTable(@Nullable List<FindEntry> list, @Nonnull Predicate<String> known) {
        int sum = 0;
        if (list != null) {
            for (FindEntry e : list) {
                if (e == null) continue;
                String id = e.item();
                if (id == null || id.isBlank() || e.weight() <= 0 || !known.test(id)) continue;
                int min = Math.max(1, Math.min(MAX_COUNT, e.min()));
                int max = Math.max(min, Math.min(MAX_COUNT, e.max()));
                entries.add(new Entry(id, e.weight(), min, max));
                sum += e.weight();
            }
        }
        this.total = sum;
    }

    public boolean isEmpty() {
        return total <= 0;
    }

    public int size() {
        return entries.size();
    }

    /** One find, or null when the table is empty. */
    @Nullable
    public Find roll(@Nonnull RandomGenerator random) {
        if (total <= 0) return null;
        int r = random.nextInt(total);
        Entry pick = entries.get(entries.size() - 1);
        for (Entry e : entries) {
            r -= e.weight();
            if (r < 0) {
                pick = e;
                break;
            }
        }
        int count = pick.min() == pick.max() ? pick.min() : pick.min() + random.nextInt(pick.max() - pick.min() + 1);
        return new Find(pick.id(), count);
    }
}
