package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.animal.AnimalMarker;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleMatch;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.InteractableUpdate;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.InventorySetActiveSlotEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * The Use prompt on wild animals the mod befriends ("Press [F] to befriend" with a food it eats in hand, "Press [F]
 * to say hello" otherwise). No vanilla role is changed.
 *
 * <p>Why this is needed (read in the 0.7 source and seen with the test player): vanilla livestock roles send their
 * "tame" hint once, through {@code StateSupport.setInteractable}, in the tick that adds the {@code Interactable}
 * marker. The marker's own replication ({@code MarkerReplicationSystems.EntityTrackerUpdate}) then sends an empty
 * {@code InteractableUpdate} to every viewer, and to every viewer who sees the animal later, which clears the hint.
 * The role never sends it again because its hint never changes. Frogs have no interaction instruction at all.
 *
 * <p>So, just before the tick's entity packets are sent, {@link Repair} looks at what is queued for each player
 * who sees an interactable wild animal. If the last Interactable update is vanilla's (empty or "tame"), ours is queued
 * after it. The role keeps quiet afterwards because its own hint has not changed. Cost: one map lookup per viewer per
 * interactable NPC per tick (only NPCs near players carry the marker); real work only when an Interactable update is
 * queued, and when a player changes hotbar slot (for wild animals within 12 blocks of that player).
 */
public final class WildPrompts {
    /** Players see a held-food prompt change only for animals this close, which is well beyond Use reach. */
    private static final double HOTBAR_REFRESH_RADIUS = 12.0;
    public static final String CRICKETS_ITEM = "CozyRanch_Food_Crickets";

    /** Players told how the Jar of Crickets works, once each per server run. */
    private static final Set<UUID> TOLD_ABOUT_CRICKETS = ConcurrentHashMap.newKeySet();

    private WildPrompts() {}

    /** The species a wild animal belongs to when the mod handles Use on it, or null. */
    @Nullable
    static SpeciesDefinition wildSpecies(@Nonnull Ref<EntityStore> animal, @Nonnull ComponentAccessor<EntityStore> accessor) {
        NPCEntity npc = accessor.getComponent(animal, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) return null;
        RoleMatch match = SpeciesRegistry.byRole(npc.getRoleName()).orElse(null);
        if (match == null || (match.kind() != RoleKind.WILD && match.kind() != RoleKind.WILD_BABY)) return null;
        ComponentType<EntityStore, AnimalMarker> marker = AnimalMarker.type();
        if (marker != null && accessor.getComponent(animal, marker) != null) return null;
        if (!RanchConfig.get().isSpeciesEnabled(match.species().Id)) return null;
        if (!CompatGuard.isManageable(animal, accessor, npc.getRoleName())) return null;
        return match.species();
    }

    @Nonnull
    static String hintFor(@Nonnull SpeciesDefinition species, @Nonnull Ref<EntityStore> viewer, @Nonnull ComponentAccessor<EntityStore> accessor) {
        ItemStack held = InventoryComponent.getItemInHand(accessor, viewer);
        String itemId = ItemStack.isEmpty(held) ? null : held.getItemId();
        return TamingRules.wildHintKey(TamingRules.foodKind(species, itemId));
    }

    /**
     * Last step before the tick's entity packets go out: wherever an Interactable update for a wild animal is queued
     * for a player (vanilla's empty marker update, or the role's own "tame" hint), our hint is queued after it, so
     * ours is what the client keeps. Ordered after the NPC behaviour tick and the queue-update group, before SendPackets.
     */
    static final class Repair extends EntityTickingSystem<EntityStore> {
        private final Query<EntityStore> query = Query.and(EntityTrackerSystems.Visible.getComponentType(), Interactable.getComponentType(), NPCEntity.getComponentType());
        /** After the NPC behaviour tick (the role's own hint) and the marker updates, before packets go out. */
        private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, RoleSystems.BehaviourTickSystem.class),
            new SystemGroupDependency<>(Order.AFTER, EntityTrackerSystems.QUEUE_UPDATE_GROUP),
            new SystemDependency<>(Order.BEFORE, EntityTrackerSystems.SendPackets.class));

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }

        @Override
        public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
            EntityTrackerSystems.Visible visible = chunk.getComponent(index, EntityTrackerSystems.Visible.getComponentType());
            if (visible == null || visible.visibleTo.isEmpty()) return;
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            SpeciesDefinition species = null;
            boolean checked = false;
            for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> e : visible.visibleTo.entrySet()) {
                EntityTrackerSystems.EntityViewer viewer = e.getValue();
                EntityTrackerSystems.EntityUpdate pending = viewer.updates.get(ref);
                if (pending == null || !needsRepair(pending.toUpdatesArray())) continue;
                if (!checked) {
                    species = wildSpecies(ref, cb);
                    checked = true;
                }
                if (species == null) return;
                Ref<EntityStore> player = e.getKey();
                if (!player.isValid() || !viewer.visible.contains(ref) || cb.getComponent(player, PlayerRef.getComponentType()) == null) continue;
                viewer.queueUpdate(ref, new InteractableUpdate(hintFor(species, player, cb)));
            }
        }

        /** True when the last Interactable update queued is not one of ours (vanilla's empty one or its own hint). */
        static boolean needsRepair(@Nullable ComponentUpdate[] updates) {
            if (updates == null) return false;
            for (int i = updates.length - 1; i >= 0; i--) {
                if (updates[i] instanceof InteractableUpdate u) {
                    return u.interactionHint == null || !(u.interactionHint.equals(TamingRules.wildHintKey(TamingRules.FoodKind.NONE)) || u.interactionHint.equals(TamingRules.wildHintKey(TamingRules.FoodKind.BASIC)));
                }
            }
            return false;
        }
    }

    /**
     * A new hotbar slot can turn "say hello" into "befriend" (or back) for wild animals near the player. Holding the
     * Jar of Crickets for the first time also explains it, since frogs have no Use prompt (they flee before Use reach).
     */
    static final class OnHotbar extends EntityEventSystem<EntityStore, InventorySetActiveSlotEvent> {
        OnHotbar() {
            super(InventorySetActiveSlotEvent.class);
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb, @Nonnull InventorySetActiveSlotEvent event) {
            Ref<EntityStore> player = chunk.getReferenceTo(index);
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            ItemStack held = InventoryComponent.getItemInHand(cb, player);
            if (playerRef != null && !ItemStack.isEmpty(held) && CRICKETS_ITEM.equals(held.getItemId()) && TOLD_ABOUT_CRICKETS.add(playerRef.getUuid())) {
                playerRef.sendMessage(Message.translation("cozyranch_taming.crickets.tip"));
            }
            EntityTrackerSystems.EntityViewer viewer = cb.getComponent(player, EntityTrackerSystems.EntityViewer.getComponentType());
            TransformComponent at = cb.getComponent(player, TransformComponent.getComponentType());
            if (viewer == null || at == null) return;
            Vector3d p = at.getPosition();
            double r2 = HOTBAR_REFRESH_RADIUS * HOTBAR_REFRESH_RADIUS;
            for (Ref<EntityStore> animal : viewer.visible) {
                if (!animal.isValid() || cb.getComponent(animal, Interactable.getComponentType()) == null) continue;
                TransformComponent t = cb.getComponent(animal, TransformComponent.getComponentType());
                if (t == null || t.getPosition().distanceSquared(p) > r2) continue;
                SpeciesDefinition species = wildSpecies(animal, cb);
                if (species == null) continue;
                viewer.queueUpdate(animal, new InteractableUpdate(TamingRules.wildHintKey(TamingRules.foodKind(species, ItemStack.isEmpty(held) ? null : held.getItemId()))));
            }
        }
    }
}
