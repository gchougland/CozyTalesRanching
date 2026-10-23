package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.RanchHouseRemovedEvent;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Owned by the produce role: products in houses and automatic collection. */
public final class ProduceFeature {
    /** With the animal and house registries: after the worlds have shut down. */
    private static final short SAVE_ON_SHUTDOWN = ShutdownEvent.SHUTDOWN_WORLDS + 4;

    @Nullable private static volatile ProduceService service;
    @Nullable private static volatile Path dataDirectory;
    @Nullable private static volatile PoopService poop;

    private ProduceFeature() {}

    @Nullable
    static ProduceService service() {
        return service;
    }

    @Nullable
    static PoopService poop() {
        return poop;
    }

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        dataDirectory = plugin.getDataDirectory();
        ProduceTuning.load(plugin.getDataDirectory());
        ProduceStore store = new ProduceStore(plugin.getDataDirectory().resolve("Produce"), () -> RanchConfig.get().saveDebounceSeconds());
        store.loadAll();
        ProduceService s = new ProduceService(store);
        service = s;
        Ranch.installProduce(s);

        plugin.getEntityStoreRegistry().registerSystem(new ProduceSystem(s));
        PoopTuning.load(plugin.getDataDirectory());
        PoopService ps = new PoopService();
        poop = ps;
        plugin.getEntityStoreRegistry().registerSystem(new PoopSystem(ps));
        plugin.getEntityStoreRegistry().registerSystem(new PoopSystem.Place(ps));
        plugin.getEventRegistry().register(RanchHouseRemovedEvent.class, s::onHouseRemoved);
        plugin.getEventRegistry().register(SAVE_ON_SHUTDOWN, ShutdownEvent.class, event -> shutdown());
        Runtime.getRuntime().addShutdownHook(new Thread(ProduceFeature::shutdown, "CozyRanch-ProduceShutdown"));
        logSummary(plugin);
    }

    /** Called from /ranch reload. */
    public static void reload() {
        Path dir = dataDirectory;
        if (dir != null) {
            ProduceTuning.load(dir);
            PoopTuning.load(dir);
        }
    }

    private static void logSummary(@Nonnull CozyTalesRanchingPlugin plugin) {
        ProduceTuning t = ProduceTuning.get();
        StringBuilder b = new StringBuilder();
        for (SpeciesDefinition s : SpeciesRegistry.all()) {
            for (SpeciesDefinition.Product p : s.Products) {
                ProduceRules.Mode m = ProduceRules.mode(p.Collect);
                if (m == ProduceRules.Mode.NONE) continue;
                if (!b.isEmpty()) b.append(", ");
                b.append(s.Id).append(' ').append(p.Item).append(" every ").append(p.IntervalMinutes).append(" min");
                if (m == ProduceRules.Mode.BUCKET) b.append(" (bucket)");
            }
        }
        plugin.getLogger().atInfo().log("Produce registered: pass every %.0f s, house cap %d, catch-up cap %.0f min; %s",
            t.PassSeconds, t.HouseCap, t.CatchUpCapMinutes, b.toString());
        PoopTuning pt = PoopTuning.get();
        plugin.getLogger().atInfo().log("Poop piles %s: pass every %.0f s, %.2f per minute after %.1f min, merge radius %d, cap %d, %d per house, off for %s",
            pt.Enabled ? "on" : "off", pt.PassSeconds, pt.ChancePerMinute, pt.CooldownMinutes, pt.MergeRadius, pt.MaxCount, pt.MaxPilesPerHouse,
            pt.Species.entrySet().stream().filter(e -> !e.getValue()).map(java.util.Map.Entry::getKey).toList());
    }

    private static void shutdown() {
        ProduceService s = service;
        if (s != null) s.store().close();
    }
}
