package com.hexvane.cozytalesranching.wildlife;

import com.hexvane.cozytalesranching.config.RanchConfig;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The wildlife role's config and visitor store, and the thread that saves the store. */
public final class WildlifeService {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static volatile WildlifeService instance;

    private final Path dataDirectory;
    private final VisitorStore store;
    private volatile WildlifeConfig config;
    private final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CozyRanch-Wildlife");
        t.setDaemon(true);
        return t;
    });
    @Nullable private ScheduledFuture<?> pendingSave;
    private volatile boolean closed;

    WildlifeService(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.store = new VisitorStore(dataDirectory);
        this.config = WildlifeConfig.load(dataDirectory);
    }

    @Nullable
    public static WildlifeService get() {
        return instance;
    }

    static void install(@Nonnull WildlifeService service) {
        instance = service;
    }

    @Nonnull
    public WildlifeConfig config() {
        return config;
    }

    @Nonnull
    public VisitorStore store() {
        return store;
    }

    void reloadConfig() {
        config = WildlifeConfig.load(dataDirectory);
    }

    /** Debounced save on the wildlife thread; never on a world thread. */
    public synchronized void scheduleSave() {
        if (closed || (pendingSave != null && !pendingSave.isDone())) return;
        long delayMs = Math.round(RanchConfig.get().saveDebounceSeconds() * 1000);
        pendingSave = saver.schedule(() -> saveNow(false), delayMs, TimeUnit.MILLISECONDS);
    }

    private void saveNow(boolean includeMoved) {
        try {
            Map<String, String> worlds = store.drainDirty(includeMoved);
            if (!worlds.isEmpty()) store.write(worlds);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Saving visitors failed", e);
        }
    }

    /** Writes what is dirty now and stops the thread. Safe to call twice. */
    void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            if (pendingSave != null) pendingSave.cancel(false);
        }
        saver.shutdown();
        try {
            saver.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (store.hasDirty(true)) {
            saveNow(true);
            LOG.info("Saved ranch visitors.");
        }
    }
}
