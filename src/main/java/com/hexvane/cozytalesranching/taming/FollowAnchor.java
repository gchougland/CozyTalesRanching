package com.hexvane.cozytalesranching.taming;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Who a following animal walks after: the owner, or the NPC mount the owner rides.
 *
 * <p>NPC mounts (vanilla horses through {@code ActionMount}, and mods such as Averion Mounts) set
 * {@link Player#getMountEntityId()} and the client then steers the mount: its {@code MountMovement} packets move the
 * mount entity's {@link TransformComponent}, not the rider's. Following the rider's entity sends the animals to the
 * spot where the owner got on. Following the mount keeps them with the owner, and the teleport catch-up lands next
 * to the rider. On dismount the id goes back to 0 and the owner is followed again.
 */
final class FollowAnchor {
    private FollowAnchor() {}

    /** The entity to follow for this owner: its mount while it rides one that is loaded here, else the owner. */
    @Nonnull
    static Ref<EntityStore> of(@Nonnull Ref<EntityStore> owner, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(owner, Player.getComponentType());
        if (player == null) return owner;
        int mountId = player.getMountEntityId();
        if (!rides(mountId)) return owner;
        Ref<EntityStore> mount = store.getExternalData().getRefFromNetworkId(mountId);
        boolean usable = mount != null && mount.isValid() && store.getComponent(mount, TransformComponent.getComponentType()) != null;
        return choose(owner, usable ? mount : null);
    }

    /** Whether a {@link Player#getMountEntityId()} value means the player rides something (0 and -1 mean not). */
    static boolean rides(int mountEntityId) {
        return mountEntityId > 0;
    }

    /** The mount when there is a usable one, else the owner. */
    @Nonnull
    static <R> R choose(@Nonnull R owner, @Nullable R mount) {
        return mount != null ? mount : owner;
    }
}
