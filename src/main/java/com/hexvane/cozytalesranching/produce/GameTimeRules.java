package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import javax.annotation.Nonnull;

/**
 * Pure rules for the ranch's game-time jumps: no engine calls, so every number is unit-tested.
 *
 * <p>The ranch's timers (product intervals, breeding cooldowns, growing up, meals) run on real minutes, which in normal
 * play are exactly the world's game time: vanilla advances game time at {@code 86400 / (day + night seconds)} game
 * seconds per real second ({@code WorldTimeResource.getSecondsPerTick}), 30 with the default 48-minute day, and a
 * processing bench turns elapsed game time back into real seconds with that same factor ({@code BenchSystems}). What
 * real time misses is game time that jumps: a night slept through ({@code UpdateWorldSlumberSystem} sets the wake-up
 * time in one {@code setGameTime}), {@code /time set}, a day-time sweep. This class finds those jumps: game time that
 * went past the highest game time seen faster than real time, in real-equivalent milliseconds. The ranch then credits
 * the jump on top of real time, so a slept night makes what that night would have made.
 */
public final class GameTimeRules {
    /** A jump shorter than this (real-equivalent ms) is clock noise, not a skip: 10 s is 5 in-game minutes by default. */
    public static final double MIN_JUMP_MS = 10_000;
    /** Leftover excess below {@link #MIN_JUMP_MS} is forgotten after this long, so tick jitter never adds up. */
    public static final long WINDOW_NANOS = 5_000_000_000L;

    private GameTimeRules() {}

    /** One world's view of its game clock. World thread only. */
    public static final class Tracker {
        boolean started;
        long lastRealNanos;
        long windowStartNanos;
        /** The furthest game time seen: going back and forward again never credits the same stretch twice. */
        long highWaterGameMs;
        /** Game time past the high-water mark beyond real time, real-equivalent ms, since the window started. */
        double excessMs;

        public long highWaterGameMs() {
            return highWaterGameMs;
        }
    }

    /**
     * Real-equivalent milliseconds of {@code gameMs} of game time: what a bench does with
     * {@code gameElapsedSeconds / WorldTimeResource.getSecondsPerTick(world)}.
     */
    public static double realEquivalentMs(double gameMs, double gameSecondsPerRealSecond) {
        if (!(gameSecondsPerRealSecond > 0) || !Double.isFinite(gameSecondsPerRealSecond)) return 0;
        return Math.max(0, gameMs) / gameSecondsPerRealSecond;
    }

    /** Game seconds per real second for a world's day and night lengths (vanilla's {@code getSecondsPerTick}). */
    public static double gameSecondsPerRealSecond(int daytimeSeconds, int nighttimeSeconds) {
        int total = daytimeSeconds + nighttimeSeconds;
        return total <= 0 ? 0 : 86_400.0 / total;
    }

    /** The most one jump credits: {@code capGameDays} in-game days in real-equivalent ms. */
    public static double capMs(double capGameDays, int daytimeSeconds, int nighttimeSeconds) {
        int total = Math.max(0, daytimeSeconds + nighttimeSeconds);
        return Math.max(0, capGameDays) * total * 1000.0;
    }

    /**
     * Feeds one look at the clocks. Returns the jump to credit now in real-equivalent ms (0 for none), capped at
     * {@code capMs}. The first look only sets the marks. Game time going back, or standing still (paused), credits
     * nothing and breaks nothing; game time running at its normal rate credits nothing either.
     */
    public static double observe(@Nonnull Tracker t, long realNanos, long gameMs, double gameSecondsPerRealSecond, double capMs) {
        if (!t.started) {
            t.started = true;
            t.lastRealNanos = realNanos;
            t.windowStartNanos = realNanos;
            t.highWaterGameMs = gameMs;
            t.excessMs = 0;
            return 0;
        }
        double realMs = Math.max(0, realNanos - t.lastRealNanos) / 1_000_000.0;
        t.lastRealNanos = realNanos;
        long newGameMs = Math.max(0, gameMs - t.highWaterGameMs);
        t.highWaterGameMs = Math.max(t.highWaterGameMs, gameMs);
        t.excessMs = Math.max(0, t.excessMs + realEquivalentMs(newGameMs, gameSecondsPerRealSecond) - realMs);
        if (t.excessMs >= MIN_JUMP_MS) {
            double jump = Math.min(t.excessMs, Math.max(0, capMs));
            t.excessMs = 0;
            t.windowStartNanos = realNanos;
            return jump;
        }
        if (realNanos - t.windowStartNanos >= WINDOW_NANOS) {
            t.excessMs = 0;
            t.windowStartNanos = realNanos;
        }
        return 0;
    }

    /**
     * Moves an animal's real-time timers back by {@code jumpMs}, as if that much time had passed for it: its breeding
     * cooldown, a baby's growing up and its meal clock. A timer never set stays unset; a feed bonus is left alone (it
     * is a short buff the player watches count down). Returns whether anything changed.
     */
    public static boolean shift(@Nonnull AnimalRecord r, long jumpMs) {
        if (jumpMs <= 0) return false;
        boolean changed = false;
        if (r.lastBredAtMs > 0) {
            r.lastBredAtMs = Math.max(1, r.lastBredAtMs - jumpMs);
            changed = true;
        }
        if (r.baby) {
            long start = r.bornAtMs > 0 ? r.bornAtMs : Math.max(0, r.tamedAtMs);
            if (start > 0) {
                r.bornAtMs = Math.max(1, start - jumpMs);
                changed = true;
            }
        }
        if (r.lastFedAtMs > 0) {
            r.lastFedAtMs = Math.max(1, r.lastFedAtMs - jumpMs);
            changed = true;
        }
        return changed;
    }
}
