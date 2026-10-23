package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.care.CareApi;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Where the page reads a house and its animals and sends its actions: the real registries, or the test command's demo. */
public interface HouseSource {
    /** The house as it is now, or null once it is gone. */
    @Nullable HouseRecord house();

    @Nonnull List<AnimalRecord> residents(@Nonnull HouseRecord house);

    /** The player's own animals of the house's species that follow them and live nowhere yet. */
    @Nonnull List<AnimalRecord> followers(@Nonnull HouseRecord house, @Nonnull UUID player);

    @Nullable AnimalRecord animal(@Nonnull UUID animalId);

    @Nonnull HouseService service();

    /** Item id to count waiting in the house, in display order. */
    @Nonnull Map<String, Integer> stored(@Nonnull HouseRecord house);

    /** World thread. Moves what fits into the player's inventory; returns how many items moved. */
    int collect(@Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house);

    /** The owner's other houses a resident of {@code house} could move to (see {@link HouseView#canMoveTo}). */
    @Nonnull List<HouseRecord> moveTargets(@Nonnull HouseRecord house);

    /** World thread. Opens the Rancher's Journal at the species; false if nothing opened. */
    boolean openJournal(@Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store, @Nonnull String speciesId);

    /** World thread. The chat line for Locate. */
    @Nonnull Txt locate(@Nonnull AnimalRecord animal, @Nonnull Txt name, @Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store);

    /** World thread, outside ticks. Looks around this house again now (habitat, the troughs near it); false if nothing ran. */
    boolean rescan(@Nonnull HouseRecord house, @Nonnull World world);

    /** True for the test command's made-up houses, which stand in no world. */
    default boolean demo() {
        return false;
    }

    /** Selection and picker state the page opens with. */
    @Nonnull
    default HouseView.PageState initialState() {
        return HouseView.PageState.NONE;
    }

    /** Reads {@code Ranch}: the persistent registries and whatever house service, product storage and journal are installed. */
    static HouseSource live(@Nonnull UUID houseId) {
        return new HouseSource() {
            @Nullable @Override
            public HouseRecord house() {
                return Ranch.houses().get(houseId).orElse(null);
            }

            @Nonnull @Override
            public List<AnimalRecord> residents(@Nonnull HouseRecord house) {
                return Ranch.animals().inHouse(house.houseId);
            }

            @Nonnull @Override
            public List<AnimalRecord> followers(@Nonnull HouseRecord house, @Nonnull UUID player) {
                return Ranch.animals().ownedBy(player).stream().filter(r -> isFollower(r, house, player)).toList();
            }

            @Nullable @Override
            public AnimalRecord animal(@Nonnull UUID animalId) {
                return Ranch.animals().get(animalId).orElse(null);
            }

            @Nonnull @Override
            public HouseService service() {
                return Ranch.houseService();
            }

            @Nonnull @Override
            public Map<String, Integer> stored(@Nonnull HouseRecord house) {
                return Ranch.produce().stored(house.houseId);
            }

            @Override
            public int collect(@Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house) {
                return Ranch.produce().collect(player, store, house);
            }

            @Nonnull @Override
            public List<HouseRecord> moveTargets(@Nonnull HouseRecord house) {
                if (house.ownerId == null) return List.of();
                return Ranch.houses().ownedBy(house.ownerId).stream()
                    .filter(h -> HouseView.canMoveTo(house, h, Ranch.animals().inHouse(h.houseId).size()))
                    .toList();
            }

            @Override
            public boolean rescan(@Nonnull HouseRecord house, @Nonnull World world) {
                com.hexvane.cozytalesranching.behaviour.BehaviourFeature.refreshToys(house.houseId);
                return CareApi.rescanHouse(world, house);
            }

            @Override
            public boolean openJournal(@Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store, @Nonnull String speciesId) {
                return Ranch.journal().open(player, store, speciesId);
            }

            @Nonnull @Override
            public Txt locate(@Nonnull AnimalRecord animal, @Nonnull Txt name, @Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store) {
                EntityStore entities = store.getExternalData();
                boolean sameWorld = entities.getWorld().getName().equals(animal.worldName);
                Locate.Fix animalFix = null;
                if (sameWorld && animal.state == AnimalRecord.State.ACTIVE && animal.entityUuid != null) {
                    Ref<EntityStore> ref = entities.getRefFromUUID(animal.entityUuid);
                    TransformComponent t = ref != null && ref.isValid() ? store.getComponent(ref, TransformComponent.getComponentType()) : null;
                    if (t != null) animalFix = fix(t.getPosition(), true);
                }
                if (animalFix == null && (animal.lastX != 0 || animal.lastY != 0 || animal.lastZ != 0)) {
                    animalFix = new Locate.Fix(animal.lastX, animal.lastY, animal.lastZ, false);
                }
                TransformComponent pt = store.getComponent(player, TransformComponent.getComponentType());
                return Locate.describe(name, service().isInside(animal.animalId), animal.state, sameWorld, animalFix,
                    pt == null ? null : fix(pt.getPosition(), true));
            }
        };
    }

    private static Locate.Fix fix(@Nonnull Vector3d p, boolean live) {
        return new Locate.Fix(p.x, p.y, p.z, live);
    }

    static boolean isFollower(@Nonnull AnimalRecord r, @Nonnull HouseRecord house, @Nonnull UUID player) {
        return player.equals(r.ownerId) && r.houseId == null && r.following
            && r.state == AnimalRecord.State.ACTIVE && house.speciesId.equals(r.speciesId);
    }
}
