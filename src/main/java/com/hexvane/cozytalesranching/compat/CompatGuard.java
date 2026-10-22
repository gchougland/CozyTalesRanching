package com.hexvane.cozytalesranching.compat;

import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Decides whether the mod may touch an entity at all. Everything the mod does to an NPC goes through
 * here first, so other mods' pets, familiars and mounts are never affected.
 * <p>
 * Other mods' component classes live in their own classloaders, so they are matched by class name
 * against the component types an entity actually has ({@link ComponentType#getTypeClass()}); nothing
 * is ever loaded by name. Answers are cached per component type and per archetype, and dropped when
 * the config is reloaded.
 */
public final class CompatGuard {
    private CompatGuard() {}

    private record Cache(
        @Nonnull RanchConfig config,
        @Nonnull Set<String> ignoredClasses,
        @Nonnull Map<ComponentType<EntityStore, ?>, Boolean> byType,
        @Nonnull Map<Archetype<EntityStore>, Boolean> byArchetype
    ) {}

    private static volatile Cache cache;

    /** Role-level check: the role belongs to a supported species and is not on the ignore list. */
    public static boolean isManageableRole(@Nonnull String roleId) {
        return !RanchConfig.get().isRoleIgnored(roleId) && SpeciesRegistry.byRole(roleId).isPresent();
    }

    /** Full check for one entity: its role, then whether it carries a component from {@code IgnoredComponentClasses}. */
    public static boolean isManageable(@Nonnull Ref<EntityStore> entity, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull String roleId) {
        if (!isManageableRole(roleId)) return false;
        if (!entity.isValid()) return false;
        return !hasIgnoredComponent(accessor.getArchetype(entity));
    }

    /** True if any of the archetype's component types is one of the configured foreign classes. */
    public static boolean hasIgnoredComponent(@Nonnull Archetype<EntityStore> archetype) {
        Cache c = cache();
        if (c.ignoredClasses.isEmpty()) return false;
        Boolean known = c.byArchetype.get(archetype);
        if (known != null) return known;
        boolean found = false;
        for (int i = archetype.getMinIndex(); i < archetype.length() && !found; i++) {
            ComponentType<EntityStore, ?> type = archetype.get(i);
            if (type != null) found = c.byType.computeIfAbsent(type, t -> isIgnoredClassName(c.ignoredClasses, t.getTypeClass().getName()));
        }
        c.byArchetype.put(archetype, found);
        return found;
    }

    /** Name match, also accepting a configured simple name ({@code PetOwnerComponent}) for any package. */
    public static boolean isIgnoredClassName(@Nonnull Set<String> ignored, @Nonnull String className) {
        if (ignored.contains(className)) return true;
        int dot = className.lastIndexOf('.');
        String simple = dot < 0 ? className : className.substring(dot + 1);
        int dollar = simple.lastIndexOf('$');
        if (dollar >= 0) simple = simple.substring(dollar + 1);
        return ignored.contains(simple);
    }

    @Nonnull
    private static Cache cache() {
        RanchConfig config = RanchConfig.get();
        Cache c = cache;
        if (c == null || c.config != config) {
            c = new Cache(config, config.ignoredComponentClasses(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
            cache = c;
        }
        return c;
    }
}
