package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Entity-side helpers shared by interactions, commands and the follow pass. World thread, outside system ticks. */
final class AnimalControl {
    static final String STATE_FOLLOW = "Follow";
    static final String STATE_STAY = "Stay";
    static final String STATE_IDLE = "Idle";
    static final String STATE_PANIC = "Panic";
    static final String HAPPY_ANIMATION = "Happy";
    static final String HEARTS = "Hearts";
    static final String HEARTS_SMALL = "Hearts_Subtle";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AnimalControl() {}

    @Nonnull
    static Message displayName(@Nonnull AnimalRecord record) {
        if (record.name != null && !record.name.isEmpty()) return Message.raw(record.name);
        return SpeciesRegistry.byId(record.speciesId).map(AnimalControl::speciesName).orElse(Message.raw(record.speciesId));
    }

    @Nonnull
    static Message speciesName(@Nonnull SpeciesDefinition species) {
        return Message.translation(species.NameKey);
    }

    /** Points the animal at its owner, or makes it stand where it is. Only for our ranch roles; others ignore states. */
    static void applyFollow(@Nonnull Ref<EntityStore> animal, @Nonnull AnimalRecord record, @Nullable Ref<EntityStore> owner, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        StateSupport states = store.getComponent(animal, StateSupport.getComponentType());
        if (npc == null || states == null || npc.getRole() == null || npc.getRole().isRoleChangeRequested()) return;
        MarkedEntitySupport marked = store.getComponent(animal, MarkedEntitySupport.getComponentType());
        if (record.following) {
            if (marked != null && owner != null && !owner.equals(marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT))) {
                marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, owner);
            }
            if (!inState(states, STATE_FOLLOW)) states.setState(animal, STATE_FOLLOW, null, store);
        } else if (!inState(states, STATE_STAY)) {
            if (marked != null) marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, null);
            TransformComponent t = store.getComponent(animal, TransformComponent.getComponentType());
            if (t != null) npc.setLeashPoint(new Vector3d(t.getPosition()));
            states.setState(animal, STATE_STAY, null, store);
        }
    }

    static boolean inState(@Nonnull StateSupport states, @Nonnull String state) {
        String name = states.getStateName();
        return name.equals(state) || name.startsWith(state + ".");
    }

    /** Hearts above the animal and its Happy animation when the model has one. */
    static void cheer(@Nonnull Ref<EntityStore> animal, @Nonnull String particles, @Nonnull Store<EntityStore> store) {
        TransformComponent t = store.getComponent(animal, TransformComponent.getComponentType());
        if (t != null) {
            Vector3d p = new Vector3d(t.getPosition()).add(0, 1.0, 0);
            ParticleUtil.spawnParticleEffect(particles, p, store);
        }
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        ModelComponent mc = store.getComponent(animal, ModelComponent.getComponentType());
        Model model = mc != null ? mc.getModel() : null;
        if (npc != null && model != null && model.getAnimationSetMap().containsKey(HAPPY_ANIMATION)) {
            npc.playAnimation(animal, AnimationSlot.Emote, HAPPY_ANIMATION, store);
        }
    }

    /** One of {@link RanchSounds} at the animal, heard by players nearby. */
    static void sound(@Nonnull Ref<EntityStore> animal, @Nonnull String soundEventId, @Nonnull Store<EntityStore> store) {
        TransformComponent t = store.getComponent(animal, TransformComponent.getComponentType());
        if (t != null) RanchSounds.play(soundEventId, t.getPosition().x, t.getPosition().y + 0.5, t.getPosition().z, store);
    }

    static void startle(@Nonnull Ref<EntityStore> animal, @Nonnull ComponentAccessor<EntityStore> accessor) {
        StateSupport states = accessor.getComponent(animal, StateSupport.getComponentType());
        if (states != null) states.setState(animal, STATE_PANIC, null, accessor);
    }

    /** Asks the engine to swap the animal's role next tick, keeping its UUID and components. */
    static boolean swapRole(@Nonnull Ref<EntityStore> animal, @Nonnull String roleId, @Nullable String state, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) return false;
        if (roleId.equals(npc.getRoleName())) return true;
        if (npc.getRole().isRoleChangeRequested()) return false;
        int index = NPCPlugin.get().getIndex(roleId);
        if (index < 0) {
            LOGGER.atWarning().log("Role %s is not loaded; cannot swap %s", roleId, npc.getRoleName());
            return false;
        }
        RoleChangeSystem.requestRoleChange(animal, npc.getRole(), index, true, state, null, true, store);
        return true;
    }

    @Nonnull
    static Optional<String> ranchRole(@Nonnull SpeciesDefinition species, @Nullable String fromRole, boolean baby) {
        String role = species.ranchRoleFor(fromRole, baby);
        return role.isEmpty() ? Optional.empty() : Optional.of(role);
    }

    @Nonnull
    static Optional<String> wildRole(@Nonnull SpeciesDefinition species, @Nullable String currentRole, boolean baby) {
        String role = species.wildRoleFor(currentRole, baby);
        return role.isEmpty() ? Optional.empty() : Optional.of(role);
    }

    @Nullable
    static String roleName(@Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        return npc == null ? null : npc.getRoleName();
    }
}
