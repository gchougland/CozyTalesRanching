package com.hexvane.cozytalesranching.journal;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Text for the journal without the server's {@code Message} type, so pages and chat lines can be built in unit tests.
 * Either a lang key with parameters (strings, numbers or nested {@code Txt}) or raw player data such as a name.
 */
public record Txt(@Nullable String key, @Nonnull Map<String, Object> params, @Nullable String raw) {
    public static final String LANG = "cozyranch_journal.";

    @Nonnull
    public static Txt key(@Nonnull String fullKey) {
        return new Txt(fullKey, new LinkedHashMap<>(), null);
    }

    /** A key of this role's lang file, {@code cozyranch_journal.<key>}. */
    @Nonnull
    public static Txt j(@Nonnull String key) {
        return key(LANG + key);
    }

    @Nonnull
    public static Txt raw(@Nonnull String text) {
        return new Txt(null, Map.of(), text);
    }

    /** The same text built for the house page ({@code houseui.Txt}), e.g. what counts for a habitat feature. */
    @Nonnull
    public static Txt from(@Nonnull com.hexvane.cozytalesranching.houseui.Txt t) {
        if (t.key() == null) return raw(t.raw() == null ? "" : t.raw());
        Txt out = key(t.key());
        t.params().forEach((k, v) -> out.with(k, v instanceof com.hexvane.cozytalesranching.houseui.Txt inner ? from(inner) : v));
        return out;
    }

    @Nonnull
    public Txt with(@Nonnull String name, @Nonnull Object value) {
        params.put(name, value);
        return this;
    }
}
