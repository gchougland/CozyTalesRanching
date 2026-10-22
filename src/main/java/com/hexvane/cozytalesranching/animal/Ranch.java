package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.api.AnimalView;
import com.hexvane.cozytalesranching.api.RanchApi;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseRegistry;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hexvane.cozytalesranching.house.InMemoryHouseRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Internal service locator. The {@code animal} role installs the persistent registry in its feature's register. */
public final class Ranch {
    private static volatile AnimalRegistry animals = new InMemoryAnimalRegistry();
    private static volatile HouseRegistry houses = new InMemoryHouseRegistry();
    private static volatile HouseService houseService = HouseService.UNAVAILABLE;
    private static volatile HousePageOpener housePages = HousePageOpener.NONE;

    private Ranch() {}

    /** Opens the house page for a player. Installed by the {@code houseui} role; the house role calls it on use. */
    public interface HousePageOpener {
        /** World thread. Returns false if no page could be opened. */
        boolean open(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house);

        HousePageOpener NONE = (p, s, h) -> false;
    }

    @Nonnull public static HouseRegistry houses() { return houses; }
    public static void installHouses(@Nonnull HouseRegistry registry) { houses = registry; }

    @Nonnull public static HouseService houseService() { return houseService; }
    public static void installHouseService(@Nonnull HouseService service) { houseService = service; }

    @Nonnull public static HousePageOpener housePages() { return housePages; }
    public static void installHousePages(@Nonnull HousePageOpener opener) { housePages = opener; }

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
