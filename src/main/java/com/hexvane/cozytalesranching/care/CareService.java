package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The care role's state: trough store, habitat config, debounced saves on its own thread. */
final class CareService {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final String HABITAT_RESOURCE = "/Server/CozyTalesRanching/Habitat.json";

    @Nullable private static volatile CareService instance;

    private final CareStore store;
    @Nullable private final Path dataDirectory;
    private volatile HabitatConfig habitat = new HabitatConfig();
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private volatile boolean closed;

    CareService(@Nullable Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.store = new CareStore(dataDirectory == null ? null : dataDirectory.resolve("Troughs"));
        ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "CozyRanch-Care");
            t.setDaemon(true);
            return t;
        });
        ex.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        ex.setRemoveOnCancelPolicy(true);
        this.executor = ex;
    }

    @Nullable
    static CareService get() {
        return instance;
    }

    static void install(@Nullable CareService service) {
        instance = service;
    }

    @Nonnull
    CareStore store() {
        return store;
    }

    @Nonnull
    HabitatConfig habitat() {
        return habitat;
    }

    /** Food some species eats, or an advanced feed (every species eats those). */
    boolean isTroughFood(@Nonnull String itemId) {
        return habitat.feedBonus(itemId) != null || CareRules.isAnyFood(SpeciesRegistry.all(), itemId);
    }

    void load() {
        store.loadAll();
        reloadHabitat();
        LOG.info("Care: " + store.troughCount() + " troughs with food, " + habitat.Features.size() + " habitat features");
    }

    /** The bundled Habitat.json, replaced by {@code <data>/Habitat.json} when a server owner provides one. */
    void reloadHabitat() {
        HabitatConfig loaded = null;
        Path override = dataDirectory == null ? null : dataDirectory.resolve("Habitat.json");
        if (override != null && Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                loaded = HabitatConfig.read(r);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + override + "; using the bundled habitat file", e);
            }
        }
        if (loaded == null) {
            try (InputStream in = CareService.class.getResourceAsStream(HABITAT_RESOURCE)) {
                if (in != null) loaded = HabitatConfig.read(new InputStreamReader(in, StandardCharsets.UTF_8));
                else LOG.warning("Bundled Habitat.json missing");
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read bundled Habitat.json", e);
            }
        }
        habitat = loaded != null ? loaded : new HabitatConfig();
    }

    /** First change starts the timer; later changes ride along. */
    void scheduleSave() {
        if (closed || !store.hasDirty() || !saveScheduled.compareAndSet(false, true)) return;
        long delayMs = (long) (RanchConfig.get().saveDebounceSeconds() * 1000);
        try {
            executor.schedule(this::saveNow, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            saveScheduled.set(false);
        }
    }

    private void saveNow() {
        saveScheduled.set(false);
        if (store.saveDirty() > 0) scheduleSave();
    }

    /** Writes everything dirty on the calling thread. For shutdown. */
    synchronized void close() {
        if (closed) return;
        closed = true;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int failed = store.saveDirty();
        if (failed > 0) LOG.severe(failed + " trough world file(s) could not be saved at shutdown");
    }
}
