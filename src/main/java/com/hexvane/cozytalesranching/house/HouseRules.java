package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.house.HouseService.Result;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The house rules that need no engine: the day cycle, who may manage a house, assignment checks, names. */
final class HouseRules {
    /** Day progress (0 = midnight) when residents start walking home; sunset is at 0.8. */
    static final double DUSK = 0.77;
    /** Day progress when residents come out; sunrise is at 0.2. */
    static final double DAWN = 0.23;
    /** A resident walking home that has not arrived by then goes in anyway. */
    static final long GO_HOME_TIMEOUT_MS = 60_000;
    static final double ARRIVE_HORIZONTAL = 1.6;
    static final double ARRIVE_VERTICAL = 2.5;
    /** How close followers must be to the player for the assign gesture. */
    static final double ASSIGN_RADIUS = 8.0;
    static final int MAX_NAME_LENGTH = 24;

    enum Phase { DAY, NIGHT }

    private HouseRules() {}

    @Nonnull
    static Phase phase(double dayProgress) {
        return dayProgress >= DUSK || dayProgress < DAWN ? Phase.NIGHT : Phase.DAY;
    }

    /**
     * The day a night or a morning belongs to: before dawn it is still the previous day's night, and the
     * last morning that has passed is yesterday's.
     */
    static long cycleDay(long epochDay, double dayProgress) {
        return dayProgress < DAWN ? epochDay - 1 : epochDay;
    }

    /** True once per cycle day: the morning of {@code cycleDay} has not been handled yet. */
    static boolean morningDue(long lastMorningDay, long cycleDay) {
        return lastMorningDay < cycleDay;
    }

    static boolean arrived(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= ARRIVE_HORIZONTAL * ARRIVE_HORIZONTAL && Math.abs(dy) <= ARRIVE_VERTICAL;
    }

    static boolean walkTimedOut(long startedMs, long nowMs) {
        return nowMs - startedMs >= GO_HOME_TIMEOUT_MS;
    }

    /** Owner or {@code manageany}; the owner also needs {@code cozyranch.house.manage}. */
    @Nonnull
    static Result checkManage(@Nonnull UUID actor, @Nullable UUID houseOwner, boolean hasManage, boolean hasManageAny) {
        return checkManage(actor, houseOwner, hasManage, hasManageAny, false);
    }

    /**
     * As above, where {@code claimAllows} means the house stands in a land claim whose rules let the actor do
     * this (claims integration): the claim stands in for house ownership; {@code cozyranch.house.manage} is still
     * needed. Outside claims {@code claimAllows} is always false and this is the owner-only rule.
     */
    @Nonnull
    static Result checkManage(@Nonnull UUID actor, @Nullable UUID houseOwner, boolean hasManage, boolean hasManageAny, boolean claimAllows) {
        if (hasManageAny) return Result.OK;
        if (!actor.equals(houseOwner) && !claimAllows) return Result.NOT_OWNER;
        return hasManage ? Result.OK : Result.NO_PERMISSION;
    }

    /**
     * Who may break (pick up) a house block: its owner, {@code manageany}, or, inside a land claim, the claim's
     * owner or anyone the claim lets break blocks there. Breaking never deletes an animal record
     * ({@link HousePlacement#houseBroken}); the house item drops for the breaker as for any block.
     */
    static boolean mayBreak(@Nonnull UUID actor, @Nullable UUID houseOwner, boolean hasManageAny, boolean claimAllows) {
        return hasManageAny || actor.equals(houseOwner) || claimAllows;
    }

    /**
     * Whether an animal may move into a house. An animal only ever lives in a house of its own owner, of
     * its species, with room (an animal already there always fits).
     */
    @Nonnull
    static Result checkAssign(@Nonnull Result manage, @Nullable UUID animalOwner, @Nullable UUID houseOwner,
                              @Nonnull String animalSpecies, @Nonnull String houseSpecies,
                              boolean alreadyResident, int residents, int capacity) {
        if (manage != Result.OK) return manage;
        if (animalOwner == null || !Objects.equals(animalOwner, houseOwner)) return Result.NOT_OWNER;
        if (!animalSpecies.equals(houseSpecies)) return Result.WRONG_SPECIES;
        if (!alreadyResident && residents >= capacity) return Result.HOUSE_FULL;
        return Result.OK;
    }

    /** Trimmed, no control characters, at most {@link #MAX_NAME_LENGTH}; null when nothing is left. */
    @Nullable
    static String cleanName(@Nullable String name) {
        if (name == null) return null;
        String clean = name.replaceAll("\\p{Cntrl}", "").trim();
        if (clean.length() > MAX_NAME_LENGTH) clean = clean.substring(0, MAX_NAME_LENGTH).trim();
        return clean.isEmpty() ? null : clean;
    }
}
