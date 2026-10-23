package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.AnimalReleasedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Taming actions the commands share with the interactions. Callers are on the animal's world thread, outside ticks. */
public final class TamingApi {
    private TamingApi() {}

    public static void setFollowing(@Nonnull AnimalRecord record, boolean following, @Nonnull Ref<EntityStore> animal, @Nonnull PlayerRef player, @Nonnull Store<EntityStore> store) {
        AnimalInteractions.setFollowing(record, following, animal, player, store);
    }

    @Nonnull
    public static Message displayName(@Nonnull AnimalRecord record) {
        return AnimalControl.displayName(record);
    }

    /**
     * Fires {@link AnimalReleasedEvent} while the record still exists, removes the record and, when the entity is
     * loaded, gives it back its first wild role. An unloaded animal keeps its ranch role until it is seen again.
     */
    /** Swaps a ranch animal's role next tick (grow up, variants), keeping its UUID and components. World thread, outside ticks. */
    public static boolean swapRole(@Nonnull Ref<EntityStore> animal, @Nonnull String roleId, @Nullable String state, @Nonnull Store<EntityStore> store) {
        return AnimalControl.swapRole(animal, roleId, state, store);
    }

    /** Hearts particles and the Happy animation, when the model has it. */
    public static void cheer(@Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        AnimalControl.cheer(animal, AnimalControl.HEARTS, store);
    }

    /**
     * Text for the animal's nameplate, or null for none. Every place that puts a nameplate on a ranch animal asks
     * this (taming, recovery, house residents), so the {@code ShowNameplates} switch lives in one spot.
     */
    @Nullable
    public static String nameplateText(@Nonnull AnimalRecord record) {
        return record.name != null && !record.name.isEmpty() ? record.name : null;
    }

    /** Opens the naming page for one of the player's animals. World thread of the player, outside ticks. */
    public static void openNamePage(@Nonnull PlayerRef player, @Nonnull UUID animalId) {
        // Filled in by the taming fix wave.
    }

    public static void release(@Nonnull AnimalRecord record, @Nullable Ref<EntityStore> animal, @Nonnull PlayerRef player, @Nonnull Store<EntityStore> store) {
        RanchEvents.fire(new AnimalReleasedEvent(record.view(), player.getUuid()));
        Ranch.animals().remove(record.animalId);
        FollowSystem.forget(record.animalId);
        if (animal == null || !animal.isValid()) return;
        if (store.getComponent(animal, Nameplate.getComponentType()) != null) store.removeComponent(animal, Nameplate.getComponentType());
        Optional<SpeciesDefinition> species = SpeciesRegistry.byId(record.speciesId);
        String currentRole = AnimalControl.roleName(animal, store);
        species.flatMap(s -> AnimalControl.wildRole(s, currentRole, record.baby))
            .ifPresent(role -> AnimalControl.swapRole(animal, role, null, store));
    }
}
