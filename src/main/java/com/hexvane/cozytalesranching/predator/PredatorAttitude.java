package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.animal.AnimalMarker;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.IAttitudeProvider;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The engine-level part of Calm. Every NPC decides how it feels about another entity through the world's
 * {@link AttitudeView}, a chain of providers asked in priority order (vanilla: the NPC's own attitude overrides, spawn
 * lineage, the attitude groups, then the role's defaults; the reputation plugin adds one the same way this does). This
 * provider is asked first:
 * <ul>
 * <li>A lured, calm or spooked predator answers {@link Attitude#IGNORE} for every player and every animal of a ranch
 * species. Vanilla detection only picks Hostile or Neutral targets, so it never notices, chases or bites them.</li>
 * <li>With {@code ProtectFromHostileMobs} on, every NPC ignores animals that carry the ranch marker. Their damage was
 * already cancelled (AnimalProtectionSystem); now wild wolves also stop chasing and gnawing at a cow they cannot hurt.</li>
 * </ul>
 * Anything else answers null and the vanilla chain decides. The NPC's attitude cache clears every 0.1 s, so a change
 * takes effect at once.
 */
final class PredatorAttitude extends StoreSystem<EntityStore> {
    /** Ahead of vanilla's per-NPC overrides (priority 0), so a calm predator's own memory of a player cannot win. */
    static final int PRIORITY = IAttitudeProvider.OVERRIDE_PRIORITY - 100;

    @Override
    public void onSystemAddedToStore(@Nonnull Store<EntityStore> store) {
        AttitudeView view = store.getResource(Blackboard.getResourceType()).getView(AttitudeView.class, 0);
        view.registerProvider(PRIORITY, PredatorAttitude::attitude);
    }

    @Override
    public void onSystemRemovedFromStore(@Nonnull Store<EntityStore> store) {}

    @Nullable
    static Attitude attitude(@Nonnull Ref<EntityStore> source, int sourceRoleIndex, @Nonnull Ref<EntityStore> target, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Predators p = Predators.get();
        if (p != null && !p.book.isEmpty()) {
            UUIDComponent id = accessor.getComponent(source, UUIDComponent.getComponentType());
            if (id != null && p.book.get(id.getUuid()) != null && isPlayerOrRanchAnimal(target, accessor)) return Attitude.IGNORE;
        }
        if (RanchConfig.get().protectFromHostileMobs()) {
            ComponentType<EntityStore, AnimalMarker> marker = AnimalMarker.type();
            if (marker != null && accessor.getComponent(target, marker) != null) return Attitude.IGNORE;
        }
        return null;
    }

    static boolean isPlayerOrRanchAnimal(@Nonnull Ref<EntityStore> target, @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (accessor.getComponent(target, Player.getComponentType()) != null) return true;
        NPCEntity npc = accessor.getComponent(target, NPCEntity.getComponentType());
        return npc != null && npc.getRoleName() != null && SpeciesRegistry.byRole(npc.getRoleName()).isPresent();
    }
}
