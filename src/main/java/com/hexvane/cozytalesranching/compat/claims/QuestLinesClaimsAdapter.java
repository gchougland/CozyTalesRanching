package com.hexvane.cozytalesranching.compat.claims;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * QuestLinesClaims through its public API object ({@code QuestLinesClaimsPlugin.getInstance().getApi()}), by
 * reflection: the claims mod lives in its own classloader and is optional. Only public API methods are used:
 * {@code getTopRegionAt}, {@code getClaimAtBlock}, {@code isOwnerOfRegion}, {@code isOwnerOfClaim} and
 * {@code canPlayerAct(PlayerRef, world, Vector3i, RegionFlags)}, which applies the claim's owner, member, group
 * and default flags the same way the claims mod's own block protection does.
 * <p>
 * Any reflective failure while answering is logged once and answered as "not claimed" or "not allowed", which
 * is the mod's behaviour without the claims mod.
 */
final class QuestLinesClaimsAdapter implements ClaimsAdapter {
    static final String PLUGIN_CLASS = "net.evilcraft.questlinesclaims.QuestLinesClaimsPlugin";
    static final String DATA_PREFIX = "net.evilcraft.questlinesclaims.data.";
    static final String REGION = "region";
    static final String CLAIM = "claim";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private final Object api;
    private final Method topRegionAt;
    private final Method regionId;
    private final Method claimAtBlock;
    private final Method claimId;
    private final Method ownerOfRegion;
    private final Method ownerOfClaim;
    private final Method canPlayerAct;
    private final Map<Flag, Object> flags;
    private final Function<UUID, Object> players;
    private volatile boolean warned;

    private QuestLinesClaimsAdapter(Object api, Method topRegionAt, Method regionId, Method claimAtBlock, Method claimId, Method ownerOfRegion,
                                    Method ownerOfClaim, Method canPlayerAct, Map<Flag, Object> flags, Function<UUID, Object> players) {
        this.api = api;
        this.topRegionAt = topRegionAt;
        this.regionId = regionId;
        this.claimAtBlock = claimAtBlock;
        this.claimId = claimId;
        this.ownerOfRegion = ownerOfRegion;
        this.ownerOfClaim = ownerOfClaim;
        this.canPlayerAct = canPlayerAct;
        this.flags = flags;
        this.players = players;
    }

    /**
     * Finds the claims plugin among {@code plugins} by class name and binds its API. Null when the plugin is
     * absent or its API is not ready yet; {@link ClaimsAdapter#NONE} (logged once) when its shape is not the one
     * this adapter knows (a different version), so the caller stops trying.
     *
     * @param playerRefClass the class {@code canPlayerAct} takes as its player (the server's {@code PlayerRef})
     * @param players        online player by UUID, as an instance of {@code playerRefClass}, or null
     */
    @Nullable
    static ClaimsAdapter bind(@Nonnull Iterable<?> plugins, @Nonnull String pluginClass, @Nonnull String dataPrefix,
                                        @Nonnull Class<?> playerRefClass, @Nonnull Function<UUID, Object> players) {
        Object plugin = null;
        for (Object p : plugins) {
            if (p != null && p.getClass().getName().equals(pluginClass)) {
                plugin = p;
                break;
            }
        }
        if (plugin == null) return null;
        try {
            Object api = plugin.getClass().getMethod("getApi").invoke(plugin);
            if (api == null) return null;
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> apiClass = api.getClass();
            Class<?> flagClass = Class.forName(dataPrefix + "RegionFlags", false, loader);
            Class<?> regionClass = Class.forName(dataPrefix + "AdminRegion", false, loader);
            Class<?> claimClass = Class.forName(dataPrefix + "PlayerClaim", false, loader);
            Map<Flag, Object> flags = new EnumMap<>(Flag.class);
            for (Flag f : Flag.values()) flags.put(f, enumConstant(flagClass, f.name()));
            return new QuestLinesClaimsAdapter(api,
                apiClass.getMethod("getTopRegionAt", String.class, int.class, int.class, int.class),
                regionClass.getMethod("getId"),
                apiClass.getMethod("getClaimAtBlock", String.class, int.class, int.class),
                claimClass.getMethod("getClaimId"),
                apiClass.getMethod("isOwnerOfRegion", String.class, UUID.class),
                apiClass.getMethod("isOwnerOfClaim", int.class, UUID.class),
                apiClass.getMethod("canPlayerAct", playerRefClass, String.class, Vector3i.class, flagClass),
                flags, players);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.log(Level.WARNING, "QuestLinesClaims is installed but its API is not one Cozy Tales: Ranching knows; houses ignore claims. " + e);
            return ClaimsAdapter.NONE;
        }
    }

    @Nonnull
    private static Object enumConstant(@Nonnull Class<?> enumClass, @Nonnull String name) throws NoSuchFieldException {
        for (Object c : enumClass.getEnumConstants() != null ? enumClass.getEnumConstants() : new Object[0]) {
            if (((Enum<?>) c).name().equals(name)) return c;
        }
        throw new NoSuchFieldException(enumClass.getName() + "." + name);
    }

    @Nonnull @Override
    public String name() {
        return "QuestLinesClaims";
    }

    @Nullable @Override
    public Claim claimAt(@Nonnull String world, int x, int y, int z) {
        try {
            Object region = topRegionAt.invoke(api, world, x, y, z);
            if (region != null) {
                Object id = regionId.invoke(region);
                if (id != null) return new Claim(REGION, id.toString());
            }
            Object claim = claimAtBlock.invoke(api, world, x, z);
            if (claim != null) return new Claim(CLAIM, String.valueOf(claimId.invoke(claim)));
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return null;
        }
    }

    @Override
    public boolean isOwner(@Nonnull Claim claim, @Nonnull UUID player) {
        try {
            Object owner = switch (claim.kind()) {
                case REGION -> ownerOfRegion.invoke(api, claim.id(), player);
                case CLAIM -> ownerOfClaim.invoke(api, Integer.parseInt(claim.id()), player);
                default -> Boolean.FALSE;
            };
            return Boolean.TRUE.equals(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return false;
        }
    }

    @Override
    public boolean allows(@Nonnull UUID player, @Nonnull String world, int x, int y, int z, @Nonnull Flag flag) {
        Object ref = players.apply(player);
        if (ref == null) return false;
        try {
            return Boolean.TRUE.equals(canPlayerAct.invoke(api, ref, world, new Vector3i(x, y, z), flags.get(flag)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return false;
        }
    }

    private void warnOnce(@Nonnull Exception e) {
        if (warned) return;
        warned = true;
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        LOG.log(Level.WARNING, "QuestLinesClaims lookup failed; treating houses as unclaimed: " + cause);
    }
}
