package com.hexvane.cozytalesranching.houseui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.Map;
import javax.annotation.Nonnull;

/** {@link UiOut} onto the server's builders. */
final class ServerUiOut implements UiOut {
    private final UICommandBuilder commands;
    private final UIEventBuilder events;

    ServerUiOut(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
        this.commands = commands;
        this.events = events;
    }

    @Override public void append(@Nonnull String document) { commands.append(document); }

    @Override public void append(@Nonnull String container, @Nonnull String document) { commands.append(container, document); }

    @Override public void clear(@Nonnull String selector) { commands.clear(selector); }

    @Override public void set(@Nonnull String selectorAndProperty, boolean value) { commands.set(selectorAndProperty, value); }

    @Override public void set(@Nonnull String selectorAndProperty, @Nonnull String value) { commands.set(selectorAndProperty, value); }

    @Override public void text(@Nonnull String selector, @Nonnull Txt text) { commands.set(selector + ".TextSpans", message(text)); }

    @Override
    public void click(@Nonnull String selector, @Nonnull String action, @Nonnull String animal) {
        EventData data = EventData.of("Action", action);
        if (!animal.isEmpty()) data = data.append("Animal", animal);
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
    }

    @Override
    public void clickWithField(@Nonnull String selector, @Nonnull String action, @Nonnull String fieldSelector) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
            EventData.of("Action", action).append("@Name", fieldSelector + ".Value"), false);
    }

    @Nonnull
    static Message message(@Nonnull Txt text) {
        if (text.key() == null) return Message.raw(text.raw() == null ? "" : text.raw());
        Message m = Message.translation(text.key());
        for (Map.Entry<String, Object> p : text.params().entrySet()) {
            Object v = p.getValue();
            switch (v) {
                case Txt t -> m = m.param(p.getKey(), message(t));
                case Integer i -> m = m.param(p.getKey(), i);
                case Long l -> m = m.param(p.getKey(), l);
                case Number n -> m = m.param(p.getKey(), n.doubleValue());
                default -> m = m.param(p.getKey(), String.valueOf(v));
            }
        }
        return m;
    }
}
