package com.hexvane.cozytalesranching.behaviour;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Ranch animals do not set off blocks they walk into. Vanilla water lilies (and other soft plants) have a
 * {@code CollisionEnter} interaction that breaks the block; its game mode condition only applies to players, so any NPC
 * that steps on a lily breaks it. NPC motion controllers run those block triggers unless {@code enableTriggers} is off,
 * and no role field reaches it, so it is switched off here for our own roles ({@code CozyRanch_*}) when the entity is
 * added: on spawn, on chunk load and after every role swap (a swap removes and re-adds the entity). Block damage
 * (cactus, fire) is a separate switch and stays on. One string check per NPC added; nothing per tick.
 */
final class GentleStepSystem extends RefSystem<EntityStore> {
    static final String ROLE_PREFIX = "CozyRanch_";
    /** Off only for tests ({@code /ranch behaviour gentle off}); applies to animals added afterwards. */
    private static volatile boolean enabled = true;
    /** How many ranch animals had their block triggers switched off since start (shown by the status command). */
    static final java.util.concurrent.atomic.AtomicInteger APPLIED = new java.util.concurrent.atomic.AtomicInteger();

    static void setEnabled(boolean on) {
        enabled = on;
    }

    private final ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
    private final Set<Dependency<EntityStore>> dependencies = Set.of(new SystemDependency<>(Order.AFTER, RoleSystems.RoleActivateSystem.class));

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return npcType;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        NPCEntity npc = cb.getComponent(ref, npcType);
        if (!enabled || npc == null || !isRanchRole(npc.getRoleName())) return;
        Role role = npc.getRole();
        if (role == null) return;
        MotionController active = role.getActiveMotionController();
        if (active instanceof MotionControllerBase base) {
            base.setEnableTriggers(false);
            APPLIED.incrementAndGet();
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {}

    static boolean isRanchRole(String roleName) {
        return roleName != null && roleName.startsWith(ROLE_PREFIX);
    }
}
