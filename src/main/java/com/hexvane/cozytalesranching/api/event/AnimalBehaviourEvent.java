package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** An owned animal did a special behaviour (mud roll, dust bath, lily hop, playing with a toy...). */
public final class AnimalBehaviourEvent extends RanchAnimalEvent {
    /** the behaviour id, e.g. {@code mud_roll}, {@code toy_ball} */
    @Nonnull private final String behaviourId;
    /** the nearest player who could see it, if any */
    @Nullable private final UUID witnessId;

    public AnimalBehaviourEvent(@Nonnull AnimalView animal, @Nonnull String behaviourId, @Nullable UUID witnessId) {
        super(animal);
        this.behaviourId = behaviourId;
        this.witnessId = witnessId;
    }

    @Nonnull public String getBehaviourId() { return behaviourId; }
    @Nullable public UUID getWitnessId() { return witnessId; }
}
