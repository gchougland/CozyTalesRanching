package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Made-up houses for {@code /ranch housepage}: records that live only in this object, never in the registries, and a
 * stand-in house service that changes them, so the page can be opened, clicked and rendered without a placed house.
 * Products, other houses of the owner and the Locate positions are made up too; the journal is the real one.
 */
public final class HouseDemo implements HouseSource {
    public enum Scenario { TYPICAL, FULL, EMPTY, UNCHECKED, MISSING, BREEDINGOFF, PRODUCTS, MILK, MOVE, PERSONALITY, LONG }

    public static final String MILK = "*Container_Bucket_State_Filled_Milk";
    /** Where the demo pretends the player stands, for Locate. */
    static final Locate.Fix PLAYER_AT = new Locate.Fix(0, 64, 0, true);

    private final HouseRecord house;
    private final List<HouseRecord> others = new ArrayList<>();
    private final List<AnimalRecord> animals = new ArrayList<>();
    private final Set<UUID> inside = new HashSet<>();
    private final Map<String, Integer> stored = new LinkedHashMap<>();
    private final UUID owner;
    private final HouseService service = new DemoService();
    private HouseView.PageState initial = HouseView.PageState.NONE;
    private long clock = 1_700_000_000_000L;

    private HouseDemo(@Nonnull UUID owner, @Nonnull String species, @Nonnull String block, int capacity) {
        this.owner = owner;
        house = newHouse("demo-house-" + species, species, block, capacity);
        house.habitatScannedAtMs = clock;
        house.habitatRequirementsMet = true;
        house.habitatScore = 0.7f;
    }

