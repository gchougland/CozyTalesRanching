package com.hexvane.cozytalesranching.compat;

import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Decides whether the mod may touch an entity at all. Everything the mod does to an NPC goes through
 * here first, so other mods' pets, familiars and mounts are never affected.
 */
public final class CompatGuard {
    private CompatGuard() {}

    /** Role-level check: the role belongs to a supported species and is not on the ignore list. */
    public static boolean isManageableRole(@Nonnull String roleId) {
        return !RanchConfig.get().isRoleIgnored(roleId) && SpeciesRegistry.byRole(roleId).isPresent();
    }

    /**
     * Full check for one entity. The starting version checks the role only; the animal role adds the
     * check for components owned by other mods ({@code RanchConfig.IgnoredComponentClasses}).
     */
    public static boolean isManageable(@Nonnull Ref<EntityStore> entity, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull String roleId) {
        return isManageableRole(roleId);
    }
}
