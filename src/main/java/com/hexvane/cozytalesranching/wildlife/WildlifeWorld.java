package com.hexvane.cozytalesranching.wildlife;

import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.api.event.RanchVisitorArrivedEvent;
import com.hexvane.cozytalesranching.care.CareApi;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.wildlife.VisitorStore.HouseState;
import com.hexvane.cozytalesranching.wildlife.VisitorStore.Visitor;
import com.hexvane.cozytalesranching.wildlife.WildlifeConfig.SpeciesWildlife;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.Candidate;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.Cap;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.Counts;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.HouseFacts;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import com.hypixel.hytale.server.spawning.SpawningContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * One world's wildlife pass and the spawn and removal of visitors. World thread, outside system ticks (world tasks
 * and commands). Only entities recorded in the {@link VisitorStore} are ever touched.
 */
final class WildlifeWorld {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private WildlifeWorld() {}

    /** The outcome of an attempt to bring a visitor. */
    enum Outcome { ARRIVED, NO_SPECIES, NO_ROLE, CAP_HOUSE, CAP_OWNER, CAP_WORLD, UNLOADED, NO_SPOT, FAILED }

    record Arrival(@Nonnull Outcome outcome, @Nullable Visitor visitor, @Nonnull String detail) {}

    /** A visitor seen this pass: loaded or not, and how far the nearest player is. */
    record Seen(@Nonnull Visitor visitor, @Nullable Ref<EntityStore> ref) {}

    // ---- the pass ----

    /** Visitors first (leave, forget), then houses that are due. Returns how many arrived. */
    static int pass(@Nonnull World world, @Nonnull WildlifeService service, long now, boolean forceDue) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        WildlifeConfig config = service.config();
        List<Vector3d> players = playerPositions(world, store);
        updateVisitors(world, store, service, config, players, now);

        List<HouseRecord> houses = Ranch.houses().inWorld(world.getName());
        Set<UUID> ids = new HashSet<>();
        for (HouseRecord h : houses) ids.add(h.houseId);
        service.store().retainHouses(world.getName(), ids);
        if (houses.isEmpty() || players.isEmpty()) return 0;

