package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Makes owned ranch animals invulnerable ({@link ProtectionRules}): with {@code ProtectOwnedAnimals} on (default) no
 * player (the owner included, so a stray swing never kills a pet) and nothing in the world (falls, drowning, fire,
 * suffocation) hurts them; with {@code ProtectFromHostileMobs} on (default) no creature does either. A hit whose attacker
 * is gone (an arrow or bomb still flying, a burn or poison still ticking after the mob that started it died) is cancelled
 * under either switch. A cancelled hit does not knock the animal back either. Below the world's
 * floor the engine kills without a damage event; recovery brings the animal back.
 * Unowned adopted animals are left as vanilla had them. Runs only on damage events, only for entities
 * carrying the ranch marker.
 */
final class AnimalProtectionSystem extends DamageEventSystem {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final long MOB_LOG_INTERVAL_MS = 10_000;
    private static final Map<UUID, Long> MOB_LOGGED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GONE_LOGGED = new ConcurrentHashMap<>();

    private final PersistentAnimalRegistry registry;
    private final ComponentType<EntityStore, AnimalMarker> markerType;
    private final ComponentType<EntityStore, UUIDComponent> uuidType = UUIDComponent.getComponentType();
    private final ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
    private final Query<EntityStore> query;

    AnimalProtectionSystem(@Nonnull PersistentAnimalRegistry registry, @Nonnull ComponentType<EntityStore, AnimalMarker> markerType) {
        this.registry = registry;
        this.markerType = markerType;
        this.query = Query.and(markerType, npcType, uuidType);
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb, @Nonnull Damage damage) {
        if (damage.isCancelled()) return;
        RanchConfig config = RanchConfig.get();
        boolean players = config.protectOwnedAnimals();
        boolean mobs = config.protectFromHostileMobs();
        if (!players && !mobs) return;
        AnimalMarker marker = chunk.getComponent(index, markerType);
        UUIDComponent uuid = chunk.getComponent(index, uuidType);
        NPCEntity npc = chunk.getComponent(index, npcType);
        if (marker == null || marker.animalId() == null || uuid == null || npc == null || npc.getRoleName() == null) return;
        AnimalRecord r = registry.store().get(marker.animalId());
        if (r == null || r.ownerId == null || !registry.isCurrent(r.animalId, uuid.getUuid(), marker.generation())) return;
        Ref<EntityStore> self = chunk.getReferenceTo(index);
        if (!CompatGuard.isManageable(self, cb, npc.getRoleName())) return;

        Damage.Source src = damage.getSource();
        Ref<EntityStore> attacker = src instanceof Damage.EntitySource source ? source.getRef() : null;
        boolean attackerValid = attacker != null && attacker.isValid();
        boolean orphanEffect = src instanceof ActiveEntityEffect effect && effect.getOwner() != null;
        ProtectionRules.Source from = ProtectionRules.classify(attacker != null, attackerValid,
            attackerValid && cb.getComponent(attacker, Player.getComponentType()) != null, orphanEffect);
        if (!ProtectionRules.cancels(true, from, players, mobs)) return;
        damage.setCancelled(true);
        // as vanilla does for a cancelled hit (spawn protection, no PvP): the blow does not fling the animal either
        if (damage.getIfPresentMetaObject(Damage.KNOCKBACK_COMPONENT) != null) cb.tryRemoveComponent(self, KnockbackComponent.getComponentType());
        if (from == ProtectionRules.Source.CREATURE || from == ProtectionRules.Source.GONE) noteMobBlocked(r, from == ProtectionRules.Source.CREATURE ? attacker : null, cb, damage.getAmount());
        else if (from == ProtectionRules.Source.WORLD) AnimalEntitySystem.debug("Blocked " + damage.getAmount() + " world damage to owned " + r.speciesId + " " + r.animalId);
    }

    /**
     * One INFO line per animal per {@link #MOB_LOG_INTERVAL_MS} (counted apart for live mobs and for attackers that are
     * gone), so a mob gnawing at a cow or a burn ticking on it does not flood the log.
     */
    private static void noteMobBlocked(@Nonnull AnimalRecord r, @Nullable Ref<EntityStore> attacker, @Nonnull CommandBuffer<EntityStore> cb, float amount) {
        long now = System.currentTimeMillis();
        Map<UUID, Long> logged = attacker == null ? GONE_LOGGED : MOB_LOGGED;
        Long last = logged.get(r.animalId);
        if (last != null && now - last < MOB_LOG_INTERVAL_MS) return;
        logged.put(r.animalId, now);
        NPCEntity npc = attacker == null ? null : cb.getComponent(attacker, NPCEntity.getComponentType());
        String who = attacker == null ? "an attacker that is gone" : npc != null && npc.getRoleName() != null ? npc.getRoleName() : "a creature";
        LOG.info("Blocked " + amount + " damage from " + who + " to owned " + r.speciesId + " " + r.animalId + " (hostile-mob protection)");
    }
}
