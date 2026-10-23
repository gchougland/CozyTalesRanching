package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Finding the animal a command names, and entity-side helpers. World thread. */
final class RehomeAnimals {
    static final String THIS = "this";
    private static final float LOOK_REACH = 12f;
    private static final int MIN_ID_PREFIX = 4;
    private static final String STATE_STAY = "Stay";

    private RehomeAnimals() {}

    /**
     * The animal a command argument names: {@code this} (the one the player looks at), a name (case-insensitive) or the
     * start of its id (at least four characters). Searches the player's own animals, and with {@code anyOwner} also
     * every animal in the player's world. Tells the player why when nothing or more than one matches.
     */
    @Nonnull
    static Optional<AnimalRecord> resolve(@Nonnull String token, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                                          @Nonnull Store<EntityStore> store, @Nonnull World world, boolean anyOwner) {
        String t = token.trim();
        if (t.equalsIgnoreCase(THIS)) {
            Ref<EntityStore> target = TargetUtil.getTargetEntity(playerEntity, LOOK_REACH, store);
            UUIDComponent uuid = target != null && target.isValid() ? store.getComponent(target, UUIDComponent.getComponentType()) : null;
            Optional<AnimalRecord> r = uuid == null ? Optional.empty() : Ranch.animals().byEntity(uuid.getUuid());
            if (r.isEmpty()) player.sendMessage(Message.translation("cozyranch_rehome.animal.look_at"));
            return r;
        }
        Map<UUID, AnimalRecord> pool = new LinkedHashMap<>();
        for (AnimalRecord r : Ranch.animals().ownedBy(player.getUuid())) pool.put(r.animalId, r);
        if (anyOwner) for (AnimalRecord r : Ranch.animals().inWorld(world.getName())) pool.putIfAbsent(r.animalId, r);
        List<AnimalRecord> matches = match(t, pool.values());
        if (matches.size() == 1) return Optional.of(matches.getFirst());
        player.sendMessage(Message.translation(matches.isEmpty() ? "cozyranch_rehome.animal.not_found" : "cozyranch_rehome.animal.ambiguous")
            .param("name", t).param("count", matches.size()));
        return Optional.empty();
    }

    /** Exact names first; when no name matches, id prefixes. */
    @Nonnull
    static List<AnimalRecord> match(@Nonnull String token, @Nonnull Iterable<AnimalRecord> pool) {
        List<AnimalRecord> byName = new ArrayList<>();
        List<AnimalRecord> byId = new ArrayList<>();
        String lower = token.toLowerCase(Locale.ROOT);
        for (AnimalRecord r : pool) {
            if (r.name != null && r.name.equalsIgnoreCase(token)) byName.add(r);
            else if (lower.length() >= MIN_ID_PREFIX && r.animalId != null && r.animalId.toString().startsWith(lower)) byId.add(r);
        }
        return byName.isEmpty() ? byId : byName;
    }

    @Nonnull
    static Message displayName(@Nonnull AnimalRecord r) {
        return TamingApi.displayName(r);
    }

    /** The animal's current entity in this world, if loaded. */
    @Nullable
    static Ref<EntityStore> live(@Nonnull World world, @Nonnull AnimalRecord r) {
        if (r.entityUuid == null || !world.getName().equals(r.worldName)) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(r.entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    @Nullable
    static Vector3d position(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        return t == null ? null : new Vector3d(t.getPosition());
    }

    /** Stops a loaded animal following anyone: no target, home where it stands, the Stay state on our ranch roles. */
    static void stopFollowing(@Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null || npc.getRole().isRoleChangeRequested()) return;
        MarkedEntitySupport marked = store.getComponent(animal, MarkedEntitySupport.getComponentType());
        if (marked != null) marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, null);
        Vector3d pos = position(animal, store);
        if (pos != null) npc.setLeashPoint(pos);
        boolean ranchRole = npc.getRoleName() != null && SpeciesRegistry.byRole(npc.getRoleName())
            .map(m -> m.kind() == RoleKind.RANCH || m.kind() == RoleKind.RANCH_BABY).orElse(false);
        StateSupport states = store.getComponent(animal, StateSupport.getComponentType());
        if (ranchRole && states != null) states.setState(animal, STATE_STAY, null, store);
    }

    @Nonnull
    static String describe(@Nonnull AnimalRecord r) {
        return r.speciesId + " " + r.animalId + (r.name != null ? " \"" + r.name + "\"" : "");
    }
}
