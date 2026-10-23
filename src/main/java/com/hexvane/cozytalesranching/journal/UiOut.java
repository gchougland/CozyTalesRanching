package com.hexvane.cozytalesranching.journal;

import javax.annotation.Nonnull;

/** Where {@link JournalLayout} writes: the server's builders in game, a recorder in tests. */
public interface UiOut {
    void append(@Nonnull String document);

    void append(@Nonnull String container, @Nonnull String document);

    void clear(@Nonnull String selector);

    void set(@Nonnull String selectorAndProperty, boolean value);

    void set(@Nonnull String selectorAndProperty, @Nonnull String value);

    void text(@Nonnull String selector, @Nonnull Txt text);

    /** A button press sends {@code Action} and {@code Arg} back to the page. */
    void click(@Nonnull String selector, @Nonnull String action, @Nonnull String arg);
}
