package com.hexvane.cozytalesranching.items;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.houseui.HouseView;
import com.hexvane.cozytalesranching.houseui.Txt;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What the Rancher's Ledger shows, worked out from the records: every house of the reader's with who lives there and
 * what waits in it, then the reader's animals with no house. No server types, so tests can build it.
 */
public record LedgerView(int animals, int houses, int waiting, @Nonnull List<House> houseList, @Nonnull List<Animal> loose, int looseMore) {
    /** Split so check-lang does not read the prefix as a key. */
    public static final String LANG = "cozyranch_items" + ".ledger.";
    public static final int MAX_HEARTS = 5;
    /** Houses listed; a ranch with more shows the first ones (most residents first). */
    public static final int MAX_HOUSES = 40;
    /** Animals with no house listed before "and N more". */
    public static final int MAX_LOOSE = 30;

    public enum Where { INSIDE, NEAR_HOME, FOLLOWING, STAYING, LOST }

    public record Animal(@Nonnull Txt name, @Nonnull Txt species, int hearts, boolean baby, @Nonnull Where where) {}

    public record Product(@Nonnull String itemId, int count) {}

    public record House(@Nonnull Txt name, @Nonnull Txt species, int residents, int capacity, @Nonnull Txt where,
                        @Nonnull List<Product> products, @Nonnull List<Animal> animals) {}

    public static Txt t(@Nonnull String key) {
        return Txt.key(LANG + key);
    }

    /**
     * @param houses       the reader's houses
     * @param animals      the reader's animals (any state)
     * @param inside       whether an animal is inside its house right now
     * @param stored       products waiting per house, item id to count
     * @param species      species definition by id, or null
     * @param houseNameKey the house item's own name key (e.g. Chicken Coop), or null
     * @param viewerWorld  the reader's world: houses elsewhere name their world
     */
    @Nonnull
    public static LedgerView of(@Nonnull List<HouseRecord> houses, @Nonnull List<AnimalRecord> animals, @Nonnull Predicate<UUID> inside,
                                @Nonnull Function<UUID, Map<String, Integer>> stored, @Nonnull Function<String, SpeciesDefinition> species,
                                @Nonnull Function<HouseRecord, String> houseNameKey, @Nonnull String viewerWorld) {
        List<House> rows = new ArrayList<>();
        int waiting = 0;
        List<HouseRecord> sortedHouses = houses.stream()
            .sorted(Comparator.comparing((HouseRecord h) -> h.speciesId).thenComparingLong(h -> h.placedAtMs).thenComparing(h -> h.houseId))
            .toList();
        for (HouseRecord h : sortedHouses) {
            SpeciesDefinition sp = species.apply(h.speciesId);
            Txt speciesName = speciesName(sp, h.speciesId);
            List<AnimalRecord> residents = animals.stream().filter(a -> h.houseId.equals(a.houseId)).toList();
            List<Animal> rowsIn = sorted(residents).stream().map(a -> {
                boolean in = inside.test(a.animalId) || a.state == AnimalRecord.State.STORED;
                Where where = a.state == AnimalRecord.State.MISSING ? Where.LOST : in ? Where.INSIDE : Where.NEAR_HOME;
                return animal(a, speciesName, where);
            }).toList();
            List<Product> products = new ArrayList<>();
            Map<String, Integer> s = stored.apply(h.houseId);
            if (s != null) {
                for (Map.Entry<String, Integer> e : s.entrySet()) {
                    if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() <= 0) continue;
                    products.add(new Product(e.getKey(), e.getValue()));
                    waiting += e.getValue();
                }
            }
            Txt where = h.worldName.equals(viewerWorld)
                ? t("house.at").with("x", h.x).with("z", h.z)
                : t("house.at.world").with("x", h.x).with("z", h.z).with("world", h.worldName);
            rows.add(new House(HouseView.houseTitle(h, speciesName, houseNameKey.apply(h)), speciesName, residents.size(),
                Math.max(1, h.capacity), where, List.copyOf(products), rowsIn));
        }
        if (rows.size() > MAX_HOUSES) rows = new ArrayList<>(rows.subList(0, MAX_HOUSES));

        List<Animal> loose = new ArrayList<>();
        List<AnimalRecord> unhoused = sorted(animals.stream().filter(a -> a.houseId == null || houses.stream().noneMatch(h -> h.houseId.equals(a.houseId))).toList());
        for (AnimalRecord a : unhoused) {
            Where where = a.state == AnimalRecord.State.MISSING ? Where.LOST : a.following ? Where.FOLLOWING : Where.STAYING;
            loose.add(animal(a, speciesName(species.apply(a.speciesId), a.speciesId), where));
        }
        int more = Math.max(0, loose.size() - MAX_LOOSE);
        if (more > 0) loose = new ArrayList<>(loose.subList(0, MAX_LOOSE));
        return new LedgerView(animals.size(), houses.size(), waiting, List.copyOf(rows), List.copyOf(loose), more);
    }

    @Nonnull
    private static Animal animal(@Nonnull AnimalRecord a, @Nonnull Txt speciesName, @Nonnull Where where) {
        Txt name = a.name == null || a.name.isBlank() ? speciesName : Txt.raw(a.name);
        return new Animal(name, speciesName, Math.clamp(a.hearts, 0, MAX_HEARTS), a.baby, where);
    }

    @Nonnull
    static Txt speciesName(@Nullable SpeciesDefinition species, @Nonnull String speciesId) {
        if (species != null && species.NameKey != null && !species.NameKey.isBlank()) return Txt.key(species.NameKey);
        String spaced = speciesId.replace('_', ' ').trim();
        if (spaced.isEmpty()) return Txt.raw(speciesId);
        return Txt.raw(spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1));
    }

    /** Adults first, then by when they were tamed or born, so rows keep their places. */
    @Nonnull
    private static List<AnimalRecord> sorted(@Nonnull List<AnimalRecord> records) {
        return records.stream().sorted(Comparator
            .comparing((AnimalRecord r) -> r.baby)
            .thenComparingLong(r -> Math.max(r.tamedAtMs, r.bornAtMs))
            .thenComparing(r -> r.animalId)).toList();
    }
}
