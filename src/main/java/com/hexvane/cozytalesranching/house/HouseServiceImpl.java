package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.AnimalHouseAssignedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The real {@link HouseService}. Runs on the house's world thread (the page and commands call it there).
 * Every method checks permissions and ownership itself; events fire after the record is saved.
 */
final class HouseServiceImpl implements HouseService {
    /** House owner, or the land claim around the house ({@link HouseAccess}). */
    @Nonnull
    private static Result manage(@Nonnull PlayerRef actor, @Nonnull HouseRecord house) {
        return HouseAccess.manage(actor.getUuid(), house);
    }

    @Nonnull
    private static Result manageAnimal(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal) {
        UUID id = actor.getUuid();
        return HouseRules.checkManage(id, animal.ownerId,
            RanchPermissions.has(id, RanchPermissions.HOUSE_MANAGE), RanchPermissions.has(id, RanchPermissions.ADMIN_MANAGE_ANY));
    }

    @Nonnull @Override
    public Result assign(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal, @Nonnull HouseRecord house) {
        if (Ranch.houses().get(house.houseId).isEmpty() || Ranch.animals().get(animal.animalId).isEmpty()) return Result.NOT_FOUND;
        boolean already = Objects.equals(animal.houseId, house.houseId);
        int residents = Ranch.animals().inHouse(house.houseId).size();
        Result check = HouseRules.checkAssign(manage(actor, house), animal.ownerId, house.ownerId, animal.speciesId, house.speciesId, already, residents, house.capacity);
        if (check != Result.OK || already) return check;
        // moving out of another house needs the right to manage that one too (it may stand in a different claim)
        HouseRecord from = animal.houseId != null ? Ranch.houses().get(animal.houseId).orElse(null) : null;
        if (from != null && (check = manage(actor, from)) != Result.OK) return check;
        UUID previous = animal.houseId;
        animal.houseId = house.houseId;
        animal.following = false;
        Ranch.animals().markDirty(animal);
        World world = Universe.get().getWorld(house.worldName);
        if (world != null && animal.state == AnimalRecord.State.ACTIVE) {
            Ref<EntityStore> ref = HouseResidents.live(world, animal);
            if (ref != null) HouseResidents.sendHome(ref, world.getEntityStore().getStore(), house);
        }
        RanchEvents.fire(new AnimalHouseAssignedEvent(animal.view(), previous, house.houseId));
        return Result.OK;
    }

    @Nonnull @Override
    public Result unassign(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal) {
        if (animal.houseId == null) return Result.NOT_FOUND;
        UUID previous = animal.houseId;
        HouseRecord house = Ranch.houses().get(previous).orElse(null);
        Result check = house != null ? manage(actor, house) : manageAnimal(actor, animal);
        if (check != Result.OK) return check;
        World world = house != null ? Universe.get().getWorld(house.worldName) : null;
        if (animal.state == AnimalRecord.State.STORED) {
            boolean out = house != null && world != null && HouseResidents.bringOut(world, world.getEntityStore().getStore(), house, animal);
            // the door is not loaded: recovery brings it out beside its owner instead
            if (!out) animal.state = AnimalRecord.State.MISSING;
        }
        if (world != null) {
            Ref<EntityStore> ref = HouseResidents.live(world, animal);
            if (ref != null) HouseResidents.releaseHome(ref, world.getEntityStore().getStore());
        }
        HouseDaySystem.forget(animal.animalId);
        animal.houseId = null;
        Ranch.animals().markDirty(animal);
        RanchEvents.fire(new AnimalHouseAssignedEvent(animal.view(), previous, null));
        return Result.OK;
    }

    @Nonnull @Override
    public Result rename(@Nonnull PlayerRef actor, @Nonnull HouseRecord house, @Nullable String name) {
        if (Ranch.houses().get(house.houseId).isEmpty()) return Result.NOT_FOUND;
        Result check = manage(actor, house);
        if (check != Result.OK) return check;
        house.name = HouseRules.cleanName(name);
        Ranch.houses().markDirty(house);
        return Result.OK;
    }

    @Nonnull @Override
    public Result setBreeding(@Nonnull PlayerRef actor, @Nonnull HouseRecord house, boolean enabled) {
        if (Ranch.houses().get(house.houseId).isEmpty()) return Result.NOT_FOUND;
        Result check = manage(actor, house);
        if (check != Result.OK) return check;
        house.breedingEnabled = enabled;
        Ranch.houses().markDirty(house);
        return Result.OK;
    }

    @Nonnull @Override
    public Result resummon(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal) {
        if (animal.houseId == null) return Result.NOT_FOUND;
        HouseRecord house = Ranch.houses().get(animal.houseId).orElse(null);
        if (house == null) return Result.NOT_FOUND;
        Result check = manage(actor, house);
        if (check != Result.OK) return check;
        World world = Universe.get().getWorld(house.worldName);
        if (world == null) return Result.UNAVAILABLE;
        Store<EntityStore> store = world.getEntityStore().getStore();
        HouseDaySystem.forget(animal.animalId);
        return HouseResidents.bringOut(world, store, house, animal) ? Result.OK : Result.UNAVAILABLE;
    }

    @Override
    public boolean isInside(@Nonnull UUID animalId) {
        return Ranch.animals().get(animalId)
            .map(r -> r.state == AnimalRecord.State.STORED && r.houseId != null)
            .orElse(false);
    }
}
