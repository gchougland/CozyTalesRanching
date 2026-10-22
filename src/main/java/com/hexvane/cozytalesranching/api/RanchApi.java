package com.hexvane.cozytalesranching.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Entry point for other mods. Compile against this jar ({@code compileOnly}) and declare
 * {@code "Hexvane:CozyTalesRanching"} as an optional dependency, then listen to the events in
 * {@code com.hexvane.cozytalesranching.api.event} with {@code getEventRegistry().register(...)}.
 * All methods are safe to call from any thread; they read a snapshot.
 */
public final class RanchApi {
    private static volatile Service service = Service.EMPTY;

    private RanchApi() {}

    @Nonnull
    public static Optional<AnimalView> animal(@Nonnull UUID animalId) {
        return service.animal(animalId);
    }

    @Nonnull
    public static List<AnimalView> animalsOwnedBy(@Nonnull UUID playerId) {
        return service.animalsOwnedBy(playerId);
    }

    /** Animal id of a ranch animal's live entity, by the entity's UUID. */
    @Nonnull
    public static Optional<UUID> animalIdForEntity(@Nonnull UUID entityUuid) {
        return service.animalIdForEntity(entityUuid);
    }

    /** True for any entity Cozy Tales: Ranching manages, so other mods can leave it alone. */
    public static boolean isRanchAnimal(@Nonnull UUID entityUuid) {
        return service.animalIdForEntity(entityUuid).isPresent();
    }

    /** Called once by the plugin. Not for other mods. */
    public static void install(@Nonnull Service impl) {
        service = impl;
    }

    public interface Service {
        @Nonnull Optional<AnimalView> animal(@Nonnull UUID animalId);
        @Nonnull List<AnimalView> animalsOwnedBy(@Nonnull UUID playerId);
        @Nonnull Optional<UUID> animalIdForEntity(@Nonnull UUID entityUuid);

        Service EMPTY = new Service() {
            @Nonnull @Override public Optional<AnimalView> animal(@Nonnull UUID animalId) { return Optional.empty(); }
            @Nonnull @Override public List<AnimalView> animalsOwnedBy(@Nonnull UUID playerId) { return List.of(); }
            @Nonnull @Override public Optional<UUID> animalIdForEntity(@Nonnull UUID entityUuid) { return Optional.empty(); }
        };
    }
}
