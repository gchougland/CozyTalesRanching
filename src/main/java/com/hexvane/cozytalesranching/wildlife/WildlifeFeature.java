package com.hexvane.cozytalesranching.wildlife;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import javax.annotation.Nonnull;

/** Owned by the wildlife role: wild visitors attracted by habitats, rare variants. */
public final class WildlifeFeature {
    /** With the other registries' flushes: after the worlds shut down. */
    private static final short SAVE_ON_SHUTDOWN = ShutdownEvent.SHUTDOWN_WORLDS + 4;

    private WildlifeFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        WildlifeService service = new WildlifeService(plugin.getDataDirectory());
        service.store().load();
        WildlifeService.install(service);
        plugin.getEntityStoreRegistry().registerSystem(new WildlifeSystem());
        plugin.getEventRegistry().register(SAVE_ON_SHUTDOWN, ShutdownEvent.class, event -> service.close());
        Runtime.getRuntime().addShutdownHook(new Thread(service::close, "CozyRanch-WildlifeShutdown"));
        plugin.getLogger().atInfo().log("Wildlife registered: %d remembered visitors, %d species, pass every %d s",
            service.store().all().size(), service.config().Species.size(), service.config().passMs() / 1000);
    }

    /** Re-reads Wildlife.json (bundled, or the server's override in the plugin data folder). */
    public static void reload() {
        WildlifeService service = WildlifeService.get();
        if (service != null) service.reloadConfig();
    }
}
