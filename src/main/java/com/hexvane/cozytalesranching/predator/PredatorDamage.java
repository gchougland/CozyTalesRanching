package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.predator.CalmBook.Entry;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The last line of Calm: in the damage filter group, damage dealt by a lured, calm or spooked predator is cancelled,
 * whoever it would hit (a bite already swinging when Calm started cannot land). A player hitting one of them startles
 * it (it flees). Returns at once while the {@link CalmBook} is empty, so normal play pays one map check per hit.
 */
final class PredatorDamage extends DamageEventSystem {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb, @Nonnull Damage damage) {
        Predators p = Predators.get();
        if (p == null || p.book.isEmpty() || damage.isCancelled()) return;
        if (!(damage.getSource() instanceof Damage.EntitySource source)) return;
        Ref<EntityStore> attacker = source.getRef();
        if (attacker == null || !attacker.isValid()) return;
        UUIDComponent attackerId = cb.getComponent(attacker, UUIDComponent.getComponentType());
        Entry calmAttacker = attackerId == null ? null : p.book.get(attackerId.getUuid());
        if (calmAttacker != null) {
            damage.setCancelled(true);
            LOGGER.atInfo().log("Blocked %.1f damage from %s predator %s (Calm)", damage.getAmount(), calmAttacker.phase, calmAttacker.speciesId);
            return;
        }
        UUIDComponent targetId = chunk.getComponent(index, UUIDComponent.getComponentType());
        Entry calmTarget = targetId == null ? null : p.book.get(targetId.getUuid());
        if (calmTarget == null) return;
        PlayerRef player = cb.getComponent(attacker, PlayerRef.getComponentType());
        if (!CalmRules.hitStartles(calmTarget.phase, player != null)) return;
        World world = store.getExternalData().getWorld();
        UUID who = player.getUuid();
        UUID entity = calmTarget.entity;
        world.execute(() -> p.startle(world, entity, who));
    }
}
