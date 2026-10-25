package com.hexvane.cozytalesranching.housedisplay;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawningContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Shows residents that are inside a house on the house's display points: one plain model entity per point
 * (network id, transform, model, the Idle animation; intangible, invulnerable, never saved), using the
 * resident's own model asset (its role's appearance, so its colour or look), scaled to fit.
 * <p>
 * World thread only, outside system ticks (world tasks, house day hooks, events relayed with
 * {@code world.execute}). What is shown is kept in memory: the entities are {@code NonSerialized}, so a save
 * never stores them, a chunk unload drops them and a restart starts with none; the next check makes them again.
 */
final class HouseDisplayService {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    /** One shown animal: who, its entity, and what it was made with (to notice a changed point or look). */
    record Shown(@Nonnull UUID animalId, @Nonnull Ref<EntityStore> ref, @Nonnull String key) {}

    /** A house's shown animals by point index. */
    static final class HouseShows {
        final String worldName;
        final Map<Integer, Shown> slots = new HashMap<>();

        HouseShows(@Nonnull String worldName) {
            this.worldName = worldName;
        }
    }

    /** What would be spawned for one point. */
    record Spec(@Nonnull String modelId, float scale, @Nonnull DisplayMath.Placement at, @Nullable String animation) {
        @Nonnull
        String key() {
            return modelId + '|' + scale + '|' + at + '|' + animation;
        }
    }

    private static final Map<UUID, HouseShows> SHOWN = new ConcurrentHashMap<>();
    /** Role id to model asset id (a role's appearance does not change while the server runs, except on reload). */
    private static final Map<String, String> ROLE_MODEL = new ConcurrentHashMap<>();

    private HouseDisplayService() {}

    static void clearCaches() {
        ROLE_MODEL.clear();
    }

    /** Brings one house's shown animals in line with the residents inside it. */
    static void refresh(@Nonnull World world, @Nonnull HouseRecord house) {
        if (!world.getName().equals(house.worldName)) return;
        List<HouseDisplayData.Point> points = HouseDisplayData.get().points(house.blockId);
        if (points.isEmpty()) {
            hideAll(world, house.houseId);
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        HouseShows shows = SHOWN.get(house.houseId);
        if (!isLoaded(world, house.x, house.z)) {
            if (shows != null) shows.slots.values().removeIf(s -> !s.ref().isValid());
            return;
        }
        List<AnimalRecord> inside = new ArrayList<>();
        for (AnimalRecord r : Ranch.animals().inHouse(house.houseId)) {
            if (r.state == AnimalRecord.State.STORED && r.animalId != null) inside.add(r);
        }
        inside.sort(Comparator.comparing(r -> r.animalId));
        if (inside.isEmpty() && (shows == null || shows.slots.isEmpty())) return;

        Map<UUID, AnimalRecord> byId = new HashMap<>();
        List<UUID> order = new ArrayList<>(inside.size());
        for (AnimalRecord r : inside) {
            byId.put(r.animalId, r);
            order.add(r.animalId);
        }
        if (shows == null) {
            shows = new HouseShows(house.worldName);
            SHOWN.put(house.houseId, shows);
        }
        Map<Integer, UUID> current = new HashMap<>();
        shows.slots.forEach((i, s) -> current.put(i, s.animalId()));
        Map<Integer, UUID> plan = DisplaySlots.plan(current, order, points.size());

        // take down what is no longer wanted, gone, or made for another point or look
        for (Iterator<Map.Entry<Integer, Shown>> it = shows.slots.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Shown> e = it.next();
            Shown s = e.getValue();
            UUID wanted = plan.get(e.getKey());
            Spec spec = wanted != null && wanted.equals(s.animalId()) ? spec(points.get(e.getKey()), house, byId.get(wanted)) : null;
            if (spec == null || !s.ref().isValid() || !spec.key().equals(s.key())) {
                remove(store, s.ref());
                it.remove();
            }
        }
        for (Map.Entry<Integer, UUID> e : plan.entrySet()) {
            if (shows.slots.containsKey(e.getKey())) continue;
            Spec spec = spec(points.get(e.getKey()), house, byId.get(e.getValue()));
            if (spec == null) continue;
            Ref<EntityStore> ref = spawn(store, spec);
            if (ref != null) shows.slots.put(e.getKey(), new Shown(e.getValue(), ref, spec.key()));
        }
        if (shows.slots.isEmpty()) SHOWN.remove(house.houseId, shows);
    }

    /** Takes down every animal shown on a house. */
    static void hideAll(@Nonnull World world, @Nonnull UUID houseId) {
        HouseShows shows = SHOWN.remove(houseId);
        if (shows == null) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Shown s : shows.slots.values()) remove(store, s.ref());
    }

