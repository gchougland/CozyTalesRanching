package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Watches each world's game clock and, when game time jumps ahead (a night slept through, {@code /time set}), lets the
 * ranch live through the skipped time: houses produce it (a production pass with the jump's minutes), breeding
 * cooldowns, babies and meal clocks move on by it. See {@link GameTimeRules}. Each tick costs one resource read and a
 * little arithmetic; the work runs only on a jump.
 */
public final class GameTimeSystem extends TickingSystem<EntityStore> {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Map<String, GameTimeRules.Tracker> TRACKERS = new ConcurrentHashMap<>();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        observe(store.getExternalData().getWorld(), store);
    }

    /**
     * Looks at the world's clock now and applies a jump if there was one. World thread only. The house's morning pass
     * calls this first, so a morning reached by sleeping sees the night's breeding cooldown already gone.
     */
    public static void observe(@Nonnull World world) {
        observe(world, world.getEntityStore().getStore());
    }

    private static void observe(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) return;
        Instant game = time.getGameTime();
        if (game == null) return;
        int day = world.getDaytimeDurationSeconds();
        int night = world.getNighttimeDurationSeconds();
        GameTimeRules.Tracker t = TRACKERS.computeIfAbsent(world.getName(), w -> new GameTimeRules.Tracker());
        long before = t.highWaterGameMs();
        double cap = GameTimeRules.capMs(ProduceTuning.get().TimeJumpCapGameDays, day, night);
        double jump = GameTimeRules.observe(t, System.nanoTime(), game.toEpochMilli(), GameTimeRules.gameSecondsPerRealSecond(day, night), cap);
        if (jump > 0) applyJump(world, (long) jump, t.highWaterGameMs() - before);
    }

    /** Called for a jump; shifts the animals' timers now and queues the production pass outside the tick. */
    static void applyJump(@Nonnull World world, long jumpMs, long gameMs) {
        int shifted = 0;
        for (AnimalRecord r : Ranch.animals().inWorld(world.getName())) {
            if (GameTimeRules.shift(r, jumpMs)) {
                Ranch.animals().markDirty(r);
                shifted++;
            }
        }
        double minutes = jumpMs / 60_000.0;
        LOG.info(String.format(Locale.ROOT, "Game time in %s jumped %.1f in-game hours: the ranch lives through %.1f minutes (%d animal timer(s) moved on)",
            world.getName(), gameMs / 3_600_000.0, minutes, shifted));
        ProduceService service = ProduceFeature.service();
        if (service == null) return;
        world.execute(() -> {
            try {
                service.pass(world, System.currentTimeMillis(), minutes);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Produce pass for a time jump failed in world " + world.getName(), e);
            }
        });
    }
}
