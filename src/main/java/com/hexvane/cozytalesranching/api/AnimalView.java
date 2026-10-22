package com.hexvane.cozytalesranching.api;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A read-only snapshot of one ranch animal, safe to keep after the event that carried it.
 *
 * @param animalId   stable identity; never changes, even when the animal's entity is recreated
 * @param ownerId    owning player, or null for an adopted animal nobody has claimed yet
 * @param speciesId  species id from the species data, e.g. {@code "cow"}
 * @param name       player-given name, or null
 * @param hearts     friendship, 0 to 5
 * @param happiness  current comfort, 0.0 to 1.0
 * @param houseId    assigned house, or null when unassigned
 * @param baby       true until the animal grows up
 * @param traits     inherited trait ids, e.g. {@code "bountiful"}
 * @param worldName  world the animal lives in
 */
public record AnimalView(
    @Nonnull UUID animalId,
    @Nullable UUID ownerId,
    @Nonnull String speciesId,
    @Nullable String name,
    int hearts,
    float happiness,
    @Nullable UUID houseId,
    boolean baby,
    @Nonnull List<String> traits,
    @Nonnull String worldName
) {}
