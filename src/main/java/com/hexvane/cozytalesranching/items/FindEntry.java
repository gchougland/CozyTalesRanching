package com.hexvane.cozytalesranching.items;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nullable;

/** One line of a find item's table in asset JSON: {@code { "Item": id, "Weight": 10, "Min": 1, "Max": 2 }}. */
public final class FindEntry {
    public static final BuilderCodec<FindEntry> CODEC = BuilderCodec.builder(FindEntry.class, FindEntry::new)
        .append(new KeyedCodec<>("Item", Codec.STRING), (o, v) -> o.item = v, o -> o.item)
        .documentation("Item id given. Ids that are not items are skipped.")
        .add()
        .append(new KeyedCodec<>("Weight", Codec.INTEGER), (o, v) -> o.weight = v, o -> o.weight)
        .documentation("Relative chance of this find (default 1).")
        .add()
        .append(new KeyedCodec<>("Min", Codec.INTEGER), (o, v) -> o.min = v, o -> o.min)
        .documentation("Fewest given (default 1).")
        .add()
        .append(new KeyedCodec<>("Max", Codec.INTEGER), (o, v) -> o.max = v, o -> o.max)
        .documentation("Most given (default Min).")
        .add()
        .build();

    private String item;
    private Integer weight = 1;
    private Integer min = 1;
    private Integer max;

    FindEntry() {}

    public FindEntry(String item, int weight, int min, int max) {
        this.item = item;
        this.weight = weight;
        this.min = min;
        this.max = max;
    }

    @Nullable
    public String item() {
        return item;
    }

    public int weight() {
        return weight == null ? 1 : weight;
    }

    public int min() {
        return min == null ? 1 : min;
    }

    public int max() {
        return max == null ? min() : max;
    }
}
