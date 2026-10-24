package com.hexvane.cozytalesranching.compat.claims;

import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Soft integration with land-claims mods (QuestLinesClaims). Houses ask {@link #claimAllows} whether the claim
 * around a house lets a player act there; with no claims mod, outside claims, or with {@code ClaimsIntegration}
 * off in the config, the answer is always "the claim says nothing" and the house rules are exactly the old ones.
 * <p>
 * The claims mod is bound lazily on first use (all plugins have loaded by then). If it is not installed the
 * answer is cached until the config is reloaded; if it is installed but not ready, binding is tried again; if its
 * API has an unknown shape, that is logged once and claims are ignored.
 */
public final class Claims {
    /** What a player wants to do with a house, and the claim flag that decides it. */
    public enum Action {
        /** Open the house's management: rename, breeding, move residents, call back, locate. */
        MANAGE(ClaimsAdapter.Flag.INTERACT),
        /** Take the house's stored produce. */
        COLLECT(ClaimsAdapter.Flag.CONTAINER),
        /** Break or pick up the house block. */
        BREAK(ClaimsAdapter.Flag.BREAK);

        public final ClaimsAdapter.Flag flag;

        Action(@Nonnull ClaimsAdapter.Flag flag) {
            this.flag = flag;
        }
    }

    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private record Binding(@Nonnull RanchConfig config, @Nonnull ClaimsAdapter adapter) {}

    @Nullable private static volatile Binding binding;
    @Nullable private static volatile ClaimsAdapter override;
    @Nonnull private static volatile Supplier<List<?>> pluginSource = Claims::loadedPlugins;

    private Claims() {}

    /** The claim around the block lets {@code actor} do {@code action}: the claim's owner always, others by the claim's flag. */
    public static boolean claimAllows(@Nonnull UUID actor, @Nonnull String world, int x, int y, int z, @Nonnull Action action) {
        return claimAllows(adapter(), actor, world, x, y, z, action);
    }

    /** The decision itself, for any adapter. False when the block is in no claim. */
    public static boolean claimAllows(@Nonnull ClaimsAdapter claims, @Nonnull UUID actor, @Nonnull String world, int x, int y, int z,
                                      @Nonnull Action action) {
        ClaimsAdapter.Claim claim = claims.claimAt(world, x, y, z);
        if (claim == null) return false;
        if (claims.isOwner(claim, actor)) return true;
        return claims.allows(actor, world, x, y, z, action.flag);
    }

    /** The active adapter: {@link ClaimsAdapter#NONE} when the integration is off or no claims mod is installed. */
    @Nonnull
    public static ClaimsAdapter adapter() {
        ClaimsAdapter forced = override;
        if (forced != null) return forced;
        RanchConfig config = RanchConfig.get();
        if (!config.claimsIntegration()) return ClaimsAdapter.NONE;
        Binding b = binding;
        if (b != null && b.config == config) return b.adapter;
        ClaimsAdapter bound = bind();
        if (bound != null) {
            binding = new Binding(config, bound);
            if (bound != ClaimsAdapter.NONE) LOG.info("Houses follow " + bound.name() + " claim permissions (config ClaimsIntegration).");
            return bound;
        }
        if (!pluginPresent()) binding = new Binding(config, ClaimsAdapter.NONE);
        return ClaimsAdapter.NONE;
    }

    /** Tests: use this adapter instead of binding (null to go back). */
    public static void installForTests(@Nullable ClaimsAdapter adapter) {
        override = adapter;
        binding = null;
    }

    /** Tests: the loaded plugins as the server would list them (null for the real plugin manager). */
    public static void installPluginsForTests(@Nullable Supplier<List<?>> plugins) {
        pluginSource = plugins != null ? plugins : Claims::loadedPlugins;
        binding = null;
    }

    @Nullable
    private static ClaimsAdapter bind() {
        List<?> plugins = plugins();
        if (plugins.isEmpty()) return null;
        return QuestLinesClaimsAdapter.bind(plugins, QuestLinesClaimsAdapter.PLUGIN_CLASS, QuestLinesClaimsAdapter.DATA_PREFIX,
            PlayerRef.class, Claims::onlinePlayer);
    }

    private static boolean pluginPresent() {
        for (Object p : plugins()) {
            if (p.getClass().getName().equals(QuestLinesClaimsAdapter.PLUGIN_CLASS)) return true;
        }
        return false;
    }

    @Nonnull
    private static List<?> plugins() {
        return pluginSource.get();
    }

    @Nonnull
    private static List<?> loadedPlugins() {
        PluginManager manager = PluginManager.get();
        return manager != null ? manager.getPlugins() : List.of();
    }

    @Nullable
    private static Object onlinePlayer(@Nonnull UUID id) {
        Universe universe = Universe.get();
        return universe != null ? universe.getPlayer(id) : null;
    }
}
