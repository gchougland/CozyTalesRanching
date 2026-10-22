package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The real {@link AnimalRegistry}: records in {@link AnimalStore}, saved per world as JSON a few
 * seconds after the last change ({@code SaveDebounceSeconds}) on the ranch's own background thread,
 * and flushed on shutdown.
 */
public final class PersistentAnimalRegistry implements AnimalRegistry {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private final AnimalStore store;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private volatile boolean closed;

    public PersistentAnimalRegistry(@Nonnull Path animalsDir) {
        this.store = new AnimalStore(animalsDir);
        ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "CozyRanch-Animals");
            t.setDaemon(true);
            return t;
        });
        // close() writes whatever is dirty itself; a pending debounced save must not hold shutdown up
        ex.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        ex.setRemoveOnCancelPolicy(true);
        this.executor = ex;
    }

    /** Reads every world's file. Call once before worlds start adding entities. */
    public void load() {
        store.loadAll();
        LOG.info("Loaded " + store.size() + " ranch animals from " + store.dir());
    }

    @Nonnull
    AnimalStore store() {
        return store;
    }

    @Nonnull
    ScheduledExecutorService executor() {
        return executor;
    }

    @Nonnull @Override
    public Optional<AnimalRecord> get(@Nonnull UUID animalId) {
        return Optional.ofNullable(store.get(animalId));
    }

    @Nonnull @Override
    public Optional<AnimalRecord> byEntity(@Nonnull UUID entityUuid) {
        return Optional.ofNullable(store.byEntity(entityUuid));
    }

    @Nonnull @Override
    public List<AnimalRecord> ownedBy(@Nonnull UUID ownerId) {
        return store.ownedBy(ownerId);
    }

    @Nonnull @Override
    public List<AnimalRecord> inHouse(@Nonnull UUID houseId) {
        return store.inHouse(houseId);
    }

    @Nonnull @Override
    public List<AnimalRecord> inWorld(@Nonnull String worldName) {
        return store.inWorld(worldName);
    }

    @Nonnull @Override
    public AnimalRecord create(@Nonnull String speciesId, @Nullable UUID ownerId, @Nonnull String worldName, boolean baby) {
        AnimalRecord r = store.create(speciesId, ownerId, worldName, baby);
        scheduleSave();
        return r;
    }

    @Override
    public void bindEntity(@Nonnull AnimalRecord record, @Nonnull Ref<EntityStore> entity, @Nonnull UUID entityUuid, @Nonnull ComponentAccessor<EntityStore> accessor) {
        long generation = store.bind(record, entityUuid);
        ComponentType<EntityStore, AnimalMarker> type = AnimalMarker.type();
        if (type != null) accessor.putComponent(entity, type, new AnimalMarker(record.animalId, generation));
        AnimalPresence.put(record.animalId, entity);
        scheduleSave();
    }

    @Override
    public boolean isCurrent(@Nonnull UUID animalId, @Nonnull UUID entityUuid, long generation) {
        return store.isCurrent(animalId, entityUuid, generation);
    }

    @Override
    public void markDirty(@Nonnull AnimalRecord record) {
        store.touch(record);
        scheduleSave();
    }

    @Override
    public void remove(@Nonnull UUID animalId) {
        store.remove(animalId);
        AnimalPresence.remove(animalId);
        scheduleSave();
    }

    /** First change starts the timer; later changes ride along, so a save is at most the debounce late. */
    void scheduleSave() {
        if (closed || !saveScheduled.compareAndSet(false, true)) return;
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

    /** Writes everything dirty on the calling thread and stops the background thread. For shutdown. */
    public synchronized void close() {
        if (closed) return;
        closed = true;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int failed = store.saveDirty();
        if (failed > 0) LOG.severe(failed + " ranch animal world file(s) could not be saved at shutdown");
        else LOG.info("Saved ranch animals.");
    }
}
