package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import com.hypixel.hytale.event.IEvent;
import javax.annotation.Nonnull;

/**
 * Base of every event about one ranch animal. Fired synchronously on the animal's world thread,
 * after the change is saved in the animal's record. Listeners must not block.
 */
public abstract class RanchAnimalEvent implements IEvent<Void> {
    @Nonnull
    private final AnimalView animal;

    protected RanchAnimalEvent(@Nonnull AnimalView animal) {
        this.animal = animal;
    }

    @Nonnull
    public AnimalView getAnimal() {
        return animal;
    }
}
