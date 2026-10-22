package com.hexvane.cozytalesranching.animal;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code CozyRanch_AnimalMarker}: saved with the entity, names the ranch animal this entity is an
 * incarnation of and which incarnation. Only the entity whose generation and UUID match the record is real.
 * Immutable once built; a new incarnation gets a new marker.
 */
public final class AnimalMarker implements Component<EntityStore> {
    public static final String ID = "CozyRanch_AnimalMarker";

    public static final BuilderCodec<AnimalMarker> CODEC = BuilderCodec.builder(AnimalMarker.class, AnimalMarker::new)
        .append(new KeyedCodec<>("AnimalId", Codec.UUID_STRING), (m, v) -> m.animalId = v, m -> m.animalId).add()
        .append(new KeyedCodec<>("Generation", Codec.LONG), (m, v) -> m.generation = v, m -> m.generation).add()
        .build();

    @Nullable private static volatile ComponentType<EntityStore, AnimalMarker> type;

    @Nullable private UUID animalId;
    private long generation;

    private AnimalMarker() {}

    public AnimalMarker(@Nonnull UUID animalId, long generation) {
        this.animalId = animalId;
        this.generation = generation;
    }

    /** Null before the animal feature registered it, so callers can treat "no type" as "no ranch animals". */
    @Nullable
    public static ComponentType<EntityStore, AnimalMarker> type() {
        return type;
    }

    static void bindType(@Nonnull ComponentType<EntityStore, AnimalMarker> componentType) {
        type = componentType;
    }

    /** Null only for a marker saved without an id (corrupt data); such an entity is left alone. */
    @Nullable
    public UUID animalId() {
        return animalId;
    }

    public long generation() {
        return generation;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return this;
    }

    @Override
    public String toString() {
        return "AnimalMarker{" + animalId + " gen " + generation + "}";
    }
}
