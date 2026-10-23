package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    @Nullable Txt notice,
    @Nonnull List<Product> products,
    @Nullable Resident selected,
    boolean picking,
    @Nonnull List<MoveTarget> moveTargets
) {
    public static final int MAX_HEARTS = 5;
    public static final int MAX_TRAITS = 2;
    /** Same limit as animal names ({@code /ranch name}) and the page's text field. */
    public static final int MAX_NAME_LENGTH = 24;
    public static final float HAPPY_AT = 0.7f;
    public static final float CONTENT_AT = 0.4f;
    public static final float HABITAT_GREAT_AT = 0.85f;
    /** Houses the move picker lists, most room first; it has no scrollbar. */
    public static final int MAX_MOVE_TARGETS = 3;
    /** Behaviour's lang file names each personality ({@code cozyranch_behaviour.personality.<id>}); split so check-lang does not ask this role for them. */
    public static final String PERSONALITY_LANG = "cozyranch_behaviour" + ".personality.";
    /** Feed bonuses with a name in this role's lang file ({@code feed.<id>}); any other id is shown humanised. */
    public static final List<String> FEED_BONUSES = List.of("hearty", "sweet", "breeders", "premium");

    public enum Mood { HAPPY, OKAY, SAD }

    public enum Where { INSIDE, OUTSIDE, LOST }

    public record Resident(@Nonnull UUID id, @Nonnull Txt name, int hearts, @Nonnull Mood mood, @Nonnull List<Txt> traits, boolean baby,
                           @Nonnull Where where, @Nullable Txt personality, @Nullable Txt feedBonus) {}

    public record Follower(@Nonnull UUID id, @Nonnull Txt name, int hearts) {}

    public enum HabitatLevel { UNCHECKED, GREAT, GOOD, POOR }

    public record Habitat(@Nonnull HabitatLevel level, @Nonnull List<String> missingRequired, @Nonnull List<String> missingPreferred) {}

    /** An item waiting in the house. */
    public record Product(@Nonnull String itemId, int count) {}

    /** Another house of the owner's, same species and world, with room. */
    public record MoveTarget(@Nonnull UUID houseId, @Nonnull Txt name, int residents, int capacity) {}

    public boolean full() {
        return residentCount >= capacity;
    }

    /** True when milk (or anything else that needs an empty bucket) waits in the house. */
    public boolean bucketsNeeded() {
        return products.stream().anyMatch(p -> needsBucket(p.itemId()));
    }

    /** What the page remembers between builds: the selected resident and whether the move picker is open. */
    public record PageState(@Nullable UUID selected, boolean picking) {
        public static final PageState NONE = new PageState(null, false);
    }

    /**
     * @param houseNameKey lang key of the house item's name, or null to fall back to the species name
     * @param inside       whether a resident is inside its house right now
     * @param stored       item id to count waiting in the house, in display order
     * @param moveTargets  the owner's other houses the selected resident could move to
     */
    @Nonnull
    public static HouseView of(@Nonnull HouseRecord house, @Nullable SpeciesDefinition species, @Nullable String houseNameKey,
                               @Nonnull List<AnimalRecord> residents, @Nonnull Predicate<UUID> inside,
                               @Nonnull List<AnimalRecord> followers, @Nullable Txt notice,
                               @Nonnull Map<String, Integer> stored, @Nonnull PageState state,
                               @Nonnull List<MoveTarget> moveTargets, long nowMs) {
        Txt speciesName = speciesName(species, house.speciesId);
        String custom = house.name == null || house.name.isBlank() ? null : house.name;
        Txt title = houseTitle(house, speciesName, houseNameKey);

        List<Resident> rows = new ArrayList<>();
        for (AnimalRecord r : sorted(residents)) {
            Where where = inside.test(r.animalId) ? Where.INSIDE : r.state == AnimalRecord.State.MISSING ? Where.LOST : Where.OUTSIDE;
            List<Txt> traits = r.traits.stream().filter(t -> t != null && !t.isBlank()).limit(MAX_TRAITS).map(HouseView::traitName).toList();
            rows.add(new Resident(r.animalId, animalName(r, speciesName), hearts(r.hearts), mood(r.happiness), traits, r.baby, where,
                personality(r.personality), feedBonus(r.feedBonus, r.feedBonusUntilMs, nowMs)));
        }
        Resident selected = rows.stream().filter(r -> r.id().equals(state.selected())).findFirst()
            .orElse(rows.isEmpty() ? null : rows.getFirst());
        List<Follower> follow = sorted(followers).stream()
            .map(r -> new Follower(r.animalId, animalName(r, speciesName), hearts(r.hearts))).toList();
        List<Product> products = stored.entrySet().stream()
            .filter(e -> e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null && e.getValue() > 0)
            .map(e -> new Product(e.getKey(), e.getValue())).toList();
        List<MoveTarget> targets = selected == null ? List.of() : moveTargets.stream()
            .sorted(Comparator.comparingInt((MoveTarget t) -> t.residents() - t.capacity()))
            .limit(MAX_MOVE_TARGETS).toList();
        boolean picking = state.picking() && !targets.isEmpty();
        int capacity = Math.max(1, house.capacity);
        return new HouseView(title, speciesName, residents.size(), capacity, rows, habitat(house, species),
            house.breedingEnabled, follow, custom, notice, products, selected, picking, targets);
    }

    /** The house's own name, else its item's name, else "{species} House". */
    @Nonnull
    public static Txt houseTitle(@Nonnull HouseRecord house, @Nonnull Txt speciesName, @Nullable String houseNameKey) {
        if (house.name != null && !house.name.isBlank()) return Txt.raw(house.name);
        if (houseNameKey != null) return Txt.key(houseNameKey);
        return Txt.ui("title.fallback").with("species", speciesName);
    }

    /** A house in the move picker: its own name, else its item's name with where it stands, so two coops differ. */
    @Nonnull
    public static Txt pickerName(@Nonnull HouseRecord house, @Nonnull Txt speciesName, @Nullable String houseNameKey) {
        Txt title = houseTitle(house, speciesName, houseNameKey);
        if (house.name != null && !house.name.isBlank()) return title;
        return Txt.ui("move.house.at").with("name", title).with("x", house.x).with("z", house.z);
    }

    /** Houses an animal of {@code house} could move to: same owner, species and world, not full. */
    public static boolean canMoveTo(@Nonnull HouseRecord from, @Nonnull HouseRecord to, int residentsThere) {
        return !from.houseId.equals(to.houseId) && from.ownerId != null && from.ownerId.equals(to.ownerId)
            && from.speciesId.equals(to.speciesId) && from.worldName.equals(to.worldName)
            && residentsThere < Math.max(1, to.capacity);
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

    @Nullable
    static Txt personality(@Nullable String id) {
        if (id == null || id.isBlank()) return null;
        return Txt.key(PERSONALITY_LANG + id.strip().toLowerCase(Locale.ROOT));
    }

    @Nullable
    static Txt feedBonus(@Nullable String id, long untilMs, long nowMs) {
        if (id == null || id.isBlank() || untilMs <= nowMs) return null;
        String clean = id.strip().toLowerCase(Locale.ROOT);
        return FEED_BONUSES.contains(clean) ? Txt.ui("feed." + clean) : Txt.ui("feed.other").with("name", humanise(clean));
    }

    /** Filled buckets: collecting one turns an empty {@code Container_Bucket} from the player's inventory into it. */
    public static boolean needsBucket(@Nonnull String itemId) {
        return itemId.startsWith("*Container_Bucket_State_Filled");
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
