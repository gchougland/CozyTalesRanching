package com.hexvane.cozytalesranching.behaviour;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Plays a ranch animal's own clips (the model's animation sets: Graze, Happy, Love, EatTrough, ...).
 * <p>
 * They go on the {@code Status} slot, as vanilla NPCs play Eat, Sleep and the like. The {@code Emote} slot is for
 * cosmetic emote ids: the client looks the id up in the emote list, not in the model, and drops it ("No emote with id
 * Graze on entity with model ..." in the client log), which is why no behaviour clip was ever seen.
 * <p>
 * A clip on {@code Status} stays until cleared: a one-shot clip holds its last frame and keeps the legs from walking.
 * {@link #playFor} clears it after a while unless something else has replaced it; {@link #stop} clears it now.
 * <p>
 * The slot also survives a role change (the engine re-adds the entity with its components and a new {@link Ref}), so a
 * wild animal asleep when it is befriended would wake up a ranch animal still playing Sleep. {@link #playFor} therefore
 * finds the animal again by UUID when its time is up, and {@link #clearUntimed} lets the follow pass drop any clip
 * nobody is timing (a vanilla Sleep, or a behaviour clip whose session was lost).
 */
public final class AnimalClips {
    /** Entity UUID to the clip a {@link #playFor} will clear, and when. */
    private static final Map<UUID, Timed> TIMED = new ConcurrentHashMap<>();

    private record Timed(@Nonnull String clip, long untilMs) {}

    private AnimalClips() {}

    /** Whether the animal's model has this animation set. */
    public static boolean has(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String clip) {
        ModelComponent mc = store.getComponent(ref, ModelComponent.getComponentType());
        Model model = mc != null ? mc.getModel() : null;
        return model != null && model.getAnimationSetMap().containsKey(clip);
    }

    /** Plays the clip (restarting it) until {@link #stop}. False if the model lacks it. World thread. */
    public static boolean play(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String clip) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || !has(store, ref, clip)) return false;
        npc.playAnimation(ref, AnimationSlot.Status, clip, true, store);
        return true;
    }

    /** Plays the clip and clears the slot after {@code seconds}, unless another clip took the slot by then. World thread. */
    public static boolean playFor(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String clip, double seconds) {
        if (!play(store, ref, clip)) return false;
        World world = store.getExternalData().getWorld();
        long ms = Math.max(100L, (long) (seconds * 1000));
        UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID uuid = id != null ? id.getUuid() : null;
        Timed timed = new Timed(clip, System.currentTimeMillis() + ms + 1000);
        if (uuid != null) TIMED.put(uuid, timed);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (!world.isAlive()) return;
            world.execute(() -> {
                if (uuid != null) TIMED.remove(uuid, timed);
                // by UUID: a role change in between gives the same animal a new ref
                Ref<EntityStore> now = uuid != null ? world.getEntityStore().getRefFromUUID(uuid) : ref;
                if (now == null || !now.isValid()) return;
                Store<EntityStore> s = world.getEntityStore().getStore();
                if (clip.equals(current(s, now))) stop(s, now);
            });
        }, ms, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Clears the slot when it holds a clip no {@link #playFor} is timing. True if it cleared one. For animals no
     * behaviour session owns (followers): a Sleep carried over from the wild role, or any clip left behind. World thread.
     */
    public static boolean clearUntimed(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        String clip = current(store, ref);
        if (clip == null) return false;
        UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
        Timed t = id != null ? TIMED.get(id.getUuid()) : null;
        if (!shouldClear(clip, t == null ? null : t.clip(), t == null ? 0 : t.untilMs(), System.currentTimeMillis())) return false;
        if (id != null && t != null) TIMED.remove(id.getUuid(), t);
        stop(store, ref);
        return true;
    }

    /** The rule behind {@link #clearUntimed}: a clip stays only while a timer for that same clip is still running. */
    static boolean shouldClear(@Nullable String current, @Nullable String timedClip, long timedUntilMs, long nowMs) {
        if (current == null) return false;
        return timedClip == null || !timedClip.equals(current) || nowMs > timedUntilMs;
    }

    /** Clears the slot, back to the walk and idle clips. World thread. */
    public static void stop(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null && current(store, ref) != null) npc.playAnimation(ref, AnimationSlot.Status, null, true, store);
    }

    /** The clip the server last put on the slot, or null. */
    @Nullable
    public static String current(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ActiveAnimationComponent active = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (active == null) return null;
        String[] slots = active.getActiveAnimations();
        int i = AnimationSlot.Status.ordinal();
        return slots != null && i < slots.length ? slots[i] : null;
    }
}