    /** The periodic check for one world: every house with points, and shows of houses that are gone. */
    static void pass(@Nonnull World world) {
        if (!world.isAlive()) return;
        HouseDisplayData data = HouseDisplayData.get();
        for (HouseRecord house : Ranch.houses().inWorld(world.getName())) {
            if (!data.points(house.blockId).isEmpty() || SHOWN.containsKey(house.houseId)) refresh(world, house);
        }
        for (Map.Entry<UUID, HouseShows> e : List.copyOf(SHOWN.entrySet())) {
            if (!e.getValue().worldName.equals(world.getName())) continue;
            if (Ranch.houses().get(e.getKey()).isEmpty()) hideAll(world, e.getKey());
        }
    }

    /** True if this world has any house that can show animals, or anything shown. Cheap; called from the tick. */
    static boolean worthPassing(@Nonnull String worldName) {
        for (HouseShows s : SHOWN.values()) {
            if (s.worldName.equals(worldName)) return true;
        }
        HouseDisplayData data = HouseDisplayData.get();
        if (!data.Enabled || data.Houses.isEmpty()) return false;
        for (HouseRecord house : Ranch.houses().inWorld(worldName)) {
            if (data.Houses.containsKey(house.blockId)) return true;
        }
        return false;
    }

    /** Shown animals of a house (for the status command and tests). */
    @Nonnull
    static List<Shown> shown(@Nonnull UUID houseId) {
        HouseShows s = SHOWN.get(houseId);
        return s == null ? List.of() : List.copyOf(s.slots.values());
    }

    static int shownCount() {
        int n = 0;
        for (HouseShows s : SHOWN.values()) n += s.slots.size();
        return n;
    }

    @Nullable
    static Spec spec(@Nonnull HouseDisplayData.Point point, @Nonnull HouseRecord house, @Nullable AnimalRecord r) {
        if (r == null) return null;
        String modelId = modelFor(r);
        if (modelId == null) return null;
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        if (asset == null) return null;
        float scale = DisplayMath.scale(point.Scale, asset.getMinScale(), asset.getMaxScale());
        DisplayMath.Placement at = DisplayMath.place(point.x(), point.y(), point.z(), point.Yaw, house.x, house.y, house.z, house.rotation);
        String anim = point.Animation != null ? point.Animation : HouseDisplayData.get().Animation;
        Map<String, ?> sets = asset.getAnimationSetMap();
        if (sets == null || !sets.containsKey(anim)) anim = null;
        return new Spec(modelId, scale, at, anim);
    }

    /** The model of the role the animal comes out as: the role it was stored with, else its species' ranch role. */
    @Nullable
    static String modelFor(@Nonnull AnimalRecord r) {
        String role = r.spawnRole;
        if (role == null || role.isEmpty()) {
            Optional<SpeciesDefinition> s = SpeciesRegistry.byId(r.speciesId);
            if (s.isEmpty()) return null;
            role = s.get().ranchRoleFor(null, r.baby);
            if (role.isEmpty()) role = s.get().RanchRole;
        }
        if (role == null || role.isEmpty()) return null;
        String cached = ROLE_MODEL.get(role);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String model = roleModel(role);
        ROLE_MODEL.put(role, model == null ? "" : model);
        return model;
    }

    @Nullable
    private static String roleModel(@Nonnull String role) {
        NPCPlugin npcs = NPCPlugin.get();
        if (npcs == null) return null;
        int index = npcs.getIndex(role);
        if (index < 0) return null;
        if (!(npcs.tryGetCachedValidRole(index) instanceof ISpawnableWithModel spawnable)) return null;
        SpawningContext ctx = new SpawningContext();
        try {
            if (!ctx.setSpawnable(spawnable) || ctx.getModel() == null) return null;
            return ctx.getModel().getModelAssetId();
        } finally {
            ctx.releaseFull();
        }
    }

    @Nullable
    private static Ref<EntityStore> spawn(@Nonnull Store<EntityStore> store, @Nonnull Spec spec) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(spec.modelId());
        if (asset == null) return null;
        Model model = Model.createScaledModel(asset, spec.scale());
        Rotation3f rotation = new Rotation3f(0f, spec.at().yawRadians(), 0f);
        Holder<EntityStore> holder = store.getRegistry().newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(spec.at().x(), spec.at().y(), spec.at().z()), rotation));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        if (spec.animation() != null) {
            ActiveAnimationComponent anim = new ActiveAnimationComponent();
            anim.setPlayingAnimation(AnimationSlot.Movement, spec.animation());
            holder.addComponent(ActiveAnimationComponent.getComponentType(), anim);
        }
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            LOG.info("House display: could not add a " + spec.modelId() + " model entity");
            return null;
        }
        return ref;
    }

    private static void remove(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (ref.isValid()) store.removeEntity(ref, RemoveReason.REMOVE);
    }

    static boolean isLoaded(@Nonnull World world, double x, double z) {
        return world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z)) != null;
    }
}
