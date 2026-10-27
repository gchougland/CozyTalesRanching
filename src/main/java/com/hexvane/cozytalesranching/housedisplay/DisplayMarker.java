package com.hexvane.cozytalesranching.housedisplay;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Marks a house display entity (no data, never saved), so a copy the game brings back later can be found. */
final class DisplayMarker implements Component<EntityStore> {
    static final DisplayMarker INSTANCE = new DisplayMarker();

    private static volatile ComponentType<EntityStore, DisplayMarker> type;

    private DisplayMarker() {}

    static void bindType(@Nonnull ComponentType<EntityStore, DisplayMarker> componentType) {
        type = componentType;
    }

    @Nonnull
    static ComponentType<EntityStore, DisplayMarker> getComponentType() {
        ComponentType<EntityStore, DisplayMarker> t = type;
        if (t == null) throw new IllegalStateException("DisplayMarker is not registered");
        return t;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return INSTANCE;
    }
}
