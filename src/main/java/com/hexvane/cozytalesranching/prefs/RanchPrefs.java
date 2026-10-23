package com.hexvane.cozytalesranching.prefs;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-player ranch settings: chat lines and sound effects, each on or off (both on by default). Toggled in the journal's
 * settings page; read by {@link RanchChat} and {@code RanchSounds}. Saved to {@code <data>/Prefs/<uuid>.json} a moment
 * after a change, on its own thread, and at shutdown.
 */
public final class RanchPrefs {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final long SAVE_DELAY_MS = 2000;

    @Nullable private static volatile RanchPrefs instance;

    private final PrefsStore store;
    @Nullable private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private volatile boolean closed;

    RanchPrefs(@Nonnull PrefsStore store, boolean saveThread) {
        this.store = store;
        if (saveThread) {
            ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r, "CozyRanch-Prefs");
                t.setDaemon(true);
                return t;
            });
            ex.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            this.executor = ex;
        } else {
            this.executor = null;
        }
    }

    /** Loads every player's settings and installs them. Setup thread. */
    @Nonnull
    public static RanchPrefs start(@Nullable Path dataDirectory) {
        PrefsStore store = new PrefsStore(dataDirectory == null ? null : dataDirectory.resolve("Prefs"));
        store.loadAll();
        RanchPrefs prefs = new RanchPrefs(store, true);
        instance = prefs;
        return prefs;
    }

    /** For tests: settings in memory (and in {@code store}'s folder when it has one), saved only by {@link #close()}. */
    @Nonnull
    public static RanchPrefs installForTest(@Nonnull PrefsStore store) {
        RanchPrefs prefs = new RanchPrefs(store, false);
        instance = prefs;
        return prefs;
    }

    public static void uninstall() {
        instance = null;
    }

    /** Ranch chat lines on for this player. True when the settings are not running. */
    public static boolean chat(@Nonnull UUID player) {
        RanchPrefs p = instance;
        return p == null || p.store.chat(player);
    }

    /** Ranch sounds on for this player. True when the settings are not running. */
    public static boolean sounds(@Nonnull UUID player) {
        RanchPrefs p = instance;
        return p == null || p.store.sounds(player);
    }

    /** Cheap check before filtering listeners one by one. */
    public static boolean anySoundsOff() {
        RanchPrefs p = instance;
        return p != null && p.store.anySoundsOff();
    }

    @Nonnull
    public static PlayerPrefs of(@Nonnull UUID player) {
        RanchPrefs p = instance;
        return p == null ? new PlayerPrefs() : p.store.copy(player);
    }

    /** Flips one setting and returns its new value. */
    public static boolean toggle(@Nonnull UUID player, @Nonnull PlayerPrefs.Setting setting) {
        RanchPrefs p = instance;
        if (p == null) return true;
        boolean on = p.store.toggle(player, setting);
        p.scheduleSave();
        return on;
    }

    public static void set(@Nonnull UUID player, @Nonnull PlayerPrefs.Setting setting, boolean on) {
        RanchPrefs p = instance;
        if (p == null) return;
        if (p.store.set(player, setting, on)) p.scheduleSave();
    }

    private void scheduleSave() {
        if (executor == null || closed || !saveScheduled.compareAndSet(false, true)) return;
        try {
            executor.schedule(this::saveNow, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            saveScheduled.set(false);
        }
    }

    private void saveNow() {
        saveScheduled.set(false);
        if (store.saveDirty() > 0) scheduleSave();
    }

    /** Writes everything changed on the calling thread. For shutdown. */
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int failed = store.saveDirty();
        if (failed > 0) LOG.severe(failed + " ranch settings file(s) could not be saved at shutdown");
    }
}
