package com.hexvane.cozytalesranching.taming.command;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared lookups for the taming sub-commands. They run on the player's world thread. */
final class TamingCommands {
    static final float LOOK_REACH = 12f;

    record Target(@Nonnull Ref<EntityStore> entity, @Nonnull AnimalRecord record) {}

    private TamingCommands() {}

    /** The ranch animal the player looks at, or empty with no message sent. */
    @Nonnull
    static Optional<Target> lookedAt(@Nonnull Ref<EntityStore> player, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> target = TargetUtil.getTargetEntity(player, LOOK_REACH, store);
        if (target == null || !target.isValid()) return Optional.empty();
        UUIDComponent uuid = store.getComponent(target, UUIDComponent.getComponentType());
        if (uuid == null) return Optional.empty();
        return Ranch.animals().byEntity(uuid.getUuid()).map(r -> new Target(target, r));
    }

    /** Looked-at animal the player may manage; tells them why not otherwise. */
    @Nonnull
    static Optional<Target> lookedAtOwned(@Nonnull Ref<EntityStore> player, @Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> store) {
        Optional<Target> target = lookedAt(player, store);
        if (target.isEmpty()) {
            playerRef.sendMessage(Message.translation("cozyranch_taming.command.look_at_animal"));
            return Optional.empty();
        }
        if (!mayManage(playerRef, target.get().record())) {
            playerRef.sendMessage(Message.translation("cozyranch_taming.command.not_yours"));
            return Optional.empty();
        }
        return target;
    }

    static boolean mayManage(@Nonnull PlayerRef player, @Nonnull AnimalRecord record) {
        return player.getUuid().equals(record.ownerId) || RanchPermissions.has(player.getUuid(), RanchPermissions.ADMIN_MANAGE_ANY);
    }

    @Nullable
    static Ref<EntityStore> entityOf(@Nonnull AnimalRecord record, @Nonnull Store<EntityStore> store) {
        if (record.entityUuid == null) return null;
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(record.entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }
}
