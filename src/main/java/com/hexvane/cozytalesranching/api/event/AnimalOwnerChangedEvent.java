package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Ownership moved, including the first claim of an adopted animal. */
public final class AnimalOwnerChangedEvent extends RanchAnimalEvent {
    /** null when it had no owner */
    @Nullable private final UUID previousOwnerId;
    /** the new owner */
    @Nonnull private final UUID newOwnerId;

    public AnimalOwnerChangedEvent(@Nonnull AnimalView animal, @Nullable UUID previousOwnerId, @Nonnull UUID newOwnerId) {
        super(animal);
        this.previousOwnerId = previousOwnerId;
        this.newOwnerId = newOwnerId;
    }

    @Nullable
    public UUID getPreviousOwnerId() {
        return previousOwnerId;
    }

    @Nonnull
    public UUID getNewOwnerId() {
        return newOwnerId;
    }
}
