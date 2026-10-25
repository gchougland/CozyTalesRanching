package com.hexvane.cozytalesranching.wildlife;

import com.hexvane.cozytalesranching.wildlife.WildlifeConfig.Rare;
import com.hexvane.cozytalesranching.wildlife.WildlifeConfig.SpeciesWildlife;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The decisions of the wildlife role, free of engine types: whether a house draws its own kind, how strongly, caps, rares, timing. */
public final class WildlifeRules {
    private static final long MINUTE_MS = 60_000L;

    private WildlifeRules() {}

    /**
     * What a pass knows about one house, all from cached data: care's last habitat scan on the record, its residents'
     * happiness, troughs near it and products waiting in it. Nothing here comes from a block scan.
     */
    public record HouseFacts(@Nonnull UUID houseId, @Nonnull UUID ownerId, @Nonnull String speciesId, boolean scanned,
                             boolean requirementsMet, double habitatScore, @Nonnull Set<String> features, int residents,
                             double avgHappiness, int favoriteInTroughs, @Nonnull Map<String, Integer> stored,
                             int lovesMet, int lovesTotal) {
        /** Facts with no loves counted (a species with no HabitatPrefers): the loves rule never holds such a house back. */
        public HouseFacts(@Nonnull UUID houseId, @Nonnull UUID ownerId, @Nonnull String speciesId, boolean scanned,
                          boolean requirementsMet, double habitatScore, @Nonnull Set<String> features, int residents,
                          double avgHappiness, int favoriteInTroughs, @Nonnull Map<String, Integer> stored) {
            this(houseId, ownerId, speciesId, scanned, requirementsMet, habitatScore, features, residents, avgHappiness, favoriteInTroughs, stored, 0, 0);
        }
    }

    /**
     * Gibson's rule (fix 4): a wild animal comes to a house only when MORE than half of the house's loves (its species'
     * {@code HabitatPrefers}) are met there; half is not enough. A species that loves nothing in particular is not held back.
     */
    public static boolean lovesMet(int met, int total) {
        return total <= 0 || met * 2 > total;
    }

    /** How many of {@code loves} are among the present features. */
    public static int countLovesMet(@Nonnull Collection<String> loves, @Nonnull Set<String> present) {
        int n = 0;
        for (String l : new LinkedHashSet<>(loves)) if (present.contains(l)) n++;
        return n;
    }

    /** A species a house attracts now, with its weight (0..1 is a chance per visit, above 1 is certain). */
    public record Candidate(@Nonnull String speciesId, double score) {}

    /** Visitors already here: at this house, of this owner, in this world. */
    public record Counts(int house, int owner, int world) {}

    public enum Cap { OK, HOUSE, OWNER, WORLD }

    /**
     * Features the last scan found: every feature the species' house cares about (required, preferred, toys) that is
     * not listed as missing. Before the first scan nothing is known.
     */
    @Nonnull
    public static Set<String> presentFeatures(boolean scanned, @Nonnull Collection<String> watched, @Nonnull Collection<String> missing) {
        if (!scanned) return Set.of();
        Set<String> out = new LinkedHashSet<>(watched);
        out.removeAll(missing);
        return out;
    }

    /** No residents yet counts as content (an empty house should still draw its first animal); else 0.5..1. */
    public static double happinessFactor(int residents, double avgHappiness) {
        if (residents <= 0) return 1.0;
        return 0.5 + 0.5 * Math.clamp(avgHappiness, 0.0, 1.0);
    }

    public static boolean onCooldown(long lastMs, double cooldownMinutes, long nowMs) {
        return lastMs > 0 && nowMs - lastMs < Math.round(cooldownMinutes * MINUTE_MS);
    }

    /**
     * Gibson's rule (fix 5): a house only ever draws visitors of its own species, normal looks and the rare one alike.
     */
    public static boolean visitsHouse(@Nonnull String visitorSpecies, @Nonnull String houseSpecies) {
        return visitorSpecies.equals(houseSpecies);
    }

