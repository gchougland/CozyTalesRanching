package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import java.util.UUID;
import javax.annotation.Nonnull;

/** An owner released an animal back into the wild. It is no longer a ranch animal after this event. */
public final class AnimalReleasedEvent extends RanchAnimalEvent {
    /** the player who released it */
    @Nonnull private final UUID playerId;

    public AnimalReleasedEvent(@Nonnull AnimalView animal, @Nonnull UUID playerId) {
        super(animal);
        this.playerId = playerId;
    }

    @Nonnull
    public UUID getPlayerId() {
        return playerId;
    }
}
