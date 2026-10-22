package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Two residents of a house bred. Fired once per breeding with every newborn. */
public final class AnimalBredEvent extends RanchAnimalEvent {
    /** the second parent (getAnimal() is the first) */
    @Nonnull private final AnimalView otherParent;
    /** the newborns, already saved */
    @Nonnull private final List<AnimalView> offspring;
    /** the house they bred in */
    @Nonnull private final UUID houseId;

    public AnimalBredEvent(@Nonnull AnimalView animal, @Nonnull AnimalView otherParent, @Nonnull List<AnimalView> offspring, @Nonnull UUID houseId) {
        super(animal);
        this.otherParent = otherParent;
        this.offspring = offspring;
        this.houseId = houseId;
    }

    @Nonnull
    public AnimalView getOtherParent() {
        return otherParent;
    }

    @Nonnull
    public List<AnimalView> getOffspring() {
        return offspring;
    }

    @Nonnull
    public UUID getHouseId() {
        return houseId;
    }
}
