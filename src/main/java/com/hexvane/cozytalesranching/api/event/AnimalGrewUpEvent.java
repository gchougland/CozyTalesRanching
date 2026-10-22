package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import javax.annotation.Nonnull;

/** A baby grew into an adult. */
public final class AnimalGrewUpEvent extends RanchAnimalEvent {
    public AnimalGrewUpEvent(@Nonnull AnimalView animal) {
        super(animal);
    }
}
