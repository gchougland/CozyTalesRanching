package com.hexvane.cozytalesranching.compat.claims.fake;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3i;

/**
 * A stand-in written for these tests with the same public method shapes the adapter binds by reflection
 * (plugin with {@code getApi()}, API, {@code RegionFlags}, {@code AdminRegion}, {@code PlayerClaim}). One claim
 * (id 7) covers x and z 0..15 in world "w", owned by {@link #OWNER}; members get the flags in {@link #memberFlags}.
 */
public final class FakeClaimsWorld {
    public static final UUID OWNER = UUID.randomUUID();
    public static final Map<UUID, Set<RegionFlags>> memberFlags = new HashMap<>();

    private FakeClaimsWorld() {}

    public enum RegionFlags { BUILD, BREAK, INTERACT, CONTAINER, DOOR }

    public static final class AdminRegion {
        public String getId() { return "spawn"; }
    }

    public static final class PlayerClaim {
        public int getClaimId() { return 7; }
    }

    /** The adapter's player type in these tests. */
    public record Player(UUID id) {}

    public static final class Plugin {
        public final Object api;
        public Plugin(Object api) { this.api = api; }
        public Object getApi() { return api; }
    }

    public static final class Api {
        public AdminRegion getTopRegionAt(String world, int x, int y, int z) { return null; }
        public PlayerClaim getClaimAtBlock(String world, int x, int z) {
            return "w".equals(world) && x >= 0 && x < 16 && z >= 0 && z < 16 ? new PlayerClaim() : null;
        }
        public boolean isOwnerOfRegion(String id, UUID player) { return false; }
        public boolean isOwnerOfClaim(int id, UUID player) { return id == 7 && OWNER.equals(player); }
        public boolean canPlayerAct(Player player, String world, Vector3i pos, RegionFlags flag) {
            if (getClaimAtBlock(world, pos.x, pos.z) == null) return true;
            if (OWNER.equals(player.id())) return true;
            return memberFlags.getOrDefault(player.id(), EnumSet.noneOf(RegionFlags.class)).contains(flag);
        }
    }

    /** An API of another version: no canPlayerAct. */
    public static final class OldApi {
        public AdminRegion getTopRegionAt(String world, int x, int y, int z) { return null; }
    }
}
