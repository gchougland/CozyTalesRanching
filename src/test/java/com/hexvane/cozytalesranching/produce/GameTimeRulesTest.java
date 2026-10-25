package com.hexvane.cozytalesranching.produce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class GameTimeRulesTest {
    /** Vanilla's default: 28.8 min of day and 19.2 min of night (WorldConfig.DEFAULT_TOTAL_DAY_DURATION_SECONDS = 2880). */
    private static final int DAY = 1728, NIGHT = 1152;
    private static final double SPEED = GameTimeRules.gameSecondsPerRealSecond(DAY, NIGHT);
    private static final double CAP = GameTimeRules.capMs(3, DAY, NIGHT);
    private static final long TICK_NANOS = 33_333_333L;
    private static final long HOUR = 3_600_000L;

    /** A world clock that advances at the normal rate per tick. */
    private static final class Clock {
        final GameTimeRules.Tracker t = new GameTimeRules.Tracker();
        long real = 1_000_000_000L;
        double game = 1_000_000_000_000.0; // game ms
        double credited;

        double look() {
            double j = GameTimeRules.observe(t, real, (long) game, SPEED, CAP);
            credited += j;
            return j;
        }

        void run(double seconds, double rate) {
            long ticks = Math.round(seconds * 30);
            for (long i = 0; i < ticks; i++) {
                real += TICK_NANOS;
                game += TICK_NANOS / 1_000_000.0 * rate;
                look();
            }
        }
    }

    private static final RandomGenerator NO_LUCK = new RandomGenerator() {
        @Override public long nextLong() { return Long.MAX_VALUE; }
        @Override public double nextDouble() { return 0.999999; }
    };

    @Test
    void vanillaNumbers() {
        assertEquals(30.0, SPEED, 1e-9);
        // 3 in-game days = 144 real-equivalent minutes with the default day
        assertEquals(144 * 60_000.0, CAP, 1e-6);
        // a bench's conversion: 30 game minutes are one real minute
        assertEquals(60_000.0, GameTimeRules.realEquivalentMs(30 * 60_000.0, SPEED), 1e-9);
        assertEquals(0.0, GameTimeRules.realEquivalentMs(1000, 0), 0);
        assertEquals(0.0, GameTimeRules.gameSecondsPerRealSecond(0, 0), 0);
    }

    @Test
    void firstLookOnlySetsTheMarks() {
        Clock c = new Clock();
        assertEquals(0, c.look());
        assertEquals((long) c.game, c.t.highWaterGameMs());
        c.real += TICK_NANOS;
        c.game += TICK_NANOS / 1_000_000.0 * SPEED;
        assertEquals(0, c.look());
    }

    @Test
    void normalTimeCreditsNothingEvenOverAnHour() {
        Clock c = new Clock();
        c.look();
        c.run(3600, SPEED);
        assertEquals(0, c.credited);
        // jitter: game time a little ahead of real every tick (2%) never adds up to a jump
        c.run(600, SPEED * 1.02);
        assertEquals(0, c.credited);
    }

    @Test
    void aNightSleptThroughIsCreditedOnce() {
        Clock c = new Clock();
        c.look();
        c.run(10, SPEED);
        // vanilla sleep: 3 real seconds of slumber, then setGameTime from 21:30 to the 06:00 wake-up
        c.run(3, SPEED);
        c.game += 8.5 * HOUR;
        c.real += TICK_NANOS;
        double jump = c.look();
        // 8.5 game hours = 510 game minutes = 17 real-equivalent minutes, less the one tick of real time
        assertEquals(17 * 60_000.0, jump, 50);
        c.run(60, SPEED);
        assertEquals(jump, c.credited, 0);
    }

    @Test
    void goingBackThenForwardDoesNotCreditTheSameTimeTwice() {
        Clock c = new Clock();
        c.look();
        c.run(5, SPEED);
        c.game -= 6 * HOUR; // an admin sweep back
        c.real += TICK_NANOS;
        assertEquals(0, c.look());
        c.run(5, SPEED);
        assertEquals(0, c.credited);
        c.game += 6 * HOUR; // forward to where it was: nothing new
        c.real += TICK_NANOS;
        assertEquals(0, c.look(), 0);
        c.game += 2 * HOUR; // and two hours beyond it: those two hours (4 real minutes), less the 10 s lived meanwhile
        c.real += TICK_NANOS;
        double j = c.look();
        assertTrue(j > 3.7 * 60_000 && j <= 4 * 60_000, "got " + j);
    }

    @Test
    void pausedTimeCreditsNothing() {
        Clock c = new Clock();
        c.look();
        c.run(120, 0);
        assertEquals(0, c.credited);
        c.run(10, SPEED); // unpaused: still nothing, it is normal time again
        assertEquals(0, c.credited);
    }

    @Test
    void aHugeJumpIsCapped() {
        Clock c = new Clock();
        c.look();
        c.game += 10 * 24 * HOUR;
        c.real += TICK_NANOS;
        assertEquals(CAP, c.look(), 0);
        c.run(10, SPEED);
        assertEquals(CAP, c.credited, 0); // the rest is dropped, not paid out later
    }

    @Test
    void aFastSweepOverManyTicksAddsUp() {
        Clock c = new Clock();
        c.look();
        // a day-time sweep: 12 game hours over 4 real seconds
        c.run(4, 12 * 3600.0 / 4);
        // 12 hours = 24 real-equivalent minutes, less the 4 s lived
        assertEquals(24 * 60_000.0 - 4_000, c.credited, 200);
    }

    @Test
    void shiftMovesTimersButLeavesUnsetOnesAndFeed() {
        AnimalRecord r = new AnimalRecord();
        assertFalse(GameTimeRules.shift(r, 60_000));
        assertEquals(0, r.lastBredAtMs);
        assertEquals(0, r.lastFedAtMs);
        assertEquals(0, r.bornAtMs);

        r.lastBredAtMs = 10_000_000;
        r.lastFedAtMs = 9_000_000;
        r.feedBonus = "premium";
        r.feedBonusUntilMs = 11_000_000;
        assertTrue(GameTimeRules.shift(r, 1_000_000));
        assertEquals(9_000_000, r.lastBredAtMs);
        assertEquals(8_000_000, r.lastFedAtMs);
        assertEquals(11_000_000, r.feedBonusUntilMs);
        assertEquals(0, r.bornAtMs); // not a baby
        assertFalse(GameTimeRules.shift(r, 0));

        AnimalRecord baby = new AnimalRecord();
        baby.baby = true;
        baby.tamedAtMs = 5_000_000; // a tamed wild baby: grows from when it was tamed
        assertTrue(GameTimeRules.shift(baby, 2_000_000));
        assertEquals(3_000_000, baby.bornAtMs);
        assertTrue(GameTimeRules.shift(baby, 99_000_000));
        assertEquals(1, baby.bornAtMs); // never back to "unset"
    }

    @Test
    void aSleptNightMakesWhatTheNightWould() {
        ProduceTuning t = new ProduceTuning().normalise();
        // a happy chicken, one egg per 25 min: awake for a whole day (28.8 min) lays 1 egg, 0.152 of the next
        ProduceRules.Step day = ProduceRules.step(t, 0, 28.8, 25, 1, 1f, 0, 1, 0, 0, 64, NO_LUCK);
        assertEquals(1, day.added());
        // sleeping the night credits its 17 minutes on top, as if it had been lived: 1.832 eggs' worth after one day
        ProduceRules.Step night = ProduceRules.step(t, day.progress(), 17, 25, 1, 1f, 0, 1, 0, 0, 64, NO_LUCK);
        assertEquals(1.832, day.added() + night.added() + night.progress(), 1e-9);
        // a second day and night: 3 eggs, the same as two whole days lived awake (2 x 45.8 min / 25)
        ProduceRules.Step day2 = ProduceRules.step(t, night.progress(), 28.8, 25, 1, 1f, 0, 1, 0, 0, 64, NO_LUCK);
        ProduceRules.Step night2 = ProduceRules.step(t, day2.progress(), 17, 25, 1, 1f, 0, 1, 0, 0, 64, NO_LUCK);
        assertEquals(3, day.added() + night.added() + day2.added() + night2.added());
        // a full house still stops it
        assertEquals(0, ProduceRules.step(t, 0, 144, 25, 1, 1f, 0, 1, 0, 0, 0, NO_LUCK).added());
    }

    @Test
    void breedingCooldownPassesWithTheNight() {
        long now = 100 * HOUR;
        AnimalRecord cow = new AnimalRecord();
        cow.lastBredAtMs = now - 45 * 60_000L; // bred 45 real minutes ago, cooldown 60
        assertFalse(now - cow.lastBredAtMs >= 60 * 60_000L);
        GameTimeRules.shift(cow, 17 * 60_000L); // then slept through the night
        assertTrue(now - cow.lastBredAtMs >= 60 * 60_000L);
    }
}
