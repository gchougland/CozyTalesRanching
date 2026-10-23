package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.config.RanchConfig;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The journal role's state: every player's journal and debounced saves on its own thread. */
final class JournalService {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    @Nullable private static volatile JournalService instance;

    private final JournalStore store;
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private volatile boolean closed;

    JournalService(@Nullable Path dataDirectory) {
        this.store = new JournalStore(dataDirectory == null ? null : dataDirectory.resolve("Journal"));
        ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "CozyRanch-Journal");
            t.setDaemon(true);
            return t;
        });
        ex.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        ex.setRemoveOnCancelPolicy(true);
        this.executor = ex;
    }

    @Nullable
    static JournalService get() {
        return instance;
    }

    static void install(@Nullable JournalService service) {
        instance = service;
    }

    void load() {
        store.loadAll();
        LOG.info("Journal: " + store.size() + " player journals");
    }

    @Nonnull
    JournalRules.Change apply(@Nonnull UUID player, @Nonnull Function<PlayerJournal, JournalRules.Change> edit) {
        JournalRules.Change change = store.apply(player, edit);
        if (change.any()) scheduleSave();
        return change;
    }

    @Nonnull
    PlayerJournal copy(@Nonnull UUID player) {
        return store.copy(player);
    }

    void reset(@Nonnull UUID player) {
        store.reset(player);
        scheduleSave();
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

    /** Writes everything changed on the calling thread. For shutdown. */
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
        if (failed > 0) LOG.severe(failed + " journal file(s) could not be saved at shutdown");
    }
}
