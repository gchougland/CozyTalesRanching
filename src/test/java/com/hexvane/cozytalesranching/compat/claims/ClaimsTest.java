package com.hexvane.cozytalesranching.compat.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.compat.claims.fake.FakeClaimsWorld;
import com.hexvane.cozytalesranching.compat.claims.fake.FakeClaimsWorld.RegionFlags;
import com.hexvane.cozytalesranching.config.RanchConfig;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClaimsTest {
    private static final UUID OWNER = FakeClaimsWorld.OWNER;
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final String PLUGIN = FakeClaimsWorld.Plugin.class.getName();
    private static final String PREFIX = FakeClaimsWorld.class.getName() + "$";

    /** A claims mod in a map: one claim at x 0..15, flags per player. */
    private record Fake(UUID owner, Map<UUID, Set<ClaimsAdapter.Flag>> flags) implements ClaimsAdapter {
        @Override public Claim claimAt(String world, int x, int y, int z) {
            return x >= 0 && x < 16 ? new Claim("claim", "1") : null;
        }
        @Override public boolean isOwner(Claim claim, UUID player) { return owner.equals(player); }
        @Override public boolean allows(UUID player, String world, int x, int y, int z, Flag flag) {
            return flags.getOrDefault(player, Set.of()).contains(flag);
        }
    }

    private static final Fake CLAIMS = new Fake(OWNER, Map.of(MEMBER, Set.of(ClaimsAdapter.Flag.INTERACT)));

    @AfterEach
    void reset() {
        Claims.installPluginsForTests(null);
        Claims.installForTests(null);
        FakeClaimsWorld.memberFlags.clear();
    }

    @Test
    void claimOwnerMayDoEverythingInTheirClaim() {
        for (Claims.Action a : Claims.Action.values()) assertTrue(Claims.claimAllows(CLAIMS, OWNER, "w", 3, 64, 3, a), a.name());
    }

    @Test
    void memberGetsExactlyTheFlagsTheOwnerGave() {
        assertTrue(Claims.claimAllows(CLAIMS, MEMBER, "w", 3, 64, 3, Claims.Action.MANAGE));
        assertFalse(Claims.claimAllows(CLAIMS, MEMBER, "w", 3, 64, 3, Claims.Action.COLLECT));
        assertFalse(Claims.claimAllows(CLAIMS, MEMBER, "w", 3, 64, 3, Claims.Action.BREAK));
        assertFalse(Claims.claimAllows(CLAIMS, STRANGER, "w", 3, 64, 3, Claims.Action.MANAGE));
    }

    @Test
    void outsideAClaimTheClaimSaysNothing() {
        for (Claims.Action a : Claims.Action.values()) {
            assertFalse(Claims.claimAllows(CLAIMS, OWNER, "w", 100, 64, 3, a));
            assertFalse(Claims.claimAllows(ClaimsAdapter.NONE, OWNER, "w", 3, 64, 3, a));
        }
    }

    @Test
    void actionsMapToTheClaimFlags() {
        assertEquals(ClaimsAdapter.Flag.INTERACT, Claims.Action.MANAGE.flag);
        assertEquals(ClaimsAdapter.Flag.CONTAINER, Claims.Action.COLLECT.flag);
        assertEquals(ClaimsAdapter.Flag.BREAK, Claims.Action.BREAK.flag);
    }

    /** A server without the claims mod: other plugins are loaded, nothing binds, nothing throws, no claim anywhere. */
    @Test
    void absentClaimsModMeansNoClaims() {
        Claims.installPluginsForTests(() -> List.of(new Object(), "AnotherPlugin"));
        assertSame(ClaimsAdapter.NONE, Claims.adapter());
        for (Claims.Action a : Claims.Action.values()) assertFalse(Claims.claimAllows(OWNER, "w", 3, 64, 3, a));
    }

    @Test
    void configSwitchTurnsTheIntegrationOff() {
        assertTrue(RanchConfig.get().claimsIntegration(), "on by default");
        RanchConfig off = RanchConfig.CODEC.decode(org.bson.BsonDocument.parse("{\"ClaimsIntegration\": false}"), new com.hypixel.hytale.codec.ExtraInfo());
        assertNotNull(off);
        assertFalse(off.claimsIntegration());
    }

    @Test
    void bindFailsSoftWhenThePluginIsAbsent() {
        assertNull(QuestLinesClaimsAdapter.bind(List.of(new Object(), "x"), PLUGIN, PREFIX, FakeClaimsWorld.Player.class, FakeClaimsWorld.Player::new));
        assertNull(QuestLinesClaimsAdapter.bind(List.of(), QuestLinesClaimsAdapter.PLUGIN_CLASS, QuestLinesClaimsAdapter.DATA_PREFIX,
            FakeClaimsWorld.Player.class, FakeClaimsWorld.Player::new));
    }

    @Test
    void bindRetriesWhileTheApiIsNotReady() {
        assertNull(QuestLinesClaimsAdapter.bind(List.of(new FakeClaimsWorld.Plugin(null)), PLUGIN, PREFIX, FakeClaimsWorld.Player.class, FakeClaimsWorld.Player::new));
    }

    @Test
    void bindGivesUpQuietlyOnAnUnknownApiShape() {
        ClaimsAdapter a = QuestLinesClaimsAdapter.bind(List.of(new FakeClaimsWorld.Plugin(new FakeClaimsWorld.OldApi())), PLUGIN, PREFIX,
            FakeClaimsWorld.Player.class, FakeClaimsWorld.Player::new);
        assertSame(ClaimsAdapter.NONE, a);
        ClaimsAdapter wrongData = QuestLinesClaimsAdapter.bind(List.of(new FakeClaimsWorld.Plugin(new FakeClaimsWorld.Api())), PLUGIN, "no.such.package.",
            FakeClaimsWorld.Player.class, FakeClaimsWorld.Player::new);
        assertSame(ClaimsAdapter.NONE, wrongData);
    }

    /** The reflective binding against an API of the same shape as QuestLinesClaims' public API. */
    @Test
    void reflectionBindingAnswersThroughTheApi() {
        ClaimsAdapter a = QuestLinesClaimsAdapter.bind(List.of(new Object(), new FakeClaimsWorld.Plugin(new FakeClaimsWorld.Api())), PLUGIN, PREFIX,
            FakeClaimsWorld.Player.class, FakeClaimsWorld.Player::new);
        assertNotNull(a);
        assertEquals("QuestLinesClaims", a.name());
        ClaimsAdapter.Claim claim = a.claimAt("w", 5, 70, 5);
        assertEquals(new ClaimsAdapter.Claim("claim", "7"), claim);
        assertNull(a.claimAt("w", 50, 70, 5));
        assertNull(a.claimAt("other", 5, 70, 5));
        assertTrue(a.isOwner(claim, OWNER));
        assertFalse(a.isOwner(claim, MEMBER));

        FakeClaimsWorld.memberFlags.put(MEMBER, EnumSet.of(RegionFlags.INTERACT, RegionFlags.BREAK));
        assertTrue(a.allows(MEMBER, "w", 5, 70, 5, ClaimsAdapter.Flag.INTERACT));
        assertTrue(a.allows(MEMBER, "w", 5, 70, 5, ClaimsAdapter.Flag.BREAK));
        assertFalse(a.allows(MEMBER, "w", 5, 70, 5, ClaimsAdapter.Flag.CONTAINER));
        assertFalse(a.allows(STRANGER, "w", 5, 70, 5, ClaimsAdapter.Flag.INTERACT));

        Claims.installForTests(a);
        assertTrue(Claims.claimAllows(OWNER, "w", 5, 70, 5, Claims.Action.BREAK), "claim owner always");
        assertTrue(Claims.claimAllows(MEMBER, "w", 5, 70, 5, Claims.Action.MANAGE));
        assertFalse(Claims.claimAllows(MEMBER, "w", 5, 70, 5, Claims.Action.COLLECT));
        assertFalse(Claims.claimAllows(MEMBER, "w", 50, 70, 5, Claims.Action.MANAGE), "wilderness: the claim says nothing even where its rules allow");
    }

    @Test
    void offlinePlayersAreNeverAllowedByFlag() {
        ClaimsAdapter a = QuestLinesClaimsAdapter.bind(List.of(new FakeClaimsWorld.Plugin(new FakeClaimsWorld.Api())), PLUGIN, PREFIX,
            FakeClaimsWorld.Player.class, id -> null);
        assertNotNull(a);
        FakeClaimsWorld.memberFlags.put(MEMBER, EnumSet.of(RegionFlags.INTERACT));
        assertFalse(a.allows(MEMBER, "w", 5, 70, 5, ClaimsAdapter.Flag.INTERACT));
    }
}
