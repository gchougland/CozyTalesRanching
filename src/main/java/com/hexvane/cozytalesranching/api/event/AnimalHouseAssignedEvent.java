package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** An animal moved into, out of, or between houses. */
public final class AnimalHouseAssignedEvent extends RanchAnimalEvent {
    /** null when it had no house */
    @Nullable private final UUID previousHouseId;
    /** null when it was removed from its house */
    @Nullable private final UUID newHouseId;

    public AnimalHouseAssignedEvent(@Nonnull AnimalView animal, @Nullable UUID previousHouseId, @Nullable UUID newHouseId) {
        super(animal);
        this.previousHouseId = previousHouseId;
        this.newHouseId = newHouseId;
    }

    @Nullable
    public UUID getPreviousHouseId() {
        return previousHouseId;
    }

    @Nullable
    public UUID getNewHouseId() {
        return newHouseId;
    }
}
