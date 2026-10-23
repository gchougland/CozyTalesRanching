package com.hexvane.cozytalesranching.produce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class ProduceRulesTest {
    private static final double EPS = 1e-9;
    private static final ProduceTuning T = new ProduceTuning().normalise();
    /** Never rolls a bonus. */
    private static final RandomGenerator NO_LUCK = new RandomGenerator() {
        @Override public long nextLong() { return Long.MAX_VALUE; }
        @Override public double nextDouble() { return 0.999999; }
    };

    private static ProduceRules.Step step(double minutes, double interval, float happiness, int hearts, double traitMult, double bonus, double premiumMin, int room) {
        return ProduceRules.step(T, 0, minutes, interval, 1, happiness, hearts, traitMult, bonus, premiumMin, room, NO_LUCK);
    }

    @Test
    void happinessScalesTheRateButNeverStopsIt() {
        assertEquals(1.0, ProduceRules.rateFactor(T, 1f, 0, 1, false), EPS);
        assertEquals(0.75, ProduceRules.rateFactor(T, 0.5f, 0, 1, false), EPS);
        assertEquals(0.65, ProduceRules.rateFactor(T, 0.3f, 0, 1, false), 1e-6); // care's floor
        assertEquals(0.5, ProduceRules.rateFactor(T, 0f, 0, 1, false), EPS);
        assertEquals(0.5, ProduceRules.rateFactor(T, Float.NaN, 0, 1, false), EPS);
        // a happy cow makes 3 milk in 120 min at a 40 min interval, an unhappy one 1 (0.65 * 3 = 1.95)
        assertEquals(3, step(120, 40, 1f, 0, 1, 0, 0, 64).added());
        assertEquals(1, step(120, 40, 0.3f, 0, 1, 0, 0, 64).added());
        assertTrue(step(600, 40, 0f, 0, 1, 0, 0, 64).added() > 0);
    }

    @Test
    void minimumRateFactorHolds() {
        ProduceTuning t = new ProduceTuning();
        t.HappinessRateAtZero = 0;
        t.normalise();
        assertEquals(t.MinRateFactor, ProduceRules.rateFactor(t, 0f, 0, 1, false), EPS);
        assertEquals(t.MinRateFactor, ProduceRules.rateFactor(t, 1f, 0, 0.0001, false), EPS);
    }

    @Test
    void heartsAddFivePercentEachUpToFive() {
        assertEquals(1.0, ProduceRules.rateFactor(T, 1f, 0, 1, false), EPS);
        assertEquals(1.15, ProduceRules.rateFactor(T, 1f, 3, 1, false), EPS);
        assertEquals(1.25, ProduceRules.rateFactor(T, 1f, 5, 1, false), EPS);
        assertEquals(1.25, ProduceRules.rateFactor(T, 1f, 9, 1, false), EPS);
        assertEquals(1.0, ProduceRules.rateFactor(T, 1f, -2, 1, false), EPS);
        // 5 hearts: 100 min at a 25 min interval gives 5 eggs instead of 4
        assertEquals(4, step(100, 25, 1f, 0, 1, 0, 0, 64).added());
        assertEquals(5, step(100, 25, 1f, 5, 1, 0, 0, 64).added());
    }

    @Test
    void bountifulMultipliesTheRate() {
        // Traits.json: bountiful ProductionMultiplier 1.25
        assertEquals(1.25, ProduceRules.rateFactor(T, 1f, 0, 1.25, false), EPS);
        assertEquals(4, step(160, 40, 1f, 0, 1, 0, 0, 64).added());
        assertEquals(5, step(160, 40, 1f, 0, 1.25, 0, 0, 64).added());
        // an unknown or zero multiplier counts as none
        assertEquals(1.0, ProduceRules.rateFactor(T, 1f, 0, 0, false), EPS);
        assertEquals(1.0, ProduceRules.rateFactor(T, 1f, 0, Double.NaN, false), EPS);
    }

    @Test
    void luckyRollsOneBonusItemPerUnitAtItsChance() {
        assertEquals(10, ProduceRules.batch(10, 1, 0, new SplittableRandom(1)));
        assertEquals(20, ProduceRules.batch(10, 2, 0, new SplittableRandom(1)));
        assertEquals(20, ProduceRules.batch(10, 1, 1.0, new SplittableRandom(1)));
        assertEquals(0, ProduceRules.batch(0, 3, 1.0, new SplittableRandom(1)));
        // Traits.json: lucky BonusProductChance 0.1 -> about 10% more items
        SplittableRandom rng = new SplittableRandom(42);
        int units = 200_000;
        int total = ProduceRules.batch(units, 1, 0.1, rng);
        double bonusShare = (total - units) / (double) units;
        assertEquals(0.1, bonusShare, 0.005);
    }

    @Test
    void premiumFeedBoostsOnlyWhileItLasts() {
        long now = 10_000_000L;
        assertEquals(0, ProduceRules.premiumMinutes(null, now + 60_000, now, 30), EPS);
        assertEquals(0, ProduceRules.premiumMinutes("hearty", now + 60_000, now, 30), EPS);
        assertEquals(30, ProduceRules.premiumMinutes("premium", now + 60_000, now, 30), EPS);
        // wore off 10 minutes ago: 20 of the last 30 minutes were boosted
        assertEquals(20, ProduceRules.premiumMinutes("premium", now - 10 * 60_000, now, 30), EPS);
        assertEquals(0, ProduceRules.premiumMinutes("premium", now - 40 * 60_000, now, 30), EPS);
        assertEquals(1.2, ProduceRules.rateFactor(T, 1f, 0, 1, true), EPS);
        // 200 min at 40: 5 without, 6 with premium the whole time
        assertEquals(5, step(200, 40, 1f, 0, 1, 0, 0, 64).added());
        assertEquals(6, step(200, 40, 1f, 0, 1, 0, 200, 64).added());
        // premium for half: 100*1/40 + 100*1.2/40 = 5.5
        ProduceRules.Step half = step(200, 40, 1f, 0, 1, 0, 100, 64);
        assertEquals(5, half.added());
        assertEquals(0.5, half.progress(), 1e-6);
    }

    @Test
    void progressCarriesOverBetweenPasses() {
        // 30 s passes at a 16 min interval: an egg after 32 passes
        double p = 0;
        int eggs = 0;
        for (int i = 0; i < 32; i++) {
            ProduceRules.Step s = ProduceRules.step(T, p, 0.5, 16, 1, 1f, 0, 1, 0, 0, 64, NO_LUCK);
            p = s.progress();
            eggs += s.added();
        }
        assertEquals(1, eggs);
        assertEquals(0, p, 1e-6);
    }

    @Test
    void aFullHousePausesAndKeepsProgress() {
        ProduceRules.Step full = ProduceRules.step(T, 0.4, 120, 40, 1, 1f, 0, 1, 0, 0, 0, NO_LUCK);
        assertEquals(0, full.added());
        assertEquals(0.4, full.progress(), EPS);
        // room for 2 of 3: the third waits in progress, so nothing made is lost
        ProduceRules.Step part = ProduceRules.step(T, 0, 120, 40, 1, 1f, 0, 1, 0, 0, 2, NO_LUCK);
        assertEquals(2, part.added());
        assertEquals(1.0, part.progress(), 1e-6);
        assertEquals(0, ProduceRules.room(64, 64));
        assertEquals(0, ProduceRules.room(70, 64));
        assertEquals(60, ProduceRules.room(4, 64));
    }

    @Test
    void catchUpIsCappedAndIgnoresAClockGoingBack() {
        long now = 1_000_000_000L;
        assertEquals(0, ProduceRules.catchUpMinutes(0, now, 480), EPS);
        assertEquals(0, ProduceRules.catchUpMinutes(now + 5000, now, 480), EPS);
        assertEquals(0.5, ProduceRules.catchUpMinutes(now - 30_000, now, 480), EPS);
        assertEquals(120, ProduceRules.catchUpMinutes(now - 120 * 60_000L, now, 480), EPS);
        assertEquals(480, ProduceRules.catchUpMinutes(now - 3 * 24 * 60 * 60_000L, now, 480), EPS);
        // three days off: a happy cow catches up 8 hours = 12 milk, not 108
        double minutes = ProduceRules.catchUpMinutes(now - 3 * 24 * 60 * 60_000L, now, T.CatchUpCapMinutes);
        assertEquals(12, step(minutes, 40, 1f, 0, 1, 0, 0, 64).added());
    }

    @Test
    void eachMilkNeedsAnEmptyBucket() {
        assertEquals(new ProduceRules.BucketPlan(0, 3), ProduceRules.bucketPlan(3, 0));
        assertEquals(new ProduceRules.BucketPlan(2, 1), ProduceRules.bucketPlan(3, 2));
        assertEquals(new ProduceRules.BucketPlan(3, 0), ProduceRules.bucketPlan(3, 5));
        assertEquals(new ProduceRules.BucketPlan(0, 0), ProduceRules.bucketPlan(0, 5));
        assertEquals(new ProduceRules.BucketPlan(0, 0), ProduceRules.bucketPlan(-1, -1));
    }

    @Test
    void collectModesAndWhoProduces() {
        assertEquals(ProduceRules.Mode.HOUSE, ProduceRules.mode("House"));
        assertEquals(ProduceRules.Mode.BUCKET, ProduceRules.mode("bucket"));
        assertEquals(ProduceRules.Mode.NONE, ProduceRules.mode("Harvest"));
        assertEquals(ProduceRules.Mode.NONE, ProduceRules.mode(null));

        AnimalRecord r = new AnimalRecord();
        r.animalId = UUID.randomUUID();
        assertFalse(ProduceRules.produces(r));
        r.houseId = UUID.randomUUID();
        assertTrue(ProduceRules.produces(r));
        r.state = AnimalRecord.State.STORED;
        assertTrue(ProduceRules.produces(r));
        r.state = AnimalRecord.State.MISSING;
        assertFalse(ProduceRules.produces(r));
        r.state = AnimalRecord.State.ACTIVE;
        r.baby = true;
        assertFalse(ProduceRules.produces(r));
    }
}
