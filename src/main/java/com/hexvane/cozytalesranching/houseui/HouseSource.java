package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseService;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Where the page reads a house and its animals and sends its actions: the real registries, or the test command's demo. */
public interface HouseSource {
    /** The house as it is now, or null once it is gone. */
    @Nullable HouseRecord house();

    @Nonnull List<AnimalRecord> residents(@Nonnull HouseRecord house);

    /** The player's own animals of the house's species that follow them and live nowhere yet. */
    @Nonnull List<AnimalRecord> followers(@Nonnull HouseRecord house, @Nonnull UUID player);

    @Nullable AnimalRecord animal(@Nonnull UUID animalId);

    @Nonnull HouseService service();

    /** Reads {@code Ranch}: the persistent registries and whatever house service is installed. */
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
        };
    }

    static boolean isFollower(@Nonnull AnimalRecord r, @Nonnull HouseRecord house, @Nonnull UUID player) {
        return player.equals(r.ownerId) && r.houseId == null && r.following
            && r.state == AnimalRecord.State.ACTIVE && house.speciesId.equals(r.speciesId);
    }
}