    @Nonnull
    private HouseRecord newHouse(@Nonnull String seed, @Nonnull String species, @Nonnull String block, int capacity) {
        HouseRecord h = new HouseRecord();
        h.houseId = UUID.nameUUIDFromBytes(seed.getBytes());
        h.ownerId = owner;
        h.speciesId = species;
        h.blockId = block;
        h.worldName = "default";
        h.capacity = capacity;
        return h;
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
                d.product("Food_Egg", 12);
                d.product("Ingredient_Feathers_Light", 3);
                d.coops();
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
                d.product("Ingredient_Fabric_Scrap_Wool", 6);
                yield d;
            }
            case PRODUCTS -> {
                HouseDemo d = new HouseDemo(owner, "chicken", "CozyRanch_House_ChickenCoop", 6);
                d.house.name = "Egg Central";
                d.habitat(true, 0.9f);
                d.resident("Clucky", 5, 0.95f, false, false, "bountiful");
                d.resident("Omelette", 4, 0.82f, false, false, "lucky");
                d.resident("Drumstick", 3, 0.7f, false, true);
                d.product("Food_Egg", 1250);
                d.product("Ingredient_Feathers_Light", 87);
                d.product("Ingredient_Fabric_Scrap_Wool", 14);
                d.product("Food_Cheese", 9);
                d.product("Ingredient_Hide_Light", 2);
                d.product("Plant_Fruit_Apple", 5);
                d.product("Food_Wildmeat_Raw", 1);
                yield d;
            }
            case MILK -> {
                HouseDemo d = new HouseDemo(owner, "cow", "CozyRanch_House_CowBarn", 4);
                d.habitat(true, 0.88f);
                d.resident("Clover", 5, 0.9f, false, false, "bountiful");
                d.resident("Bessie", 3, 0.75f, false, true);
                d.product(MILK, 3);
                yield d;
            }
            case MOVE -> {
                HouseDemo d = create(Scenario.FULL, owner);
                d.initial = new HouseView.PageState(null, true);
                yield d;
            }
            case PERSONALITY -> {
                HouseDemo d = new HouseDemo(owner, "pig", "CozyRanch_House_Pigsty", 4);
                d.habitat(true, 0.82f, "vegetables");
                d.resident("Truffles", 5, 0.93f, false, false, "lucky").personality("playful").feed("hearty", Long.MAX_VALUE);
                d.resident("Hamlet", 3, 0.66f, false, false, "gourmet", "hardy").personality("shy").feed("premium", Long.MAX_VALUE);
                d.resident("Rosie", 2, 0.5f, false, true).personality("curious").feed("sweet", 1);
                d.resident("Sprout", 1, 0.8f, true, false, "swift_grower").personality("lazy").feed("breeders", Long.MAX_VALUE);
                d.product("Plant_Fruit_Apple", 2);
                yield d;
            }
            case LONG -> {
                HouseDemo d = new HouseDemo(owner, "frog", "CozyRanch_House_FrogPond", 6);
                d.house.name = "Lily Pad Lagoon Paradise";
                d.habitat(false, 0.4f, "lily_pads", "reeds", "tall_grass", "shade");
                d.resident("Bartholomew Ribbitson II", 5, 0.9f, false, false, "affectionate", "efficient_eater")
                    .personality("affectionate").feed("breeders", Long.MAX_VALUE);
                d.resident("Wilhelmina Croakington", 4, 0.3f, true, false, "swift_grower", "twinborn")
                    .personality("curious").feed("premium", Long.MAX_VALUE);
                d.resident("Sir Hops A Lot The Third", 2, 0.5f, false, true, "fertile", "bountiful").personality("playful");
                d.follower("Maximilian Leapfrogger", 4);
                d.product("Ingredient_Hide_Light", 999);
                d.otherHouse("Grand Old Pond Of Frogs", 1, 6, 0, 0);
                d.otherHouse(null, 2, 4, -1820, 3415);
                d.initial = new HouseView.PageState(null, true);
                yield d;
            }
        };
    }

    /** The owner's other chicken coops: one named, one not, one full (left out of the picker). */
    private void coops() {
        otherHouse("Sunrise Roost", 3, 6, 0, 0);
        otherHouse(null, 1, 4, 140, -32);
        otherHouse("The Packed Perch", 4, 4, 0, 0);
    }

    private void otherHouse(@Nullable String name, int residents, int capacity, int x, int z) {
        HouseRecord h = newHouse("demo-house-" + house.speciesId + "-" + others.size(), house.speciesId, house.blockId, capacity);
        h.name = name;
        h.x = x;
        h.z = z;
        others.add(h);
        for (int i = 0; i < residents; i++) animal("Neighbour " + i, 2, 0.6f, false).houseId = h.houseId;
    }

    private void habitat(boolean met, float score, @Nonnull String... missing) {
        house.habitatRequirementsMet = met;
        house.habitatScore = score;
        house.habitatMissing = new ArrayList<>(List.of(missing));
    }

    private void product(@Nonnull String itemId, int count) {
        stored.put(itemId, count);
    }

    @Nonnull
    private Resident resident(@Nullable String name, int hearts, float happiness, boolean baby, boolean isInside, @Nonnull String... traits) {
        AnimalRecord r = animal(name, hearts, happiness, baby, traits);
        r.houseId = house.houseId;
        if (isInside) {
            r.state = AnimalRecord.State.STORED;
            inside.add(r.animalId);
        } else {
            int n = animals.size();
            r.lastX = 6 + 9 * n;
            r.lastY = 64;
            r.lastZ = 4 - 13 * n;
        }
        return new Resident(r);
    }

    /** Lets a scenario add what the behaviour and care roles write to a record. */
    private record Resident(@Nonnull AnimalRecord record) {
        @Nonnull Resident personality(@Nonnull String id) {
            record.personality = id;
            return this;
        }

        @Nonnull Resident feed(@Nonnull String id, long untilMs) {
            record.feedBonus = id;
            record.feedBonusUntilMs = untilMs;
            return this;
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

    @Nonnull @Override public HouseView.PageState initialState() { return initial; }

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

    @Nonnull @Override
    public Map<String, Integer> stored(@Nonnull HouseRecord h) {
        return h.houseId.equals(house.houseId) ? new LinkedHashMap<>(stored) : Map.of();
    }

    /** Hands over everything but milk: the demo player never carries empty buckets. Nothing reaches a real inventory. */
    @Override
    public int collect(@Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord h) {
        int moved = 0;
        for (var it = stored.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (HouseView.needsBucket(e.getKey())) continue;
            moved += e.getValue();
            it.remove();
        }
        return moved;
    }

    @Nonnull @Override
    public List<HouseRecord> moveTargets(@Nonnull HouseRecord h) {
        return others.stream().filter(o -> HouseView.canMoveTo(h, o, residents(o).size())).toList();
    }

    @Override
    public boolean openJournal(@Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store, @Nonnull String speciesId) {
        return Ranch.journal().open(player, store, speciesId);
    }

    @Nonnull @Override
    public Txt locate(@Nonnull AnimalRecord animal, @Nonnull Txt name, @Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store) {
        return locate(animal, name);
    }

    /** Locate against the made-up positions: residents stand a few blocks apart, the player at the origin. */
    @Nonnull
    Txt locate(@Nonnull AnimalRecord animal, @Nonnull Txt name) {
        return Locate.describe(name, inside.contains(animal.animalId), animal.state, true,
            new Locate.Fix(animal.lastX, animal.lastY, animal.lastZ, true), PLAYER_AT);
    }

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
