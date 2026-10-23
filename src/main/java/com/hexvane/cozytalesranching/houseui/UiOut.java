package com.hexvane.cozytalesranching.houseui;

import javax.annotation.Nonnull;

/** Where {@link HousePageLayout} writes the page: the server's command and event builders, or a test recorder. */
public interface UiOut {
    void append(@Nonnull String document);

    void append(@Nonnull String container, @Nonnull String document);

    void clear(@Nonnull String selector);

    void set(@Nonnull String selectorAndProperty, boolean value);

    void set(@Nonnull String selectorAndProperty, @Nonnull String value);

    /** Sets {@code selector.TextSpans}. */
    void text(@Nonnull String selector, @Nonnull Txt text);

    /** A button press that sends {@code Action} and, when not empty, {@code Animal}. */
    default void click(@Nonnull String selector, @Nonnull String action, @Nonnull String animal) {
        click(selector, action, animal, "");
    }

    /** A button press that sends {@code Action} and, when not empty, {@code Animal} and {@code House}. */
    void click(@Nonnull String selector, @Nonnull String action, @Nonnull String animal, @Nonnull String house);

    /** A button press that also sends the live {@code Value} of a text field as {@code @Name}. */
    void clickWithField(@Nonnull String selector, @Nonnull String action, @Nonnull String fieldSelector);
}
