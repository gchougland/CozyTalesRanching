package com.hexvane.cozytalesranching.predator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * The predator system's slow-pass clocks, one set per world. Every world ticks the same system instance on its own
 * thread, so shared clocks would give the pass to whichever world's tick crossed the period and starve the others.
 * Each world's set is only touched by that world's thread.
 */
final class PassClocks {
    static final float SPRINT_PERIOD = 0.25f;
    static final float TIMER_PERIOD = 1.0f;
    static final float LURE_PERIOD = 2.0f;

    /** One world's clocks. */
    static final class Clock {
        private float sprint;
        private float timer;
        private float lure;

        void advance(float dt) {
            sprint += dt;
            timer += dt;
            lure += dt;
        }

        boolean takeLure() {
            if (lure < LURE_PERIOD) return false;
            lure = 0;
            return true;
        }

        boolean takeSprint() {
            if (sprint < SPRINT_PERIOD) return false;
            sprint = 0;
            return true;
        }

        boolean takeTimer() {
            if (timer < TIMER_PERIOD) return false;
            timer = 0;
            return true;
        }
    }

    private final Map<String, Clock> byWorld = new ConcurrentHashMap<>();

    @Nonnull
    Clock of(@Nonnull String worldName) {
        Clock c = byWorld.get(worldName);
        return c != null ? c : byWorld.computeIfAbsent(worldName, k -> new Clock());
    }
}
