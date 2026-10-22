package com.hexvane.cozytalesranching.houseui;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Text for the page without the server's {@code Message} type, so the layout can be built and checked in unit tests.
 * Either a lang key with parameters (strings, numbers or nested {@code Txt}) or raw player data such as a name.
 */
public record Txt(@Nullable String key, @Nonnull Map<String, Object> params, @Nullable String raw) {
    public static final String LANG = "cozyranch_houseui.";

    @Nonnull
    public static Txt key(@Nonnull String fullKey) {
        return new Txt(fullKey, new LinkedHashMap<>(), null);
    }

    /** A key of this role's lang file, {@code cozyranch_houseui.<key>}. */
    @Nonnull
    public static Txt ui(@Nonnull String key) {
        return key(LANG + key);
    }

    @Nonnull
    public static Txt raw(@Nonnull String text) {
        return new Txt(null, Map.of(), text);
    }

    @Nonnull
    public Txt with(@Nonnull String name, @Nonnull Object value) {
        params.put(name, value);
        return this;
    }
}
