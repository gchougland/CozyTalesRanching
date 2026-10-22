package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A ranch animal died or was processed. The record is kept as MISSING and the animal is recovered, unless it was butchered or was an unclaimed adopted animal killed by a player. */
public final class AnimalDiedEvent extends RanchAnimalEvent {
    public enum Cause { KILLED, BUTCHERED, OTHER }

    /** why */
    @Nonnull private final Cause cause;
    /** entity UUID of the killer, when one is known */
    @Nullable private final UUID killerId;

    public AnimalDiedEvent(@Nonnull AnimalView animal, @Nonnull Cause cause, @Nullable UUID killerId) {
        super(animal);
        this.cause = cause;
        this.killerId = killerId;
    }

    @Nonnull
    public Cause getCause() {
        return cause;
    }

    @Nullable
    public UUID getKillerId() {
        return killerId;
    }
}
