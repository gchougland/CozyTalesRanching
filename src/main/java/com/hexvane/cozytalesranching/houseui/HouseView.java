package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** What the house page shows, worked out from the records. No server types, so tests can build it. */
public record HouseView(
    @Nonnull Txt title,
    @Nonnull Txt species,
    int residentCount,
    int capacity,
    @Nonnull List<Resident> residents,
    @Nonnull Habitat habitat,
    boolean breeding,
    @Nonnull List<Follower> followers,
    @Nullable String customName,
    @Nullable Txt notice
) {
    public static final int MAX_HEARTS = 5;
    public static final int MAX_TRAITS = 2;
    /** Same limit as animal names ({@code /ranch name}) and the page's text field. */
    public static final int MAX_NAME_LENGTH = 24;
    public static final float HAPPY_AT = 0.7f;
    public static final float CONTENT_AT = 0.4f;
    public static final float HABITAT_GREAT_AT = 0.85f;

    public enum Mood { HAPPY, OKAY, SAD }

    public enum Where { INSIDE, OUTSIDE, LOST }

    public record Resident(@Nonnull UUID id, @Nonnull Txt name, int hearts, @Nonnull Mood mood, @Nonnull List<Txt> traits, boolean baby, @Nonnull Where where) {}

    public record Follower(@Nonnull UUID id, @Nonnull Txt name, int hearts) {}

    public enum HabitatLevel { UNCHECKED, GREAT, GOOD, POOR }

    public record Habitat(@Nonnull HabitatLevel level, @Nonnull List<String> missingRequired, @Nonnull List<String> missingPreferred) {}

    public boolean full() {
        return residentCount >= capacity;
    }

    /**
     * @param houseNameKey lang key of the house item's name, or null to fall back to the species name
     * @param inside       whether a resident is inside its house right now
     */
    @Nonnull
    public static HouseView of(@Nonnull HouseRecord house, @Nullable SpeciesDefinition species, @Nullable String houseNameKey,
                               @Nonnull List<AnimalRecord> residents, @Nonnull Predicate<UUID> inside,
                               @Nonnull List<AnimalRecord> followers, @Nullable Txt notice) {
        Txt speciesName = speciesName(species, house.speciesId);
        String custom = house.name == null || house.name.isBlank() ? null : house.name;
        Txt title = custom != null ? Txt.raw(custom)
            : houseNameKey != null ? Txt.key(houseNameKey)
            : Txt.ui("title.fallback").with("species", speciesName);

        List<Resident> rows = new ArrayList<>();
        for (AnimalRecord r : sorted(residents)) {
            Where where = inside.test(r.animalId) ? Where.INSIDE : r.state == AnimalRecord.State.MISSING ? Where.LOST : Where.OUTSIDE;
            List<Txt> traits = r.traits.stream().filter(t -> t != null && !t.isBlank()).limit(MAX_TRAITS).map(HouseView::traitName).toList();
            rows.add(new Resident(r.animalId, animalName(r, speciesName), hearts(r.hearts), mood(r.happiness), traits, r.baby, where));
        }
        List<Follower> follow = sorted(followers).stream()
            .map(r -> new Follower(r.animalId, animalName(r, speciesName), hearts(r.hearts))).toList();
        int capacity = Math.max(1, house.capacity);
        return new HouseView(title, speciesName, residents.size(), capacity, rows, habitat(house, species),
            house.breedingEnabled, follow, custom, notice);
    }

    @Nonnull
    static Habitat habitat(@Nonnull HouseRecord house, @Nullable SpeciesDefinition species) {
        if (house.habitatScannedAtMs <= 0) return new Habitat(HabitatLevel.UNCHECKED, List.of(), List.of());
        List<String> required = new ArrayList<>(), preferred = new ArrayList<>();
        List<String> requires = species == null ? List.of() : species.HabitatRequires;
        for (String id : house.habitatMissing) {
            if (id == null || id.isBlank() || required.contains(id) || preferred.contains(id)) continue;
            (requires.contains(id) ? required : preferred).add(id);
        }
        HabitatLevel level = !house.habitatRequirementsMet || !required.isEmpty() ? HabitatLevel.POOR
            : house.habitatScore >= HABITAT_GREAT_AT ? HabitatLevel.GREAT : HabitatLevel.GOOD;
        return new Habitat(level, List.copyOf(required), List.copyOf(preferred));
    }

    @Nonnull
    static Mood mood(float happiness) {
        return happiness >= HAPPY_AT ? Mood.HAPPY : happiness >= CONTENT_AT ? Mood.OKAY : Mood.SAD;
    }

    static int hearts(int hearts) {
        return Math.clamp(hearts, 0, MAX_HEARTS);
    }

    @Nonnull
    static Txt speciesName(@Nullable SpeciesDefinition species, @Nonnull String speciesId) {
        if (species != null && !species.NameKey.isBlank()) return Txt.key(species.NameKey);
        return Txt.raw(humanise(speciesId));
    }

    @Nonnull
    static Txt animalName(@Nonnull AnimalRecord r, @Nonnull Txt speciesName) {
        return r.name == null || r.name.isBlank() ? speciesName : Txt.raw(r.name);
    }

    /** Adults first, then by when they were tamed or born, so rows keep their places between renders. */
    @Nonnull
    private static List<AnimalRecord> sorted(@Nonnull List<AnimalRecord> records) {
        return records.stream().sorted(Comparator
            .comparing((AnimalRecord r) -> r.baby)
            .thenComparingLong(r -> Math.max(r.tamedAtMs, r.bornAtMs))
            .thenComparing(r -> r.animalId)).toList();
    }

    /** Trimmed, at most {@link #MAX_NAME_LENGTH} characters, no control characters; empty means "use the default name". */
    @Nullable
    public static String cleanName(@Nullable String raw) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder();
        raw.codePoints().filter(c -> !Character.isISOControl(c)).forEach(out::appendCodePoint);
        String name = out.toString().strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) return null;
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH).strip() : name;
    }

    @Nonnull
    static Txt traitName(@Nonnull String id) {
        return com.hexvane.cozytalesranching.breeding.TraitRegistry.get().get(id)
            .filter(t -> !t.NameKey.isEmpty())
            .map(t -> Txt.key(t.NameKey))
            .orElseGet(() -> Txt.raw(humanise(id)));
    }

    /** {@code open_space} to {@code Open space}: for ids with no trait entry. */
    @Nonnull
    static String humanise(@Nonnull String id) {
        String spaced = id.replace('_', ' ').replace('-', ' ').trim();
        if (spaced.isEmpty()) return id;
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1).toLowerCase(Locale.ROOT);
    }
}
