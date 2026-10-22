package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import javax.annotation.Nonnull;

/** An animal produced a resource (eggs, milk, wool...), collected into its house or dropped. */
public final class AnimalProducedEvent extends RanchAnimalEvent {
    /** the item id produced */
    @Nonnull private final String itemId;
    /** how many */
    private final int quantity;

    public AnimalProducedEvent(@Nonnull AnimalView animal, @Nonnull String itemId, int quantity) {
        super(animal);
        this.itemId = itemId;
        this.quantity = quantity;
    }

    @Nonnull
    public String getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }
}
