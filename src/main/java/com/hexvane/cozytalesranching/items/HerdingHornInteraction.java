package com.hexvane.cozytalesranching.items;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SimpleInteraction;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code "Type": "CozyRanchHerdingHorn"} on the Herding Horn: blowing it calls every animal of the player's that is
 * loaded within {@code Radius} blocks (default 32) and not already following, and they start following, as
 * {@code /ranch follow} does for 16 blocks. Needs the {@code cozyranch.follow} node. Animals inside a house at night
 * have no entity, so they stay in. Runs on the world thread with {@link World#execute}; it walks the player's own
 * records (no entity scan), once per blow, at most once per 5 seconds per player. No systems, no ticks, no saved data.
 */
public final class HerdingHornInteraction extends SimpleInstantInteraction {
    public static final String TYPE = "CozyRanchHerdingHorn";
    public static final String DEFAULT_SOUND = "SFX_CozyRanch_Herding_Horn";
    private static final Map<UUID, Long> LAST_BLOWN = new ConcurrentHashMap<>();

    public static final BuilderCodec<HerdingHornInteraction> CODEC = BuilderCodec
        .builder(HerdingHornInteraction.class, HerdingHornInteraction::new, SimpleInstantInteraction.CODEC)
        .documentation("Calls the player's nearby ranch animals to follow them.")
        .appendInherited(
            new KeyedCodec<>("Radius", Codec.DOUBLE),
            (i, v) -> i.radius = v,
            i -> i.radius,
            (o, p) -> o.radius = p.radius
        )
        .documentation("How far the call carries, in blocks (4 to 64, default 32).")
        .add()
        .appendInherited(
            new KeyedCodec<>("Sound", Codec.STRING),
            (i, v) -> i.sound = v,
            i -> i.sound,
            (o, p) -> o.sound = p.sound
        )
        .documentation("Sound event played where the player stands.")
        .add()
        .build();

    private double radius = HerdingRules.DEFAULT_RADIUS;
    private String sound = DEFAULT_SOUND;

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> player = context.getEntity();
        if (buffer == null || player == null || !player.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRef = buffer.getComponent(player, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = buffer.getExternalData().getWorld();
        double reach = HerdingRules.radius(radius);
        String soundId = sound == null || sound.isBlank() ? DEFAULT_SOUND : sound;
        world.execute(() -> blow(world, playerRef, reach, soundId));
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {}

    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new SimpleInteraction();
    }

    /** World thread, outside ticks. */
    static void blow(@Nonnull World world, @Nonnull PlayerRef player, double reach, @Nonnull String soundId) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        UUID uuid = player.getUuid();
        if (!RanchPermissions.has(uuid, RanchPermissions.FOLLOW)) {
            RanchChat.tell(player, Message.translation("cozyranch_items.herding_horn.no_permission"));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_BLOWN.get(uuid);
        if (!HerdingRules.ready(last == null ? 0L : last, now, HerdingRules.DEFAULT_COOLDOWN_MS)) return;
        LAST_BLOWN.put(uuid, now);
        Store<EntityStore> store = world.getEntityStore().getStore();
        TransformComponent me = store.getComponent(ref, TransformComponent.getComponentType());
        if (me == null) return;
        Vector3d at = me.getPosition();
        RanchSounds.play(soundId, at.x, at.y + 1.0, at.z, store);
        int called = 0;
        for (AnimalRecord record : Ranch.animals().ownedBy(uuid)) {
            if (record.following || record.entityUuid == null) continue;
            Ref<EntityStore> animal = store.getExternalData().getRefFromUUID(record.entityUuid);
            if (animal == null || !animal.isValid()) continue;
            TransformComponent t = store.getComponent(animal, TransformComponent.getComponentType());
            if (t == null) continue;
            Vector3d p = t.getPosition();
            if (!HerdingRules.inReach(p.x - at.x, p.y - at.y, p.z - at.z, reach)) continue;
            TamingApi.setFollowing(record, true, animal, player, store);
            called++;
        }
        RanchChat.tell(player, called == 0
            ? Message.translation("cozyranch_items.herding_horn.none")
            : Message.translation("cozyranch_items.herding_horn.called").param("count", called));
    }
}