    /**
     * How strongly this house draws {@code visitorSpecies}: 0 for another species' house, and 0 unless {@link #lovesMet}
     * holds, the house's requirements are met and its habitat score reaches {@code MinHabitatScore}.
     */
    public static double score(@Nonnull WildlifeConfig config, @Nonnull String visitorSpecies, @Nonnull SpeciesWildlife sw,
                               @Nonnull HouseFacts house, long lastVisitMs, long nowMs) {
        if (!visitsHouse(visitorSpecies, house.speciesId())) return 0;
        if (!house.scanned() || onCooldown(lastVisitMs, sw.CooldownMinutes, nowMs)) return 0;
        if (!lovesMet(house.lovesMet(), house.lovesTotal())) return 0;
        if (!house.requirementsMet() || house.habitatScore() + 1e-6 < config.MinHabitatScore) return 0;
        return Math.max(0, sw.OwnHouseWeight * house.habitatScore()) * happinessFactor(house.residents(), house.avgHappiness());
    }

    /** The house's own species when it draws one now (an empty list or one candidate). */
    @Nonnull
    public static List<Candidate> candidates(@Nonnull WildlifeConfig config, @Nonnull HouseFacts house,
                                             @Nonnull Map<String, Long> lastVisitBySpecies, @Nonnull Predicate<String> speciesEnabled, long nowMs) {
        String id = house.speciesId();
        SpeciesWildlife sw = config.species(id);
        if (sw == null || !speciesEnabled.test(id)) return List.of();
        double s = score(config, id, sw, house, lastVisitBySpecies.getOrDefault(id, 0L), nowMs);
        return s > 0 ? List.of(new Candidate(id, s)) : List.of();
    }

    /**
     * Whether anyone comes this time, and who: a visit happens with the strongest attraction as its chance, and the
     * species is drawn by weight. {@code null} when nobody comes.
     */
    @Nullable
    public static Candidate pick(@Nonnull List<Candidate> candidates, @Nonnull RandomGenerator rng, boolean certain) {
        if (candidates.isEmpty()) return null;
        double max = 0, total = 0;
        for (Candidate c : candidates) {
            max = Math.max(max, c.score());
            total += c.score();
        }
        if (!certain && rng.nextDouble() >= Math.min(1.0, max)) return null;
        double roll = rng.nextDouble() * total;
        for (Candidate c : candidates) {
            roll -= c.score();
            if (roll < 0) return c;
        }
        return candidates.getLast();
    }

    @Nonnull
    public static Cap caps(@Nonnull WildlifeConfig config, @Nonnull Counts counts) {
        if (counts.world() >= config.MaxPerWorld) return Cap.WORLD;
        if (counts.owner() >= config.MaxPerOwner) return Cap.OWNER;
        if (counts.house() >= config.MaxPerHouse) return Cap.HOUSE;
        return Cap.OK;
    }

    /** Every condition of the rare variant holds at this house (only a house of its own species). */
    public static boolean rareConditionsMet(@Nonnull String visitorSpecies, @Nullable Rare rare, @Nonnull HouseFacts house) {
        if (rare == null || rare.Role.isEmpty() || !visitsHouse(visitorSpecies, house.speciesId()) || !house.scanned()) return false;
        if (!lovesMet(house.lovesMet(), house.lovesTotal())) return false;
        if (!house.features().containsAll(rare.Features)) return false;
        if (house.habitatScore() + 1e-6 < rare.MinHabitatScore) return false;
        if (house.residents() < rare.MinResidents) return false;
        if (rare.MinResidents > 0 && house.avgHappiness() + 1e-6 < rare.MinAvgHappiness) return false;
        if (house.favoriteInTroughs() < rare.FavoriteInTroughs) return false;
        for (Map.Entry<String, Integer> need : rare.StoredItems.entrySet()) {
            if (house.stored().getOrDefault(need.getKey(), 0) < need.getValue()) return false;
        }
        return true;
    }

