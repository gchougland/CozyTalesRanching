package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The bait-and-calm rules, with no engine types so they can be unit tested.
 *
 * <p>A wild predator goes through three phases, each held only in memory and never longer than its timer:
 * <ul>
 * <li>{@link Phase#LURED}: it smelled bait in a Bait Bowl and walks to it ({@link #LURE_SECONDS} at most).</li>
 * <li>{@link Phase#CALM}: it ate the bait; for the species' CalmMinutes it ignores players and ranch animals and can be
 * fed by hand. Every hand feeding tops the timer back up to a full CalmMinutes.</li>
 * <li>{@link Phase#SPOOKED}: something startled it (a hit, or a player sprinting into it). For {@link #SPOOK_SECONDS} it
 * runs from that player and still never attacks; then it is an ordinary wild animal again.</li>
 * </ul>
 * In all three phases the predator never targets or damages a player or a ranch animal.
 */
public final class CalmRules {
    /** How far a baited bowl is smelled, in blocks. */
    public static final double LURE_RADIUS = 16.0;
    /** A lured predator that has not reached the bowl by then gives up. */
    public static final long LURE_SECONDS = 30;
    /** Close enough to the bowl to eat (horizontal blocks; the walk stops about 2 blocks out). */
    public static final double EAT_DISTANCE = 3.0;
    /** A predator that gave up on a bowl is not lured again for this long. */
    public static final long GAVE_UP_COOLDOWN_SECONDS = 60;
    /** How long a startled predator runs and keeps ignoring players before it is wild again. */
    public static final long SPOOK_SECONDS = 20;
    /** Players near a calm predator are told it grows restless this long before Calm ends. */
    public static final long RESTLESS_WARNING_SECONDS = 20;
    /** A walk that stood still this many one-second checks in a row, restarted each time, is given up on. */
    public static final int STALL_KICKS = 3;

    /** A lured predator whose walk stalled this close to the bowl (horizontal blocks) still gets the bait. */
    public static final double EAT_IN_PLACE_DISTANCE = 10.0;

    /** When the walk times out: close enough that it is calmed where it stands (the bait is still used up). */
    public static boolean closeEnoughToEatInPlace(double dx, double dz) {
        return dx * dx + dz * dz <= EAT_IN_PLACE_DISTANCE * EAT_IN_PLACE_DISTANCE;
    }

    /** A sprinting player this close (blocks) startles a calm predator. */
    public static final double SPRINT_STARTLE_DISTANCE = 2.5;
    /** Players this close to a bowl hear about the predator at it. */
    public static final double TELL_RADIUS = 24.0;

    public enum Phase { LURED, CALM, SPOOKED }

    /** What hand feeding a wild animal may do. */
    public enum FeedGate {
        /** Not a predator: the normal taming rules apply. */
        NORMAL,
        /** A calm predator: normal feeding, and each feeding tops up Calm. */
        CALM_PREDATOR,
        /** A predator that is not calm: only the "too wild, try a Bait Bowl" hint. */
        TOO_WILD
    }

    /** Which filled look a bowl shows for a bait item. */
    public enum BowlLook {
        MEAT("Meat"), FISH("Fish"), BERRIES("Berries");

        public final String state;

        BowlLook(String state) {
            this.state = state;
        }
    }

    private CalmRules() {}

    @Nonnull
    public static FeedGate feedGate(@Nonnull SpeciesDefinition species, @Nullable Phase phase) {
        if (!species.Predator) return FeedGate.NORMAL;
        return phase == Phase.CALM ? FeedGate.CALM_PREDATOR : FeedGate.TOO_WILD;
    }

    /** Whether {@code itemId} is one of this species' baits. Only predators take bait. */
    public static boolean isBait(@Nonnull SpeciesDefinition species, @Nullable String itemId) {
        return species.Predator && itemId != null && species.BaitItems.contains(itemId);
    }

    public static long calmMillis(@Nonnull SpeciesDefinition species) {
        double minutes = species.CalmMinutes > 0 ? species.CalmMinutes : 3.0;
        return Math.max(1_000L, Math.round(minutes * 60_000.0));
    }

    /** When Calm ends if it starts now. */
    public static long calmUntil(long nowMs, @Nonnull SpeciesDefinition species) {
        return nowMs + calmMillis(species);
    }

    /** A hand feeding tops Calm back up to a full CalmMinutes; it never shortens it. */
    public static long extendedUntil(long currentUntilMs, long nowMs, @Nonnull SpeciesDefinition species) {
        return Math.max(currentUntilMs, calmUntil(nowMs, species));
    }

    public static long lureUntil(long nowMs) {
        return nowMs + LURE_SECONDS * 1000L;
    }

    public static long spookUntil(long nowMs) {
        return nowMs + SPOOK_SECONDS * 1000L;
    }

    public static boolean expired(long untilMs, long nowMs) {
        return nowMs >= untilMs;
    }

    /** True once, in the last {@link #RESTLESS_WARNING_SECONDS} of Calm, when Calm lasts longer than the warning. */
    public static boolean warnRestless(@Nonnull Phase phase, long untilMs, long nowMs, boolean alreadyWarned, long calmMs) {
        if (phase != Phase.CALM || alreadyWarned || calmMs <= RESTLESS_WARNING_SECONDS * 2000L) return false;
        long left = untilMs - nowMs;
        return left > 0 && left <= RESTLESS_WARNING_SECONDS * 1000L;
    }

    /** A lured predator that got this close to the bowl eats. */
    public static boolean closeEnoughToEat(double dx, double dz) {
        return dx * dx + dz * dz <= EAT_DISTANCE * EAT_DISTANCE;
    }

    /** A sprinting player bumping into a calm or lured predator startles it; walking and crouching never do. */
    public static boolean sprintStartles(@Nonnull Phase phase, boolean sprinting, double distance) {
        return phase != Phase.SPOOKED && sprinting && distance <= SPRINT_STARTLE_DISTANCE;
    }

    /** A hit from a player startles a predator in any phase but SPOOKED (which it already is). */
    public static boolean hitStartles(@Nonnull Phase phase, boolean fromPlayer) {
        return fromPlayer && phase != Phase.SPOOKED;
    }

    /**
     * Which vanilla state names a predator may sit in during a phase. Anything else is put back by the guard: a calm
     * one goes to Idle, a lured one back to its walk (Search.Investigate), a spooked one to Flee.
     */
    public static boolean stateAllowed(@Nonnull Phase phase, @Nonnull String mainState) {
        return switch (phase) {
            case CALM -> mainState.equals("Idle") || mainState.equals("Sleep");
            case LURED -> mainState.equals("Search");
            case SPOOKED -> mainState.equals("Flee") || mainState.equals("Panic") || mainState.equals("Idle") || mainState.equals("Sleep") || mainState.equals("ReturnHome");
        };
    }

    /** Fish baits show fish, fruit and berries show berries, everything else (meat) shows meat. */
    @Nonnull
    public static BowlLook bowlLook(@Nonnull String itemId) {
        String id = itemId.toLowerCase(Locale.ROOT);
        if (id.contains("fish") || id.contains("salmon") || id.contains("trout") || id.contains("crab")) return BowlLook.FISH;
        if (id.contains("berr") || id.contains("fruit") || id.contains("apple") || id.contains("honey")) return BowlLook.BERRIES;
        return BowlLook.MEAT;
    }
}
