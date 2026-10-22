package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * House actions other features call: the house page's buttons, commands. Implemented by the {@code house}
 * role; {@link #UNAVAILABLE} stands in until then. Get it with {@code Ranch.houseService()}. World thread.
 * Every method checks ownership and permissions itself and fires the events the contract names.
 */
public interface HouseService {
    enum Result { OK, NOT_OWNER, NO_PERMISSION, WRONG_SPECIES, HOUSE_FULL, NOT_FOUND, UNAVAILABLE }

    /** Makes {@code animal} live in {@code house} (moving it out of any other house). */
    @Nonnull Result assign(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal, @Nonnull HouseRecord house);

    /** Moves {@code animal} out of its house; a stored animal comes out at the door. */
    @Nonnull Result unassign(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal);

    @Nonnull Result rename(@Nonnull PlayerRef actor, @Nonnull HouseRecord house, @Nullable String name);

    @Nonnull Result setBreeding(@Nonnull PlayerRef actor, @Nonnull HouseRecord house, boolean enabled);

    /** Brings a resident back to the door: from inside, or if lost or far away. */
    @Nonnull Result resummon(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal);

    /** True while the animal is inside its house (record STORED, no entity). */
    boolean isInside(@Nonnull UUID animalId);

    HouseService UNAVAILABLE = new HouseService() {
        @Nonnull @Override public Result assign(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal, @Nonnull HouseRecord house) { return Result.UNAVAILABLE; }
        @Nonnull @Override public Result unassign(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal) { return Result.UNAVAILABLE; }
        @Nonnull @Override public Result rename(@Nonnull PlayerRef actor, @Nonnull HouseRecord house, @Nullable String name) { return Result.UNAVAILABLE; }
        @Nonnull @Override public Result setBreeding(@Nonnull PlayerRef actor, @Nonnull HouseRecord house, boolean enabled) { return Result.UNAVAILABLE; }
        @Nonnull @Override public Result resummon(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal) { return Result.UNAVAILABLE; }
        @Override public boolean isInside(@Nonnull UUID animalId) { return false; }
    };
}
