package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.api.AnimalView;
import com.hexvane.cozytalesranching.api.RanchApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Internal service locator. The {@code animal} role installs the persistent registry in its feature's register. */
public final class Ranch {
    private static volatile AnimalRegistry animals = new InMemoryAnimalRegistry();

    private Ranch() {}

    @Nonnull
    public static AnimalRegistry animals() {
        return animals;
    }

    public static void installAnimals(@Nonnull AnimalRegistry registry) {
        animals = registry;
        RanchApi.install(new RanchApi.Service() {
            @Nonnull @Override
            public Optional<AnimalView> animal(@Nonnull UUID animalId) {
                return registry.get(animalId).map(AnimalRecord::view);
            }

            @Nonnull @Override
            public List<AnimalView> animalsOwnedBy(@Nonnull UUID playerId) {
                return registry.ownedBy(playerId).stream().map(AnimalRecord::view).toList();
            }

            @Nonnull @Override
            public Optional<UUID> animalIdForEntity(@Nonnull UUID entityUuid) {
                return registry.byEntity(entityUuid).map(r -> r.animalId);
            }
        });
    }
}
