package com.hexvane.cozytalesranching.care;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.annotation.Nonnull;

/**
 * What one trough holds: up to {@link #MAX_SLOTS} stacks, any mix of foods. Plain data so it saves with Gson
 * and can be unit tested; the caller passes each item's stack size.
 */
public final class TroughContents {
    public static final int MAX_SLOTS = 4;

    /** One stack. Field names are the save format. */
    public static final class Slot {
        public String item = "";
        public int count;

        public Slot() {}

        public Slot(@Nonnull String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    public List<Slot> slots = new ArrayList<>();

    public boolean isEmpty() {
        return slots.stream().allMatch(s -> s.count <= 0);
    }

    public int count(@Nonnull String item) {
        int n = 0;
        for (Slot s : slots) if (s.item.equals(item)) n += s.count;
        return n;
    }

    /**
     * Puts up to {@code amount} of {@code item} in: tops up stacks of the same item first, then opens new
     * slots while there are fewer than {@link #MAX_SLOTS}. Returns how many went in.
     */
    public int add(@Nonnull String item, int amount, @Nonnull ToIntFunction<String> maxStack) {
        if (amount <= 0 || item.isEmpty()) return 0;
        int cap = Math.max(1, maxStack.applyAsInt(item));
        int left = amount;
        for (Slot s : slots) {
            if (left == 0) break;
            if (!s.item.equals(item) || s.count >= cap) continue;
            int put = Math.min(cap - s.count, left);
            s.count += put;
            left -= put;
        }
        while (left > 0 && slots.size() < MAX_SLOTS) {
            int put = Math.min(cap, left);
            slots.add(new Slot(item, put));
            left -= put;
        }
        return amount - left;
    }

    /** Takes one of {@code item} from its smallest stack. Returns false if there is none. */
    public boolean takeOne(@Nonnull String item) {
        Slot smallest = null;
        for (Slot s : slots) {
            if (s.item.equals(item) && s.count > 0 && (smallest == null || s.count < smallest.count)) smallest = s;
        }
        if (smallest == null) return false;
        smallest.count--;
        if (smallest.count <= 0) slots.remove(smallest);
        return true;
    }

    /** Empties the trough and returns what was in it. */
    @Nonnull
    public List<Slot> takeAll() {
        List<Slot> out = new ArrayList<>();
        for (Slot s : slots) if (s.count > 0) out.add(new Slot(s.item, s.count));
        slots.clear();
        return out;
    }

    @Nonnull
    public TroughContents copy() {
        TroughContents c = new TroughContents();
        for (Slot s : slots) c.slots.add(new Slot(s.item, s.count));
        return c;
    }

    /** Drops empty or malformed slots after a load. */
    @Nonnull
    TroughContents normalise() {
        if (slots == null) slots = new ArrayList<>();
        slots.removeIf(s -> s == null || s.item == null || s.item.isEmpty() || s.count <= 0);
        return this;
    }
}
