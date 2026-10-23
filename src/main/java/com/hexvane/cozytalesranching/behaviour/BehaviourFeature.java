package com.hexvane.cozytalesranching.behaviour;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.api.event.AnimalBehaviourEvent;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Owned by the behaviour role: flavour behaviours, personality, babies following parents. */
public final class BehaviourFeature {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final String RESOURCE = "/Server/CozyTalesRanching/Behaviour.json";

    private static volatile BehaviourConfig config = new BehaviourConfig().normalise();
    private static volatile boolean logToggle;
    @Nullable private static volatile Path dataDirectory;
    private static final BehaviourPlanner PLANNER = new BehaviourPlanner();
    /** AnimalBehaviourEvents seen on the event bus, by behaviour id (the status command shows them). */
    static final Map<String, Integer> RECEIVED = new ConcurrentHashMap<>();

    private BehaviourFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        dataDirectory = plugin.getDataDirectory();
        config = load(plugin.getDataDirectory());
        plugin.getEntityStoreRegistry().registerSystem(new BehaviourSystem(PLANNER));
        plugin.getEventRegistry().register(AnimalBehaviourEvent.class, e -> RECEIVED.merge(e.getBehaviourId(), 1, Integer::sum));
        plugin.getLogger().atInfo().log("Behaviour registered: %d behaviours for %d species", config.Behaviours.size(), config.Species.size());
    }

    /** Called from /ranch reload: re-reads Behaviour.json and lets every animal that was busy idle again. */
    public static void reload() {
        Path dir = dataDirectory;
        if (dir == null) return;
        config = load(dir);
        PLANNER.toys.invalidate();
        Universe universe = Universe.get();
        if (universe != null) {
            for (World world : universe.getWorlds().values()) world.execute(() -> PLANNER.stopAll(world));
        }
        LOG.info("Reloaded Behaviour.json: " + config.Behaviours.size() + " behaviours");
    }

    @Nonnull
    static BehaviourConfig config() {
        return config;
    }

    @Nonnull
    static BehaviourPlanner planner() {
        return PLANNER;
    }

    static boolean logging() {
        return logToggle || config.Log || RanchConfig.get().debugLogging();
    }

    static void setLogging(boolean on) {
        logToggle = on;
    }

    /** The bundled file, or {@code <data>/Behaviour.json} when present. */
    @Nonnull
    static BehaviourConfig load(@Nonnull Path dataDir) {
        Path override = dataDir.resolve("Behaviour.json");
        if (Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                return BehaviourConfig.read(r);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read " + override + "; using the bundled Behaviour.json", e);
            }
        }
        try (InputStream in = BehaviourFeature.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOG.warning("Bundled Behaviour.json missing");
                return new BehaviourConfig().normalise();
            }
            return BehaviourConfig.read(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read the bundled Behaviour.json", e);
            return new BehaviourConfig().normalise();
        }
    }
}
