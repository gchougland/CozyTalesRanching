package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import javax.annotation.Nonnull;

/** Friendship reached a new whole number of hearts (up or down). */
public final class AnimalFriendshipChangedEvent extends RanchAnimalEvent {
    /** hearts before, 0 to 5 */
    private final int previousHearts;
    /** hearts now, 0 to 5 */
    private final int newHearts;

    public AnimalFriendshipChangedEvent(@Nonnull AnimalView animal, int previousHearts, int newHearts) {
        super(animal);
        this.previousHearts = previousHearts;
        this.newHearts = newHearts;
    }

    public int getPreviousHearts() {
        return previousHearts;
    }

    public int getNewHearts() {
        return newHearts;
    }
}
