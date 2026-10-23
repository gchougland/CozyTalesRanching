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
 */
public final class AnimalClips {
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
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (!world.isAlive()) return;
            world.execute(() -> {
                if (!ref.isValid()) return;
                Store<EntityStore> s = world.getEntityStore().getStore();
                if (clip.equals(current(s, ref))) stop(s, ref);
            });
        }, ms, TimeUnit.MILLISECONDS);
        return true;
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
