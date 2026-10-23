package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * The house page's "Show area": two rings of glowing motes on the ground, sent to the player who turned them on and
 * nobody else. Honey ({@code HouseBorder}): the habitat scan disc around the house (decorations, water, shade, and
 * troughs with food that count for the habitat). Sky blue ({@code HouseBorderTrough}): how far from the door an
 * animal asleep at home reaches a trough to eat, drawn only when it differs from the first ring.
 * <p>
 * The ring points sit on the ground nearest the house's height, worked out once when turned on (and again on
 * Refresh) from sections already in memory; a point whose column is not loaded is left out. Every
 * {@link #PULSE_MS} the motes are sent again in one write. It ends after {@link #LIFETIME_MS}, when the player
 * walks {@link #LEAVE_MARGIN} blocks past the ring, changes world or leaves, or toggles it off. Writes no blocks.
 */
public final class HouseBorder {
    public static final String HABITAT_PARTICLE = "HouseBorder";
    public static final String TROUGH_PARTICLE = "HouseBorderTrough";
    public static final long PULSE_MS = 2_000L;
    public static final long LIFETIME_MS = 180_000L;
    public static final double LEAVE_MARGIN = 24.0;
    /** Blocks between motes along a ring. */
    public static final double SPACING = 1.5;
    /** Ground is searched from this far above the reference height down to {@link #BELOW} below it. */
    public static final int ABOVE = 10;
    public static final int BELOW = 6;

    /** One mote: where, and which ring. */
    public record Point(double x, double y, double z, boolean trough) {}

    record Active(@Nonnull String world, @Nonnull UUID houseId, double cx, double cz, double leaveRadius,
                  @Nonnull List<Point> points, long untilMs) {}

    /** What a ring point's column holds, for the ground search. */
    interface Column {
        boolean loaded(int y);

        /** Solid or fluid: something to stand a mote on. */
        boolean ground(int y);
    }

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private HouseBorder() {}

    public static boolean isOn(@Nonnull UUID player, @Nonnull UUID houseId) {
        Active a = ACTIVE.get(player);
        return a != null && a.houseId().equals(houseId);
    }

    public static void stop(@Nonnull UUID player) {
        ACTIVE.remove(player);
    }

    /** Turns the border of this house on for the player (replacing any other), and sends the first motes. World thread. */
    public static void start(@Nonnull PlayerRef player, @Nonnull World world, @Nonnull HouseRecord house, long nowMs) {
        int radius = RanchConfig.get().habitatScanRadius();
        int troughRange = RanchConfig.get().troughRange();
        List<Point> points = points(world, house, radius, troughRange);
        double cx = house.x + 0.5, cz = house.z + 0.5;
        Active a = new Active(world.getName(), house.houseId, cx, cz, radius + 0.5 + LEAVE_MARGIN, points, nowMs + LIFETIME_MS);
        ACTIVE.put(player.getUuid(), a);
        send(player, a);
    }

    /** After a Refresh: the ground may have changed, so the points are worked out again (the timer is kept). World thread. */
    public static void refresh(@Nonnull PlayerRef player, @Nonnull World world, @Nonnull HouseRecord house) {
        Active a = ACTIVE.get(player.getUuid());
        if (a == null || !a.houseId().equals(house.houseId)) return;
        List<Point> points = points(world, house, RanchConfig.get().habitatScanRadius(), RanchConfig.get().troughRange());
        ACTIVE.put(player.getUuid(), new Active(a.world(), a.houseId(), a.cx(), a.cz(), a.leaveRadius(), points, a.untilMs()));
    }

    static boolean anyIn(@Nonnull String world) {
        if (ACTIVE.isEmpty()) return false;
        for (Active a : ACTIVE.values()) if (a.world().equals(world)) return true;
        return false;
    }

    /** Sends every border of this world again, and ends the ones that are done. World thread, outside ticks. */
    static void pulse(@Nonnull World world, long nowMs) {
        for (Map.Entry<UUID, Active> e : List.copyOf(ACTIVE.entrySet())) {
            Active a = e.getValue();
            if (!a.world().equals(world.getName())) continue;
            PlayerRef player = Universe.get().getPlayer(e.getKey());
            Vector3d at = player == null ? null : position(player, world);
            if (at == null || !keep(a, at.x, at.z, nowMs)) {
                ACTIVE.remove(e.getKey(), a);
                continue;
            }
            send(player, a);
        }
    }

    /** Still on: not past its time, and the player within the leave radius of the house. */
    static boolean keep(@Nonnull Active a, double playerX, double playerZ, long nowMs) {
        if (nowMs >= a.untilMs()) return false;
        double dx = playerX - a.cx(), dz = playerZ - a.cz();
        return dx * dx + dz * dz <= a.leaveRadius() * a.leaveRadius();
    }

    /** The player's position if they are in this world, else null. */
    @Nullable
    private static Vector3d position(@Nonnull PlayerRef player, @Nonnull World world) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid() || ref.getStore().getExternalData().getWorld() != world) return null;
        return player.getTransform().getPosition();
    }

    private static void send(@Nonnull PlayerRef player, @Nonnull Active a) {
        if (a.points().isEmpty()) return;
        SpawnParticleSystem[] packets = new SpawnParticleSystem[a.points().size()];
        for (int i = 0; i < packets.length; i++) {
            Point p = a.points().get(i);
            packets[i] = new SpawnParticleSystem(p.trough() ? TROUGH_PARTICLE : HABITAT_PARTICLE, new Position(p.x(), p.y(), p.z()),
                null, 1f, null, 0f);
        }
        player.getPacketHandler().write(packets);
    }

    /** Both rings of a house, on the ground. World thread. */
    @Nonnull
    static List<Point> points(@Nonnull World world, @Nonnull HouseRecord house, int radius, int troughRange) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        List<Point> out = new ArrayList<>();
        // the scan covers whole columns within the radius of the house's column, so the edge is half a block further out
        for (double[] xz : ring(house.x + 0.5, house.z + 0.5, radius + 0.5, SPACING)) {
            Integer y = ground(reader, xz[0], xz[1], house.y);
            if (y != null) out.add(new Point(xz[0], y, xz[1], false));
        }
        if (showTroughRing(radius, troughRange)) {
            int doorY = (int) Math.floor(house.doorY);
            for (double[] xz : ring(house.doorX, house.doorZ, troughRange, SPACING)) {
                Integer y = ground(reader, xz[0], xz[1], doorY);
                if (y != null) out.add(new Point(xz[0], y, xz[1], true));
            }
        }
        return out;
    }

    /** The trough ring only adds something when it is clearly smaller or larger than the habitat ring. */
    static boolean showTroughRing(int radius, int troughRange) {
        return Math.abs(radius + 0.5 - troughRange) >= 1.5;
    }

    /** Evenly spaced points on a circle, at most {@code spacing} apart, starting due north. */
    @Nonnull
    static List<double[]> ring(double cx, double cz, double radius, double spacing) {
        int n = Math.max(8, (int) Math.ceil(2 * Math.PI * radius / spacing));
        List<double[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            out.add(new double[] {cx + Math.sin(a) * radius, cz - Math.cos(a) * radius});
        }
        return out;
    }

    @Nullable
    private static Integer ground(@Nonnull SectionReader reader, double x, double z, int refY) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        return groundY(new Column() {
            @Override
            public boolean loaded(int y) {
                return reader.canHoldY(y) && reader.hasStorageInMemory(bx, y, bz);
            }

            @Override
            public boolean ground(int y) {
                if (reader.getFluidId(bx, y, bz) != Fluid.EMPTY_ID) return true;
                int id = reader.getBlock(bx, y, bz);
                if (id == BlockType.EMPTY_ID) return false;
                BlockType type = BlockType.getAssetMap().getAsset(id);
                return type != null && !type.isUnknown() && type.getMaterial() == BlockMaterial.Solid;
            }
        }, refY);
    }

    /**
     * The height a mote stands at in this column: one above the ground surface (ground with no ground on top) nearest
     * {@code refY}, searched from {@link #ABOVE} above to {@link #BELOW} below. Null when any of it is not in memory or
     * there is no surface.
     */
    @Nullable
    static Integer groundY(@Nonnull Column column, int refY) {
        Integer best = null;
        for (int y = refY + ABOVE; y >= refY - BELOW; y--) {
            if (!column.loaded(y) || !column.loaded(y + 1)) return null;
            if (!column.ground(y) || column.ground(y + 1)) continue;
            int standAt = y + 1;
            if (best == null || Math.abs(standAt - refY) < Math.abs(best - refY)) best = standAt;
        }
        return best;
    }
}
