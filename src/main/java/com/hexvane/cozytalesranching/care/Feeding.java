package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.breeding.BreedingRules;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Advanced feed and care-side traits applied to a record. Callers mark the record dirty. World thread. */
final class Feeding {
    private Feeding() {}

    /** Eating an advanced feed: its bonus replaces any running one; Breeder's Feed also moves the cooldown on once. */
    static boolean apply(@Nonnull AnimalRecord record, @Nonnull String itemId, @Nonnull HabitatConfig config, long nowMs) {
        String bonus = config.feedBonus(itemId);
        if (bonus == null) return false;
        long cooldown = SpeciesRegistry.byId(record.speciesId)
            .map(s -> BreedingRules.cooldownMs(record, s, TraitRegistry.get())).orElse(0L);
        apply(record, bonus, config.feedDurationMs(itemId), cooldown, config.FeedEffects.BreedersCooldownCut, nowMs);
        return true;
    }

    static void apply(@Nonnull AnimalRecord record, @Nonnull String bonus, long durationMs, long cooldownMs, double cut, long nowMs) {
        record.feedBonus = bonus;
        record.feedBonusUntilMs = nowMs + Math.max(0L, durationMs);
        if (CareRules.BONUS_BREEDERS.equals(bonus)) record.lastBredAtMs = CareRules.breedersCut(record.lastBredAtMs, nowMs, cooldownMs, cut);
    }

    /** Clears a bonus that has worn off. Returns true if the record changed. */
    static boolean expire(@Nonnull AnimalRecord record, long nowMs) {
        if (record.feedBonus == null || CareRules.bonusActive(record.feedBonus, record.feedBonusUntilMs, nowMs)) return false;
        record.feedBonus = null;
        record.feedBonusUntilMs = 0L;
        return true;
    }

    static boolean active(@Nonnull AnimalRecord record, @Nullable String bonus, long nowMs) {
        return CareRules.bonusActive(record.feedBonus, record.feedBonusUntilMs, nowMs) && (bonus == null || bonus.equals(record.feedBonus));
    }

    @Nonnull
    static CareTraits traits(@Nonnull AnimalRecord record, @Nonnull HabitatConfig config) {
        return CareTraits.of(TraitRegistry.get(), record.traits == null ? List.of() : record.traits, config.DeclineTraitFloor);
    }

    static float friendshipMultiplier(@Nonnull AnimalRecord record, @Nonnull HabitatConfig config, long nowMs) {
        return CareRules.friendshipMultiplier(traits(record, config).friendship(), active(record, CareRules.BONUS_SWEET, nowMs),
            config.FeedEffects.SweetFriendshipMultiplier);
    }
}