    /** Which conditions are still missing, for the status command and the notes (ids, not player text). */
    @Nonnull
    public static List<String> rareMissing(@Nonnull Rare rare, @Nonnull HouseFacts house) {
        List<String> out = new ArrayList<>();
        if (!lovesMet(house.lovesMet(), house.lovesTotal())) out.add("loves>" + house.lovesTotal() / 2 + " of " + house.lovesTotal());
        for (String f : rare.Features) if (!house.features().contains(f)) out.add(f);
        if (house.habitatScore() + 1e-6 < rare.MinHabitatScore) out.add("score>=" + rare.MinHabitatScore);
        if (house.residents() < rare.MinResidents) out.add("residents>=" + rare.MinResidents);
        if (rare.MinResidents > 0 && house.avgHappiness() + 1e-6 < rare.MinAvgHappiness) out.add("happiness>=" + rare.MinAvgHappiness);
        if (house.favoriteInTroughs() < rare.FavoriteInTroughs) out.add("favourite in troughs>=" + rare.FavoriteInTroughs);
        for (Map.Entry<String, Integer> need : rare.StoredItems.entrySet()) {
            if (house.stored().getOrDefault(need.getKey(), 0) < need.getValue()) out.add(need.getKey() + ">=" + need.getValue());
        }
        return out;
    }

    /** True when this visitor is the rare one: conditions met, not on its cooldown, and the chance roll. */
    public static boolean rollRare(@Nonnull String visitorSpecies, @Nullable Rare rare, @Nonnull HouseFacts house, long lastRareMs,
                                   long nowMs, @Nonnull RandomGenerator rng) {
        if (!rareConditionsMet(visitorSpecies, rare, house)) return false;
        if (onCooldown(lastRareMs, rare.CooldownMinutes, nowMs)) return false;
        return rng.nextDouble() < rare.Chance;
    }

    /** The role a normal visitor of this species spawns as; null when none of its roles exists. */
    @Nullable
    public static String normalRole(@Nonnull SpeciesWildlife sw, @Nonnull Predicate<String> roleExists, @Nonnull RandomGenerator rng) {
        List<String> usable = sw.Roles.stream().filter(roleExists).toList();
        return usable.isEmpty() ? null : usable.get(rng.nextInt(usable.size()));
    }

    public static long nextVisitDelayMs(@Nonnull WildlifeConfig config, @Nonnull RandomGenerator rng) {
        return between(config.MinVisitMinutes, config.MaxVisitMinutes, rng);
    }

    public static long stayMs(@Nonnull WildlifeConfig config, @Nonnull RandomGenerator rng) {
        return between(config.MinStayMinutes, config.MaxStayMinutes, rng);
    }

    private static long between(double minMinutes, double maxMinutes, @Nonnull RandomGenerator rng) {
        double m = minMinutes + (maxMinutes - minMinutes) * rng.nextDouble();
        return Math.round(m * MINUTE_MS);
    }

    /** What a leaving visitor does now. */
    public enum LeaveStep { WAIT, REMOVE }

    /**
     * A visitor walking off goes when nobody can see it go: no player within {@code HiddenDistance}; or, after
     * {@code LeaveSeconds}, no player within {@code NearPlayerDistance}; or in any case after 4 x {@code LeaveSeconds}.
     */
    @Nonnull
    public static LeaveStep leaving(@Nonnull WildlifeConfig config, long leavingSinceMs, long nowMs, double nearestPlayer) {
        double seconds = (nowMs - leavingSinceMs) / 1000.0;
        if (nearestPlayer > config.HiddenDistance) return LeaveStep.REMOVE;
        if (seconds >= config.LeaveSeconds && nearestPlayer > config.NearPlayerDistance) return LeaveStep.REMOVE;
        if (seconds >= config.LeaveSeconds * 4) return LeaveStep.REMOVE;
        return LeaveStep.WAIT;
    }
}
