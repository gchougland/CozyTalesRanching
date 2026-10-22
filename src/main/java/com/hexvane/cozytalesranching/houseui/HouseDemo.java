package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Made-up houses for {@code /ranch housepage}: records that live only in this object, never in the registries, and a
 * stand-in house service that changes them, so the page can be opened, clicked and rendered without a placed house.
 */
public final class HouseDemo implements HouseSource {
    public enum Scenario { TYPICAL, FULL, EMPTY, UNCHECKED, MISSING, BREEDINGOFF }

    private final HouseRecord house;
    private final List<AnimalRecord> animals = new ArrayList<>();
    private final Set<UUID> inside = new HashSet<>();
    private final UUID owner;
    private final HouseService service = new DemoService();
    private long clock = 1_700_000_000_000L;

    private HouseDemo(@Nonnull UUID owner, @Nonnull String species, @Nonnull String block, int capacity) {
        this.owner = owner;
        house = new HouseRecord();
        house.houseId = UUID.nameUUIDFromBytes(("demo-house-" + species).getBytes());
        house.ownerId = owner;
        house.speciesId = species;
        house.blockId = block;
        house.worldName = "default";
        house.capacity = capacity;
        house.habitatScannedAtMs = clock;
        house.habitatRequirementsMet = true;
        house.habitatScore = 0.7f;
    }

