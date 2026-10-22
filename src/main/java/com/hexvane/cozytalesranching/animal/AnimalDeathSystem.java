package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.api.AnimalView;
import com.hexvane.cozytalesranching.api.event.AnimalDiedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * When the current incarnation of a ranch animal dies: fires {@link AnimalDiedEvent} and marks the
 * record MISSING so recovery brings the animal back. The one exception is an unowned adopted animal
 * killed by a player, which is gone for good as it would be in vanilla (otherwise it would be an
 * endless source of drops). Runs once per death, only for entities carrying the ranch marker.
 */
final class AnimalDeathSystem extends DeathSystems.OnDeathSystem {
    private final PersistentAnimalRegistry registry;
    private final ComponentType<EntityStore, AnimalMarker> markerType;
    private final ComponentType<EntityStore, UUIDComponent> uuidType = UUIDComponent.getComponentType();
    private final Query<EntityStore> query;

    AnimalDeathSystem(@Nonnull PersistentAnimalRegistry registry, @Nonnull ComponentType<EntityStore, AnimalMarker> markerType) {
        this.registry = registry;
        this.markerType = markerType;
        this.query = markerType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent death, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        AnimalMarker marker = cb.getComponent(ref, markerType);
        UUIDComponent uuid = cb.getComponent(ref, uuidType);
        if (marker == null || marker.animalId() == null || uuid == null) return;
        UUID animalId = marker.animalId();
        if (!registry.isCurrent(animalId, uuid.getUuid(), marker.generation())) return;
        // presence is cleared exactly once per incarnation, so a repeated death callback does nothing
        if (!AnimalPresence.removeIfRef(animalId, ref)) return;
        AnimalRecord r = registry.store().get(animalId);
        if (r == null) return;

        UUID killerId = null;
        boolean killedByPlayer = false;
        Damage info = death.getDeathInfo();
        if (info != null && info.getSource() instanceof Damage.EntitySource source) {
            Ref<EntityStore> killer = source.getRef();
            if (killer.isValid()) {
                UUIDComponent k = cb.getComponent(killer, uuidType);
                if (k != null) killerId = k.getUuid();
                killedByPlayer = cb.getComponent(killer, Player.getComponentType()) != null;
            }
        }
        AnimalDiedEvent.Cause cause = killerId != null ? AnimalDiedEvent.Cause.KILLED : AnimalDiedEvent.Cause.OTHER;

        TransformComponent t = cb.getComponent(ref, TransformComponent.getComponentType());
        if (t != null) {
            Vector3d p = t.getPosition();
            r.lastX = p.x;
            r.lastY = p.y;
            r.lastZ = p.z;
        }
        World world = store.getExternalData().getWorld();
        r.worldName = world.getName();
        AnimalView view = r.view();
        if (r.ownerId == null && killedByPlayer) {
            registry.remove(animalId);
            AnimalEntitySystem.debug("Unowned ranch animal " + animalId + " killed by a player; record removed");
        } else {
            r.state = AnimalRecord.State.MISSING;
            AnimalPresence.markMissing(animalId, System.currentTimeMillis());
            registry.markDirty(r);
            AnimalEntitySystem.debug("Ranch animal " + animalId + " died; marked missing for recovery");
        }
        fire(world, view, cause, killerId);
    }

    private static void fire(@Nonnull World world, @Nonnull AnimalView view, @Nonnull AnimalDiedEvent.Cause cause, @Nullable UUID killerId) {
        world.execute(() -> RanchEvents.fire(new AnimalDiedEvent(view, cause, killerId)));
    }
}
