package com.hexvane.cozytalesranching.breeding;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The breeding and growing-up rules, free of engine types so every rule is unit tested. */
public final class BreedingRules {
    /** Happiness at or above this counts as good care: babies grow a little faster. */
    public static final double GOOD_CARE_HAPPINESS = 0.8;
    public static final double GOOD_CARE_GROWTH_MULTIPLIER = 0.85;
    /** Hearts a newborn starts with (raised from birth). */
    public static final int BABY_HEARTS = 1;
    private static final long MINUTE_MS = 60_000L;
    /** Happiness is a float and the thresholds doubles: 0.7f is below 0.7 without this. */
    private static final double FLOAT_SLACK = 1e-6;

    private BreedingRules() {}

    public record Settings(boolean serverEnabled, int minHearts, double minHappiness) {}

    /** Why a house did or did not breed this morning. The names are lang key suffixes for the debug command. */
    public enum Outcome {
        BRED, SERVER_DISABLED, HOUSE_DISABLED, NO_OWNER, NO_PERMISSION, UNKNOWN_SPECIES, HABITAT, FULL, NO_PAIR
    }

    public record Plan(@Nonnull Outcome outcome, @Nullable AnimalRecord parentA, @Nullable AnimalRecord parentB, int babies,
                       boolean habitatUnscanned, int eligible) {
        static Plan no(@Nonnull Outcome o, boolean habitatUnscanned, int eligible) {
            return new Plan(o, null, null, 0, habitatUnscanned, eligible);
        }
    }

    /** Why one resident cannot breed; null when it can. Names are lang key suffixes. */
    public enum Block { WRONG_SPECIES, BABY, NOT_INSIDE, OTHER_HOUSE, HEARTS, HAPPINESS, COOLDOWN }

    @Nullable
    public static Block whyNot(@Nonnull AnimalRecord a, @Nonnull HouseRecord house, @Nonnull SpeciesDefinition species,
                               @Nonnull TraitBook book, @Nonnull Settings s, long nowMs) {
        if (!species.Id.equals(a.speciesId)) return Block.WRONG_SPECIES;
        if (a.baby) return Block.BABY;
        if (a.state != AnimalRecord.State.STORED) return Block.NOT_INSIDE;
        if (house.houseId == null || !house.houseId.equals(a.houseId)) return Block.OTHER_HOUSE;
        if (a.hearts < s.minHearts()) return Block.HEARTS;
        if (a.happiness + FLOAT_SLACK < s.minHappiness()) return Block.HAPPINESS;
        if (!cooledDown(a, species, book, nowMs)) return Block.COOLDOWN;
        return null;
    }

    /** Real-time cooldown after breeding: the species' minutes times the animal's own Fertile multiplier. */
    public static long cooldownMs(@Nonnull AnimalRecord a, @Nonnull SpeciesDefinition species, @Nonnull TraitBook book) {
        double minutes = Math.max(0, species.BreedCooldownMinutes) * book.multiplier(a.traits, TraitBook.BREED_COOLDOWN_MULTIPLIER);
        return Math.round(minutes * MINUTE_MS);
    }

    public static boolean cooledDown(@Nonnull AnimalRecord a, @Nonnull SpeciesDefinition species, @Nonnull TraitBook book, long nowMs) {
        return a.lastBredAtMs <= 0 || nowMs - a.lastBredAtMs >= cooldownMs(a, species, book);
    }

    /**
     * The whole morning decision for one house. At most one pair breeds: the two eligible residents that have waited
     * longest. One baby, or twins when a parent is Twinborn, the roll succeeds and two more fit in the house.
     */
    @Nonnull
    public static Plan plan(@Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents, @Nullable SpeciesDefinition species,
                            @Nonnull TraitBook book, @Nonnull Settings s, boolean ownerMayBreed, long nowMs, @Nonnull RandomGenerator rng) {
        boolean unscanned = house.habitatScannedAtMs == 0;
        if (!s.serverEnabled()) return Plan.no(Outcome.SERVER_DISABLED, unscanned, 0);
        if (!house.breedingEnabled) return Plan.no(Outcome.HOUSE_DISABLED, unscanned, 0);
        if (house.ownerId == null) return Plan.no(Outcome.NO_OWNER, unscanned, 0);
        if (!ownerMayBreed) return Plan.no(Outcome.NO_PERMISSION, unscanned, 0);
        if (species == null) return Plan.no(Outcome.UNKNOWN_SPECIES, unscanned, 0);
        if (!unscanned && !house.habitatRequirementsMet) return Plan.no(Outcome.HABITAT, unscanned, 0);
        if (residents.size() >= house.capacity) return Plan.no(Outcome.FULL, unscanned, 0);

        List<AnimalRecord> ready = new ArrayList<>();
        for (AnimalRecord a : residents) {
            if (whyNot(a, house, species, book, s, nowMs) == null) ready.add(a);
        }
        if (ready.size() < 2) return Plan.no(Outcome.NO_PAIR, unscanned, ready.size());
        ready.sort(Comparator.<AnimalRecord>comparingLong(a -> a.lastBredAtMs).thenComparing(a -> String.valueOf(a.animalId)));
        AnimalRecord a = ready.get(0);
        AnimalRecord b = ready.get(1);
        int babies = 1;
        if (residents.size() + 2 <= house.capacity) {
            double twins = book.chance(Stream.concat(a.traits.stream(), b.traits.stream()).toList(), TraitBook.TWIN_CHANCE);
            if (twins > 0 && rng.nextDouble() < twins) babies = 2;
        }
        return new Plan(Outcome.BRED, a, b, babies, unscanned, ready.size());
    }

