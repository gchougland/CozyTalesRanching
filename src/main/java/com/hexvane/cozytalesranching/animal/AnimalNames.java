package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What to call one animal when it has no name of its own: the name of its look (Raven, Frost Snail, Black Wolf, Ice
 * Bat), which is the name over its head, from its role's {@code NameTranslationKey}. A raven lives in a Rookery with
 * the crows and shares the crow species, so the species name alone called every raven a crow.
 */
public final class AnimalNames {
    /** Role id to its name key; "" for a role without one. */
    private static final Map<String, String> ROLE_NAME = new ConcurrentHashMap<>();

    private AnimalNames() {}

    /** Lang key for the animal's look: the role it last had, else the role it is stored with, else its species. */
    @Nullable
    public static String key(@Nonnull AnimalRecord r) {
        return key(r, SpeciesRegistry.byId(r.speciesId).orElse(null), Ranch.animals().lastRole(r.animalId), AnimalNames::roleNameKey);
    }

    /** Lang key for an animal of this role (a wild visitor, a live entity), else the species name. */
    @Nullable
    public static String key(@Nullable String role, @Nullable SpeciesDefinition species) {
        String k = role == null || role.isEmpty() ? null : roleNameKey(role);
        if (k != null) return k;
        return species != null && !species.NameKey.isBlank() ? species.NameKey : null;
    }

    /** The rule behind {@link #key(AnimalRecord)}, pure. */
    @Nullable
    static String key(@Nonnull AnimalRecord r, @Nullable SpeciesDefinition species, @Nullable String lastRole,
                      @Nonnull Function<String, String> roleNames) {
        for (String role : new String[] {lastRole, r.spawnRole}) {
            if (role == null || role.isEmpty()) continue;
            String k = roleNames.apply(role);
            if (k != null && !k.isEmpty()) return k;
        }
        return species != null && !species.NameKey.isBlank() ? species.NameKey : null;
    }

    /** The role's name key, as the engine computes it for the name over the head; null when unknown. */
    @Nullable
    static String roleNameKey(@Nonnull String role) {
        String cached = ROLE_NAME.get(role);
        if (cached != null) return cached.isEmpty() ? null : cached;
        NPCPlugin npcs = NPCPlugin.get();
        if (npcs == null) return null;
        int index = npcs.getIndex(role);
        if (index < 0) return null;
        String key = null;
        if (npcs.tryGetCachedValidRole(index) instanceof ISpawnableWithModel spawnable) {
            ExecutionContext ctx = new ExecutionContext();
            ctx.setScope(spawnable.createExecutionScope());
            key = spawnable.getNameTranslationKey(ctx, spawnable.createModifierScope(ctx));
        }
        ROLE_NAME.put(role, key == null ? "" : key);
        return key == null || key.isEmpty() ? null : key;
    }
}