        RandomGenerator rng = ThreadLocalRandom.current();
        int arrived = 0;
        for (HouseRecord house : houses) {
            if (arrived >= config.MaxArrivalsPerPass) break;
            if (!eligible(world, house, players, config)) continue;
            HouseState state = service.store().house(house.houseId, world.getName());
            if (state.nextVisitAtMs <= 0 && !forceDue) {
                state.nextVisitAtMs = now + WildlifeRules.nextVisitDelayMs(config, rng);
                service.store().markDirty(world.getName());
                LOG.info("Wildlife: house " + house.houseId + " (" + house.speciesId + ") may draw a visitor in "
                    + (state.nextVisitAtMs - now) / 1000 + " s");
                continue;
            }
            if (now < state.nextVisitAtMs && !forceDue) continue;
            state.nextVisitAtMs = now + WildlifeRules.nextVisitDelayMs(config, rng);
            service.store().markDirty(world.getName());
            HouseFacts facts = facts(house);
            List<Candidate> candidates = WildlifeRules.candidates(config, facts, state.lastVisitBySpecies,
                id -> SpeciesRegistry.byId(id).isPresent(), now);
            Candidate pick = WildlifeRules.pick(candidates, rng, forceDue);
            if (pick == null) continue;
            SpeciesWildlife sw = config.species(pick.speciesId());
            if (sw == null) continue;
            boolean rare = WildlifeRules.rollRare(pick.speciesId(), sw.Rare, facts, state.lastRareAtMs, now, rng);
            Arrival a = bring(world, store, service, house, pick.speciesId(), rare, -1, false, now);
            if (a.outcome() == Outcome.ARRIVED) arrived++;
            else LOG.fine("Wildlife: no visitor for house " + house.houseId + ": " + a.outcome() + " " + a.detail());
        }
        if (arrived > 0 || service.store().hasDirty()) service.scheduleSave();
        return arrived;
    }

    /** Loaded origin, owner online, and a player near. */
    static boolean eligible(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<Vector3d> players, @Nonnull WildlifeConfig config) {
        if (house.ownerId == null || !chunkLoaded(world, house.x, house.z)) return false;
        Universe universe = Universe.get();
        if (universe == null || universe.getPlayer(house.ownerId) == null) return false;
        return nearest(players, house.x + 0.5, house.y, house.z + 0.5) <= config.PlayerRange;
    }

    // ---- visitors ----

    private static void updateVisitors(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull WildlifeService service,
                                       @Nonnull WildlifeConfig config, @Nonnull List<Vector3d> players, long now) {
        VisitorStore visitors = service.store();
        for (Visitor v : visitors.inWorld(world.getName())) {
            Ref<EntityStore> ref = liveRef(world, v.entityUuid);
            if (ref == null) {
                if (chunkLoaded(world, v.x, v.z)) {
                    visitors.forget(v.entityUuid);
                    LOG.info("Wildlife: visitor " + v.entityUuid + " (" + v.roleId + ") is gone (killed or removed by something else); forgotten");
                } else if (now - Math.max(v.leaveAtMs, v.arrivedAtMs) > Math.round(config.ForgetUnloadedHours * 3_600_000L)) {
                    visitors.forget(v.entityUuid);
                    LOG.info("Wildlife: visitor " + v.entityUuid + " stayed unloaded too long; forgotten (it stays a wild " + v.roleId + ")");
                }
                continue;
            }
            if (!stillOurs(ref, store, v)) {
                visitors.forget(v.entityUuid);
                LOG.info("Wildlife: visitor " + v.entityUuid + " (" + v.roleId + ") was befriended; it is a ranch animal now and no longer a visitor");
                continue;
            }
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t != null) {
                Vector3d p = t.getPosition();
                v.x = p.x;
                v.y = p.y;
                v.z = p.z;
            }
            if (!v.leaving()) {
                if (now >= v.leaveAtMs) startLeaving(world, store, ref, v, now);
                continue;
            }
            double nearest = nearest(players, v.x, v.y, v.z);
            if (WildlifeRules.leaving(config, v.leavingSinceMs, now, nearest) == WildlifeRules.LeaveStep.REMOVE) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                visitors.forget(v.entityUuid);
                LOG.info(String.format("Wildlife: visitor %s (%s) left after %d s walking off, nearest player %.0f blocks",
                    v.entityUuid, v.roleId, (now - v.leavingSinceMs) / 1000, nearest));
            }
        }
        visitors.markDirty(world.getName());
    }

    /** The entity is still the wild visitor we spawned: same role, and nobody has made it a ranch animal. */
    static boolean stillOurs(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Visitor v) {
        if (Ranch.animals().byEntity(v.entityUuid).isPresent()) return false;
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        return npc != null && v.roleId.equals(npc.getRoleName());
    }

    /** Points its home far beyond the edge of the ranch; the vanilla leash behaviour walks it there. */
    static void startLeaving(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull Visitor v, long now) {
        v.leavingSinceMs = now;
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        HouseRecord house = Ranch.houses().get(v.houseId).orElse(null);
        if (npc != null) {
            double cx = house != null ? house.x + 0.5 : v.x, cz = house != null ? house.z + 0.5 : v.z;
            double dx = v.x - cx, dz = v.z - cz;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.5) {
                double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                dx = Math.cos(a);
                dz = Math.sin(a);
                len = 1;
            }
            double reach = 48;
            npc.setLeashPoint(new Vector3d(cx + dx / len * reach, v.y, cz + dz / len * reach));
        }
        LOG.info(String.format("Wildlife: visitor %s (%s) walks off after %d s, %.0f blocks from its house",
            v.entityUuid, v.roleId, (now - v.arrivedAtMs) / 1000, distanceToHouse(v)));
    }

    /** Removes every loaded visitor of this world and forgets all of them. Returns how many entities went. */
    static int clear(@Nonnull World world, @Nonnull WildlifeService service) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        int removed = 0;
        for (Visitor v : service.store().inWorld(world.getName())) {
            Ref<EntityStore> ref = liveRef(world, v.entityUuid);
            if (ref != null && stillOurs(ref, store, v)) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                removed++;
            }
            service.store().forget(v.entityUuid);
        }
        service.scheduleSave();
        return removed;
    }

    // ---- arrival ----

    /**
     * Brings a visitor of {@code speciesId} to the house: role, caps, a safe spot at the edge of the ranch, spawn,
     * home pointed at the house, saved, then the event, the owner's chat line and the sound. {@code stayMs} below 0
     * draws the stay from the config.
     */
    @Nonnull
    static Arrival bring(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull WildlifeService service, @Nonnull HouseRecord house,
                         @Nonnull String speciesId, boolean rare, long stayMs, boolean ignoreCaps, long now) {
        WildlifeConfig config = service.config();
        SpeciesWildlife sw = config.species(speciesId);
        SpeciesDefinition species = SpeciesRegistry.byId(speciesId).orElse(null);
        if (sw == null || species == null) return new Arrival(Outcome.NO_SPECIES, null, speciesId);
        NPCPlugin npcs = NPCPlugin.get();
        if (npcs == null) return new Arrival(Outcome.FAILED, null, "no NPC plugin");
        RandomGenerator rng = ThreadLocalRandom.current();
        String role = rare && sw.Rare != null && !sw.Rare.Role.isEmpty() ? sw.Rare.Role : WildlifeRules.normalRole(sw, r -> npcs.getIndex(r) >= 0, rng);
        if (role == null || npcs.getIndex(role) < 0) return new Arrival(Outcome.NO_ROLE, null, String.valueOf(role));
        if (!ignoreCaps) {
            Cap cap = WildlifeRules.caps(config, counts(world, service, house, now));
            if (cap != Cap.OK) {
                return new Arrival(switch (cap) {
                    case HOUSE -> Outcome.CAP_HOUSE;
                    case OWNER -> Outcome.CAP_OWNER;
                    default -> Outcome.CAP_WORLD;
                }, null, cap.name());
            }
        }
        if (!chunkLoaded(world, house.x, house.z)) return new Arrival(Outcome.UNLOADED, null, "house chunk");
        if (!(npcs.tryGetCachedValidRole(npcs.getIndex(role)) instanceof ISpawnableWithModel spawnable)) {
            return new Arrival(Outcome.NO_ROLE, null, role + " is not spawnable");
        }
        StringBuilder why = new StringBuilder();
        List<SpotRules.Spot> spots = SpotRules.candidates(new WorldSpotGrid(world), house.x, house.y, house.z, config, sw.NearWater, rng, why);
        Ref<EntityStore> ref = null;
        Vector3d at = null;
        for (SpotRules.Spot s : spots) {
            Placed p = test(world, spawnable, s, why);
            if (p == null) continue;
            var spawned = npcs.spawnEntity(store, npcs.getIndex(role), p.pos, new Rotation3f(0f, (float) (rng.nextDouble() * Math.PI * 2), 0f), p.model, null);
            if (spawned == null || spawned.first() == null || !spawned.first().isValid()) continue;
            ref = spawned.first();
            at = p.pos;
            NPCEntity npc = spawned.second();
            if (npc != null) npc.setLeashPoint(new Vector3d(house.doorX, house.doorY, house.doorZ));
            break;
        }
        if (ref == null) return new Arrival(Outcome.NO_SPOT, null, spots.size() + " candidate spots;" + why);
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) {
            store.removeEntity(ref, RemoveReason.REMOVE);
            return new Arrival(Outcome.FAILED, null, "no uuid");
        }
        Visitor v = new Visitor();
        v.entityUuid = uuid.getUuid();
        v.houseId = house.houseId;
        v.ownerId = house.ownerId;
        v.speciesId = speciesId;
        v.roleId = role;
        v.rare = rare;
        v.worldName = world.getName();
        v.arrivedAtMs = now;
        v.leaveAtMs = now + (stayMs >= 0 ? stayMs : WildlifeRules.stayMs(config, rng));
        v.x = at.x;
        v.y = at.y;
        v.z = at.z;
        service.store().add(v);
        HouseState state = service.store().house(house.houseId, world.getName());
        state.lastVisitBySpecies.put(speciesId, now);
        if (rare) state.lastRareAtMs = now;
        service.store().markDirty(world.getName());
        service.scheduleSave();
        LOG.info(String.format("Wildlife: %s visitor %s (%s) arrived for house %s at %.1f %.1f %.1f, %.0f blocks out, stays %d s",
            rare ? "RARE" : "a", v.entityUuid, role, house.houseId, at.x, at.y, at.z,
            Math.hypot(at.x - house.x - 0.5, at.z - house.z - 0.5), (v.leaveAtMs - now) / 1000));
        RanchEvents.fire(new RanchVisitorArrivedEvent(house.ownerId, house.houseId, speciesId, role, rare, world.getName()));
        RanchSounds.play(RanchSounds.VISITOR_ARRIVED, at.x, at.y, at.z, store);
        tellOwner(world, store, house, sw, species, rare, config);
        return new Arrival(Outcome.ARRIVED, v, role);
    }

    private record Placed(@Nonnull Vector3d pos, @Nullable com.hypixel.hytale.server.core.asset.type.model.config.Model model) {}

    /** The engine's own fit test for the role's model at the spot, as house spawns do (a fresh context per column). */
    @Nullable
    private static Placed test(@Nonnull World world, @Nonnull ISpawnableWithModel spawnable, @Nonnull SpotRules.Spot s, @Nonnull StringBuilder why) {
        SpawningContext ctx = new SpawningContext();
        try {
            if (!ctx.setSpawnable(spawnable)) return null;
            if (!ctx.set(world, s.x() + 0.5, s.y(), s.z() + 0.5, false, 1.5)) {
                if (why.length() < 400) why.append(' ').append(s.x()).append(',').append(s.z()).append(':').append(ctx.getLastResult());
                return null;
            }
            SpawnTestResult r = ctx.canSpawn();
            if (r != SpawnTestResult.TEST_OK) {
                if (why.length() < 400) why.append(' ').append(s.x()).append(',').append(s.z()).append(':').append(r);
                return null;
            }
            return new Placed(new Vector3d(ctx.xSpawn, ctx.ySpawn, ctx.zSpawn), ctx.getModel());
        } finally {
            ctx.releaseFull();
        }
    }

    /** A quiet line to the owner, only when the owner is near the house. */
    private static void tellOwner(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull HouseRecord house, @Nonnull SpeciesWildlife sw,
                                  @Nonnull SpeciesDefinition species, boolean rare, @Nonnull WildlifeConfig config) {
        for (PlayerRef p : world.getPlayerRefs()) {
            if (!house.ownerId.equals(p.getUuid())) continue;
            Ref<EntityStore> pr = p.getReference();
            if (pr == null || !pr.isValid()) return;
            TransformComponent t = store.getComponent(pr, TransformComponent.getComponentType());
            if (t == null || t.getPosition().distance(house.x + 0.5, house.y, house.z + 0.5) > config.PlayerRange) return;
            Message animal = rare && sw.Rare != null && !sw.Rare.NameKey.isEmpty()
                ? Message.translation(sw.Rare.NameKey) : Message.translation(species.NameKey);
            RanchChat.tell(p, Message.translation(rare ? "cozyranch_wildlife.arrived.rare" : "cozyranch_wildlife.arrived")
                .param("animal", animal).param("house", houseName(house)));
            return;
        }
    }

    @Nonnull
    static Message houseName(@Nonnull HouseRecord h) {
        if (h.name != null && !h.name.isEmpty()) return Message.raw(h.name);
        Item item = Item.getAssetMap().getAsset(h.blockId);
        return item != null ? Message.translation(item.getTranslationKey()) : Message.raw(h.blockId);
    }

    // ---- what a house is like ----

    /** Everything the rules need about a house, from cached records only. */
    @Nonnull
    static HouseFacts facts(@Nonnull HouseRecord house) {
        SpeciesDefinition s = SpeciesRegistry.byId(house.speciesId).orElse(null);
        Set<String> watched = new LinkedHashSet<>();
        List<String> favourites = List.of();
        if (s != null) {
            watched.addAll(s.HabitatRequires);
            watched.addAll(s.HabitatPrefers);
            favourites = s.FavoriteTreats;
        }
        watched.addAll(CareApi.toysLiked(house.speciesId));
        boolean scanned = house.habitatScannedAtMs > 0;
        List<String> missing = house.habitatMissing == null ? List.of() : house.habitatMissing;
        Set<String> present = WildlifeRules.presentFeatures(scanned, watched, missing);
        int residents = 0;
        double happiness = 0;
        for (AnimalRecord r : Ranch.animals().inHouse(house.houseId)) {
            if (!house.speciesId.equals(r.speciesId)) continue;
            residents++;
            happiness += r.happiness;
        }
        int favourite = 0;
        if (!favourites.isEmpty()) {
            int radius = RanchConfig.get().habitatScanRadius();
            for (CareApi.Trough t : CareApi.troughsNear(house.worldName, house.x + 0.5, house.y, house.z + 0.5, radius)) {
                for (CareApi.Stack st : t.stacks()) if (favourites.contains(st.itemId())) favourite += st.count();
            }
        }
        Map<String, Integer> stored = Ranch.produce().stored(house.houseId);
        return new HouseFacts(house.houseId, house.ownerId, house.speciesId, scanned, house.habitatRequirementsMet, house.habitatScore,
            present, residents, residents == 0 ? 0 : happiness / residents, favourite, stored);
    }

    /** Visitors that are here now (loaded, or unloaded but not yet due to leave). */
    @Nonnull
    static Counts counts(@Nonnull World world, @Nonnull WildlifeService service, @Nonnull HouseRecord house, long now) {
        int h = 0, o = 0, w = 0;
        for (Visitor v : service.store().all()) {
            if (!world.getName().equals(v.worldName)) {
                if (house.ownerId.equals(v.ownerId) && now < v.leaveAtMs) o++;
                continue;
            }
            boolean present = liveRef(world, v.entityUuid) != null || now < v.leaveAtMs;
            if (!present) continue;
            w++;
            if (house.ownerId.equals(v.ownerId)) o++;
            if (house.houseId.equals(v.houseId)) h++;
        }
        return new Counts(h, o, w);
    }

    // ---- small helpers ----

    @Nullable
    static Ref<EntityStore> liveRef(@Nonnull World world, @Nullable UUID entityUuid) {
        if (entityUuid == null) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    static boolean chunkLoaded(@Nonnull World world, double x, double z) {
        return world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z)) != null;
    }

    @Nonnull
    static List<Vector3d> playerPositions(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        List<Vector3d> out = new ArrayList<>();
        for (PlayerRef p : world.getPlayerRefs()) {
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t != null) out.add(new Vector3d(t.getPosition()));
        }
        return out;
    }

    /** Horizontal distance from its last seen place to its house; -1 when the house is gone. */
    static double distanceToHouse(@Nonnull Visitor v) {
        HouseRecord h = v.houseId == null ? null : Ranch.houses().get(v.houseId).orElse(null);
        return h == null ? -1 : Math.hypot(v.x - h.x - 0.5, v.z - h.z - 0.5);
    }

    static double nearest(@Nonnull List<Vector3d> players, double x, double y, double z) {
        double best = Double.MAX_VALUE;
        for (Vector3d p : players) best = Math.min(best, p.distance(x, y, z));
        return best;
    }
}