    /**
     * Each parent trait passes with {@code InheritChance} (a trait both parents have gets two rolls); then the list is
     * cut to {@code MaxTraits} at random; then with {@code MutationChance} a random trait the baby lacks is added (or
     * replaces one at random when the list is full). Unknown trait ids are never passed on.
     */
    @Nonnull
    public static List<String> inheritTraits(@Nonnull List<String> a, @Nonnull List<String> b, @Nonnull TraitBook book, @Nonnull RandomGenerator rng) {
        List<String> out = new ArrayList<>();
        for (List<String> parent : List.of(a, b)) {
            for (String t : parent) {
                if (!book.has(t)) continue;
                if (rng.nextDouble() < book.InheritChance && !out.contains(t)) out.add(t);
            }
        }
        while (out.size() > book.MaxTraits) out.remove(rng.nextInt(out.size()));
        if (book.MaxTraits > 0 && rng.nextDouble() < book.MutationChance) {
            List<String> fresh = book.ids().stream().filter(t -> !out.contains(t)).toList();
            if (!fresh.isEmpty()) {
                String pick = fresh.get(rng.nextInt(fresh.size()));
                if (out.size() >= book.MaxTraits) out.set(rng.nextInt(out.size()), pick);
                else out.add(pick);
            }
        }
        return out;
    }

    /**
     * The role a newborn spawns with. {@link SpeciesDefinition#ranchRoleFor} maps wild roles to ranch variants, but a
     * parent already carries the ranch variant itself (CozyRanch_Frog_Blue), so a variant role is passed on as it is
     * when the species has no baby role.
     */
    @Nonnull
    public static String babyRole(@Nonnull SpeciesDefinition species, @Nullable String parentRole) {
        if (species.RanchBabyRole.isEmpty() && parentRole != null && species.RanchRoleVariants.containsValue(parentRole)) return parentRole;
        return species.ranchRoleFor(parentRole, true);
    }

    /** The adult role for a baby whose role is {@code currentRole}; keeps a colour variant. */
    @Nonnull
    public static String adultRole(@Nonnull SpeciesDefinition species, @Nullable String currentRole) {
        if (currentRole != null && species.RanchRoleVariants.containsValue(currentRole)) return currentRole;
        return species.ranchRoleFor(currentRole, false);
    }

    /** Real-time minutes from birth to adult: the species' minutes, times Swift Grower, times good care. */
    public static long growUpMs(@Nonnull AnimalRecord baby, @Nonnull SpeciesDefinition species, @Nonnull TraitBook book) {
        double minutes = Math.max(0, species.GrowUpMinutes) * book.multiplier(baby.traits, TraitBook.GROW_UP_MULTIPLIER);
        if (baby.happiness + FLOAT_SLACK >= GOOD_CARE_HAPPINESS) minutes *= GOOD_CARE_GROWTH_MULTIPLIER;
        return Math.round(minutes * MINUTE_MS);
    }

    /** When the growth clock started: birth, else when it was tamed; 0 when neither is known. */
    public static long growthStartMs(@Nonnull AnimalRecord baby) {
        return baby.bornAtMs > 0 ? baby.bornAtMs : Math.max(0, baby.tamedAtMs);
    }

    public static boolean isGrown(@Nonnull AnimalRecord baby, @Nonnull SpeciesDefinition species, @Nonnull TraitBook book, long nowMs) {
        long start = growthStartMs(baby);
        return baby.baby && start > 0 && nowMs - start >= growUpMs(baby, species, book);
    }
}