    @Nullable
    public static Scenario scenario(@Nullable String name) {
        if (name == null || name.isBlank()) return Scenario.TYPICAL;
        try {
            return Scenario.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nonnull
    public static HouseDemo create(@Nonnull Scenario scenario, @Nonnull UUID owner) {
        return switch (scenario) {
            case TYPICAL -> {
                HouseDemo d = new HouseDemo(owner, "cow", "CozyRanch_House_CowBarn", 4);
                d.habitat(true, 0.72f, "open_space");
                d.resident("Buttercup", 4, 0.86f, false, false, "gentle");
                d.resident("Moose", 2, 0.55f, false, true);
                d.resident(null, 1, 0.22f, false, false);
                d.follower("Daisy", 3);
                yield d;
            }
            case FULL -> {
                HouseDemo d = new HouseDemo(owner, "chicken", "CozyRanch_House_ChickenCoop", 6);
                d.house.name = "The Sunny Side Henhouse";
                d.habitat(true, 0.95f);
                d.resident("Princess Featherbottom", 5, 0.92f, false, false, "fluffy", "early_riser");
                d.resident("Sir Reginald Cluckworth", 4, 0.78f, false, true, "golden_feathers", "brave", "loud");
                d.resident("Henrietta Marigold", 3, 0.64f, false, false, "speedy");
                d.resident("Nugget", 2, 0.45f, false, false);
                d.resident("Pepper", 1, 0.3f, false, true, "shy");
                d.resident("Little Pip", 0, 0.8f, true, false, "fluffy");
                d.follower("Waddles", 2);
                yield d;
            }
            case EMPTY -> {
                HouseDemo d = new HouseDemo(owner, "pig", "CozyRanch_House_Pigsty", 4);
                d.habitat(true, 0.6f, "mud");
                yield d;
            }
            case UNCHECKED -> {
                HouseDemo d = new HouseDemo(owner, "frog", "CozyRanch_House_FrogPond", 4);
                d.house.habitatScannedAtMs = 0;
                d.house.habitatRequirementsMet = false;
                d.house.habitatScore = 0;
                d.resident("Ribbit", 3, 0.6f, false, false, "jumpy");
                d.resident(null, 1, 0.5f, false, false);
                d.follower(null, 1);
                yield d;
            }
            case MISSING -> {
                HouseDemo d = new HouseDemo(owner, "frog", "CozyRanch_House_FrogPond", 4);
                d.habitat(false, 0.35f, "lily_pads", "reeds");
                d.resident("Lily", 2, 0.35f, false, false);
                d.resident("Croaker", 4, 0.48f, false, true, "loud");
                yield d;
            }
            case BREEDINGOFF -> {
                HouseDemo d = new HouseDemo(owner, "sheep", "CozyRanch_House_Sheepfold", 4);
                d.house.breedingEnabled = false;
                d.habitat(true, 0.8f, "flowers");
                d.resident("Woolliam", 5, 0.9f, false, false, "extra_fluffy");
                d.resident("Cotton", 3, 0.74f, false, false);
                d.resident("Lambchop", 1, 0.66f, true, true);
                yield d;
            }
        };
    }

    private void habitat(boolean met, float score, @Nonnull String... missing) {
        house.habitatRequirementsMet = met;
        house.habitatScore = score;
        house.habitatMissing = new ArrayList<>(List.of(missing));
    }

    private void resident(@Nullable String name, int hearts, float happiness, boolean baby, boolean isInside, @Nonnull String... traits) {
        AnimalRecord r = animal(name, hearts, happiness, baby, traits);
        r.houseId = house.houseId;
        if (isInside) {
            r.state = AnimalRecord.State.STORED;
            inside.add(r.animalId);
        }
    }

    private void follower(@Nullable String name, int hearts) {
        animal(name, hearts, 0.6f, false).following = true;
    }

    @Nonnull
    private AnimalRecord animal(@Nullable String name, int hearts, float happiness, boolean baby, @Nonnull String... traits) {
        AnimalRecord r = new AnimalRecord();
        r.animalId = UUID.nameUUIDFromBytes(("demo-animal-" + house.speciesId + "-" + animals.size()).getBytes());
        r.ownerId = owner;
        r.speciesId = house.speciesId;
        r.name = name;
        r.hearts = hearts;
        r.happiness = happiness;
        r.baby = baby;
        r.traits = new ArrayList<>(List.of(traits));
        r.worldName = house.worldName;
        r.tamedAtMs = clock += 60_000;
        animals.add(r);
        return r;
    }

    @Nonnull @Override public HouseRecord house() { return house; }

    @Nonnull @Override
    public List<AnimalRecord> residents(@Nonnull HouseRecord h) {
        return animals.stream().filter(r -> h.houseId.equals(r.houseId)).toList();
    }

    @Nonnull @Override
    public List<AnimalRecord> followers(@Nonnull HouseRecord h, @Nonnull UUID player) {
        return animals.stream().filter(r -> HouseSource.isFollower(r, h, player)).toList();
    }

    @Nullable @Override
    public AnimalRecord animal(@Nonnull UUID animalId) {
        return animals.stream().filter(r -> r.animalId.equals(animalId)).findFirst().orElse(null);
    }

    @Nonnull @Override public HouseService service() { return service; }

    /** Stand-in for the house role's service: changes the demo records only, never the world or the registries. */
    private final class DemoService implements HouseService {
        @Nonnull @Override
        public Result assign(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal, @Nonnull HouseRecord h) {
            if (residents(h).size() >= h.capacity) return Result.HOUSE_FULL;
            animal.houseId = h.houseId;
            animal.following = false;
            return Result.OK;
        }

        @Nonnull @Override
        public Result unassign(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal) {
            animal.houseId = null;
            animal.state = AnimalRecord.State.ACTIVE;
            animal.following = true;
            inside.remove(animal.animalId);
            return Result.OK;
        }

        @Nonnull @Override
        public Result rename(@Nonnull PlayerRef actor, @Nonnull HouseRecord h, @Nullable String name) {
            h.name = name;
            return Result.OK;
        }

        @Nonnull @Override
        public Result setBreeding(@Nonnull PlayerRef actor, @Nonnull HouseRecord h, boolean enabled) {
            h.breedingEnabled = enabled;
            return Result.OK;
        }

        @Nonnull @Override
        public Result resummon(@Nonnull PlayerRef actor, @Nonnull AnimalRecord animal) {
            animal.state = AnimalRecord.State.ACTIVE;
            inside.remove(animal.animalId);
            return Result.OK;
        }

        @Override
        public boolean isInside(@Nonnull UUID animalId) {
            return inside.contains(animalId);
        }
    }
}
