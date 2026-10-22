package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Owned by the animal role: persistent records, deduplication, adopting animals tamed before this mod, protection, recovery, the compatibility guard. */
public final class AnimalFeature {
    /** After the worlds have shut down (their entities unloaded and positions noted), before universe resources flush. */
    private static final short SAVE_ON_SHUTDOWN = ShutdownEvent.SHUTDOWN_WORLDS + 4;

    @Nullable private static volatile PersistentAnimalRegistry registry;
    @Nullable private static volatile AnimalRecovery recovery;

    private AnimalFeature() {}

    /** Called once from the plugin's setup(). Register components, systems, interactions and sub-commands here. */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        ComponentRegistryProxy<EntityStore> entities = plugin.getEntityStoreRegistry();
        ComponentType<EntityStore, AnimalMarker> markerType = entities.registerComponent(AnimalMarker.class, AnimalMarker.ID, AnimalMarker.CODEC);
        AnimalMarker.bindType(markerType);

        PersistentAnimalRegistry animals = new PersistentAnimalRegistry(plugin.getDataDirectory().resolve("Animals"));
        animals.load();
        registry = animals;
        Ranch.installAnimals(animals);

        entities.registerSystem(new AnimalEntitySystem(animals, markerType));
        entities.registerSystem(new AnimalProtectionSystem(animals, markerType));
        entities.registerSystem(new AnimalDeathSystem(animals, markerType));

        AnimalRecovery rec = new AnimalRecovery(animals, markerType);
        rec.start();
        recovery = rec;

        plugin.getEventRegistry().register(SAVE_ON_SHUTDOWN, ShutdownEvent.class, event -> shutdown());
        Runtime.getRuntime().addShutdownHook(new Thread(AnimalFeature::shutdown, "CozyRanch-AnimalsShutdown"));
    }

    @Nullable
    static AnimalRecovery recovery() {
        return recovery;
    }

    private static void shutdown() {
        AnimalRecovery rec = recovery;
        if (rec != null) rec.stop();
        PersistentAnimalRegistry animals = registry;
        if (animals != null) animals.close();
    }
}
