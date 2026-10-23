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
 * Cancels damage to owned ranch animals: from every player (the owner included, so a stray swing never
 * kills a pet) when {@code ProtectOwnedAnimals} is on, and from other creatures, anywhere, when
 * {@code ProtectFromHostileMobs} is on (on by default).
 * Falls, fire and drowning still hurt; recovery covers deaths.
 * Unowned adopted animals are left as vanilla had them. Runs only on damage events, only for entities
 * carrying the ranch marker.
 */
final class AnimalProtectionSystem extends DamageEventSystem {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final long MOB_LOG_INTERVAL_MS = 10_000;
    private static final Map<UUID, Long> MOB_LOGGED = new ConcurrentHashMap<>();

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
        if (!(damage.getSource() instanceof Damage.EntitySource source)) return;

        AnimalMarker marker = chunk.getComponent(index, markerType);
        UUIDComponent uuid = chunk.getComponent(index, uuidType);
        NPCEntity npc = chunk.getComponent(index, npcType);
        if (marker == null || marker.animalId() == null || uuid == null || npc == null || npc.getRoleName() == null) return;
        AnimalRecord r = registry.store().get(marker.animalId());
        if (r == null || r.ownerId == null || !registry.isCurrent(r.animalId, uuid.getUuid(), marker.generation())) return;
        Ref<EntityStore> self = chunk.getReferenceTo(index);
        if (!CompatGuard.isManageable(self, cb, npc.getRoleName())) return;

        Ref<EntityStore> attacker = source.getRef();
        if (!attacker.isValid()) return;
        boolean fromPlayer = cb.getComponent(attacker, Player.getComponentType()) != null;
        if (fromPlayer ? players : mobs) {
            damage.setCancelled(true);
            if (!fromPlayer) noteMobBlocked(r, attacker, cb, damage.getAmount());
        }
    }

    /** One INFO line per animal per {@link #MOB_LOG_INTERVAL_MS}, so a mob gnawing at a cow does not flood the log. */
    private static void noteMobBlocked(@Nonnull AnimalRecord r, @Nonnull Ref<EntityStore> attacker, @Nonnull CommandBuffer<EntityStore> cb, float amount) {
        long now = System.currentTimeMillis();
        Long last = MOB_LOGGED.get(r.animalId);
        if (last != null && now - last < MOB_LOG_INTERVAL_MS) return;
        MOB_LOGGED.put(r.animalId, now);
        NPCEntity npc = cb.getComponent(attacker, NPCEntity.getComponentType());
        String who = npc != null && npc.getRoleName() != null ? npc.getRoleName() : "a creature";
        LOG.info("Blocked " + amount + " damage from " + who + " to owned " + r.speciesId + " " + r.animalId + " (hostile-mob protection)");
    }
}
