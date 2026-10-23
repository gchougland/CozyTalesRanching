package com.hexvane.cozytalesranching.behaviour;

import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.AnimalBehaviourEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.behaviour.BehaviourConfig.Def;
import com.hexvane.cozytalesranching.behaviour.BehaviourConfig.Kind;
import com.hexvane.cozytalesranching.behaviour.BehaviourRules.Context;
import com.hexvane.cozytalesranching.behaviour.BehaviourRules.Option;
import com.hexvane.cozytalesranching.behaviour.BehaviourRules.Snapshot;
import com.hexvane.cozytalesranching.behaviour.BehaviourRules.Verdict;
import com.hexvane.cozytalesranching.care.CareApi;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * The once-a-second planner, per world, on the world thread (a queued task, never inside a tick). It walks the
 * world's houses and their residents through the registries, never all entities. For each resident it either
 * advances the behaviour it is doing or, when its next decision is due, picks one ({@link BehaviourRules}) and
 * hands the movement to the role: the {@code Behave} state seeks the leash point, {@code Tag} and {@code Meet}
 * seek the entity in {@code LockedTarget}. Clips play in the Emote slot. It only ever starts from {@code Idle}
 * and only leaves its own states, so follow, stay, going home, the night and role swaps always win.
 */
final class BehaviourPlanner {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    /** The trough hitbox is two blocks long and reaches 1.5 from its block centre; this clears it on any side. */
    private static final double TROUGH_GAP = 2.1;
    private static final String HEARTS_SMALL = "Hearts_Subtle";
    private static final int PERSONALITY_PASSES = 10;
    private static final int CLEANUP_PASSES = 120;

    /** One behaviour in progress. */
    static final class Session {
        final String id;
        final Def def;
        final Kind kind;
        @Nullable final String clip;
        final String state;
        final long startedMs;
        final double seconds;
        @Nullable Vector3d spot;
        @Nullable Ref<EntityStore> target;
        @Nullable UUID partnerId;
        double arriveDistance;
        long actUntilMs;
        long nextRepeatMs;
        long nextTraceMs;
        boolean acting;

        Session(@Nonnull String id, @Nonnull Def def, @Nullable String clip, @Nonnull String state, long now, double seconds) {
            this.id = id;
            this.def = def;
            this.kind = def.kind();
            this.clip = clip;
            this.state = state;
            this.startedMs = now;
            this.seconds = seconds;
        }
    }

    /** A player in the world this pass. */
    record Seen(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref, @Nonnull Vector3d pos) {}

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextDecision = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> lastStart = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> lastEvent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastShy = new ConcurrentHashMap<>();
    /** Test toys from {@code /ranch behaviour toy}: base block id to behaviour id. */
    private final Map<String, String> extraToys = new ConcurrentHashMap<>();
    private final Map<String, Integer> passes = new ConcurrentHashMap<>();
    final ToyIndex toys = new ToyIndex();

    /** Counters for {@code /ranch behaviour status}. */
    final Map<String, Integer> started = new ConcurrentHashMap<>();
    final Map<String, Integer> eventsFired = new ConcurrentHashMap<>();
    volatile int lastPassResidents;
    volatile int lastPassReads;
    volatile long lastPassNanos;

    @Nonnull
    Map<UUID, Session> sessions() {
        return sessions;
    }

    void addTestToy(@Nonnull String blockId, @Nonnull String behaviourId) {
        extraToys.put(BehaviourConfig.baseBlockId(blockId), behaviourId);
        toys.invalidate();
    }

    @Nonnull
    Map<String, String> testToys() {
        return Map.copyOf(extraToys);
    }

    @Nullable
    private String toyBehaviour(@Nonnull BehaviourConfig c, @Nonnull String baseId) {
        String t = extraToys.get(baseId);
        return t != null ? t : c.toyBehaviour(baseId);
    }

    /** World thread. */
    void pass(@Nonnull World world) {
        if (!world.isAlive()) return;
        long t0 = System.nanoTime();
        BehaviourConfig c = BehaviourFeature.config();
        Store<EntityStore> store = world.getEntityStore().getStore();
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) return;
        boolean day = BehaviourRules.isDaytime(time.getDayProgress());
        long now = System.currentTimeMillis();
        int pass = passes.merge(world.getName(), 1, Integer::sum);
        if (pass % PERSONALITY_PASSES == 1) rollPersonalities(world);
        if (pass % CLEANUP_PASSES == 0) cleanup();

        List<HouseRecord> houses = Ranch.houses().inWorld(world.getName());
        if (houses.isEmpty() && sessions.isEmpty()) return;
        WorldBlocks blocks = new WorldBlocks(world);
        List<Seen> players = players(world, store);
        double dayLength = (double) world.getDaytimeDurationSeconds() + world.getNighttimeDurationSeconds();
        double range = RanchConfig.get().homeRangeRadius();

        boolean scanned = false;
        int residents = 0;
        Set<UUID> visited = new java.util.HashSet<>();
        for (HouseRecord house : houses) {
            try {
                if (!scanned && day && toys.due(house.houseId, now, c) && blocks.loaded((int) Math.floor(house.doorX), (int) Math.floor(house.doorY), (int) Math.floor(house.doorZ))) {
                    toys.scan(blocks, house, (int) Math.min(c.ToyScanRadius, range), id -> toyBehaviour(c, id), now);
                    scanned = true;
                }
                List<AnimalRecord> inHouse = Ranch.animals().inHouse(house.houseId);
                for (AnimalRecord r : inHouse) {
                    residents++;
                    visited.add(r.animalId);
                    step(world, store, blocks, c, house, r, inHouse, players, day, now, dayLength, range);
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Behaviour pass failed for house " + house.houseId, e);
            }
        }
        if (sessions.size() > 0) dropOrphans(world, store, visited);
        lastPassResidents = residents;
        lastPassReads = blocks.reads;
        lastPassNanos = System.nanoTime() - t0;
    }

    /**
     * A session whose animal was not visited this pass (it left its house, changed world, or its house went):
     * the animal stops where it is, and goes back to Idle if it is still in one of our states.
     */
    private void dropOrphans(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Set<UUID> visited) {
        for (Map.Entry<UUID, Session> e : new ArrayList<>(sessions.entrySet())) {
            if (visited.contains(e.getKey())) continue;
            AnimalRecord r = Ranch.animals().get(e.getKey()).orElse(null);
            if (r != null && !world.getName().equals(r.worldName)) continue;
            sessions.remove(e.getKey());
            Ref<EntityStore> ref = r != null ? live(world, r) : null;
            if (ref == null) continue;
            Session s = e.getValue();
            if (s.clip != null && s.acting) play(store, ref, null, false);
            StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
            if (states != null && BehaviourRules.isOwnState(baseState(states.getStateName()))) toIdle(store, ref, position(store, ref), s.target != null);
            debug("Ranch animal " + e.getKey() + " stops " + s.id + " (no longer a resident here)");
        }
    }

    private void step(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull WorldBlocks blocks, @Nonnull BehaviourConfig c,
                      @Nonnull HouseRecord house, @Nonnull AnimalRecord r, @Nonnull List<AnimalRecord> inHouse, @Nonnull List<Seen> players,
                      boolean day, long now, double dayLength, double range) {
        Ref<EntityStore> ref = live(world, r);
        if (ref == null) {
            sessions.remove(r.animalId);
            return;
        }
        Snapshot snap = snapshot(store, ref, r, house, day, now, range);
        Session s = sessions.get(r.animalId);
        if (s != null) {
            Verdict v = BehaviourRules.canContinue(snap);
            if (v != Verdict.OK) {
                stop(store, ref, house, s, v != Verdict.ROLE_CHANGING && v != Verdict.NOT_RANCH_ROLE);
                sessions.remove(r.animalId);
                debug("Ranch animal " + r.animalId + " stops " + s.id + " (" + v + ")");
                return;
            }
            advance(world, store, c, house, r, ref, s, players, now);
            return;
        }
        if (BehaviourRules.isOwnState(snap.state()) && snap.ranchRole() && !snap.roleChanging()) {
            backToIdle(store, ref, house, true);
            return;
        }
        if (!day) return;
        if (BehaviourRules.canStart(snap, c) != Verdict.OK) return;

        if ("shy".equals(r.personality) && shyReaction(store, c, house, r, ref, players, now, range)) return;

        Long due = nextDecision.get(r.animalId);
        if (due == null) {
            nextDecision.put(r.animalId, now + (long) (BehaviourRules.decisionSeconds(c, r.personality, rand()) * 1000 * rand()));
            return;
        }
        if (now < due) return;
        nextDecision.put(r.animalId, now + (long) (BehaviourRules.decisionSeconds(c, r.personality, rand()) * 1000));
        Context ctx = context(world, store, blocks, c, house, r, ref, inHouse, players);
        List<Option> options = BehaviourRules.options(ctx, c, id -> coolingDown(c, r.animalId, id, now, dayLength));
        Option pick = BehaviourRules.pick(options, BehaviourRules.idleWeight(c, r.personality), rand());
        if (pick != null) start(world, store, c, house, r, ref, pick.id(), pick.animation(), inHouse, players, now, range);
    }

    @Nonnull
    private Snapshot snapshot(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull AnimalRecord r, @Nonnull HouseRecord house,
                              boolean day, long now, double range) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        String role = npc != null ? npc.getRoleName() : null;
        boolean ranch = role != null && SpeciesRegistry.byRole(role).map(m -> m.kind() == RoleKind.RANCH || m.kind() == RoleKind.RANCH_BABY).orElse(false)
            && CompatGuard.isManageable(ref, store, role);
        boolean changing = npc == null || npc.getRole() == null || npc.getRole().isRoleChangeRequested();
        String state = states != null ? baseState(states.getStateName()) : "";
        double d = t != null ? horizontal(t.getPosition(), house.doorX, house.doorZ) : Double.MAX_VALUE;
        long touched = Math.max(r.lastPettedAtMs, 0);
        return new Snapshot(r.ownerId != null, r.houseId != null, r.state == AnimalRecord.State.ACTIVE, r.following, state, changing,
            ranch && states != null && t != null, day, d, range, (now - touched) / 1000.0);
    }

    @Nonnull
    private Context context(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull WorldBlocks blocks, @Nonnull BehaviourConfig c,
                            @Nonnull HouseRecord house, @Nonnull AnimalRecord r, @Nonnull Ref<EntityStore> ref, @Nonnull List<AnimalRecord> inHouse,
                            @Nonnull List<Seen> players) {
        Vector3d p = position(store, ref);
        Map<String, List<String>> can = c.behavioursOf(r.speciesId, r.baby);
        Set<String> ground = Set.of();
        boolean shaded = false;
        if (needsGround(c, can)) ground = BehaviourRules.groundTags(blocks.sample(p.x, p.y, p.z), c);
        if (can.containsKey("nap")) shaded = blocks.shaded(p.x, p.y, p.z, c.ShadeHeight);
        boolean nearHouse = horizontal(p, house.doorX, house.doorZ) <= c.NearHouseDistance;
        boolean trough = hasKind(c, can, Kind.TROUGH) && !CareApi.troughsNear(world.getName(), house.doorX, house.doorY, house.doorZ, CareApi.troughRange()).isEmpty();
        Set<String> toysNear = new LinkedHashSet<>();
        for (ToyIndex.Toy toy : toys.toys(house.houseId)) if (can.containsKey(toy.behaviourId())) toysNear.add(toy.behaviourId());
        boolean parent = r.baby && can.containsKey("follow_parent") && parentOf(world, store, r, p, inHouse) != null;
        boolean partner = hasKind(c, can, Kind.SOCIAL) && partnerFor(world, store, c, r, p, inHouse) != null;
        boolean playerNear = nearestPlayer(players, p, c.PlayerRange, null) != null;
        boolean ownerNear = r.ownerId != null && nearestPlayer(players, p, c.PlayerRange, r.ownerId) != null;
        return new Context(r.speciesId, r.baby, r.personality, animations(store, ref), ground, shaded, nearHouse, trough, toysNear, parent, partner, playerNear, ownerNear);
    }

    private static boolean needsGround(@Nonnull BehaviourConfig c, @Nonnull Map<String, List<String>> can) {
        for (String id : can.keySet()) {
            Def d = c.def(id);
            if (d != null && d.kind() == Kind.HERE && d.Ground != null && !d.Ground.isEmpty()) return true;
        }
        return false;
    }

    private static boolean hasKind(@Nonnull BehaviourConfig c, @Nonnull Map<String, List<String>> can, @Nonnull Kind kind) {
        for (String id : can.keySet()) {
            Def d = c.def(id);
            if (d != null && d.kind() == kind) return true;
        }
        return false;
    }

    /**
     * Starts a behaviour on an idle resident. Returns null on success, or why it could not start (for
     * {@code /ranch behaviour force}).
     */
    @Nullable
    String start(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull BehaviourConfig c, @Nonnull HouseRecord house,
                 @Nonnull AnimalRecord r, @Nonnull Ref<EntityStore> ref, @Nonnull String id, @Nullable String clip,
                 @Nonnull List<AnimalRecord> inHouse, @Nonnull List<Seen> players, long now, double range) {
        Def def = c.def(id);
        if (def == null) return "unknown";
        Vector3d p = position(store, ref);
        double seconds = BehaviourRules.durationSeconds(def, rand());
        Session s;
        switch (def.kind()) {
            case HERE -> {
                s = new Session(id, def, clip, BehaviourRules.STATE_BEHAVE, now, seconds);
                s.spot = new Vector3d(p);
            }
            case REST -> {
                s = new Session(id, def, clip, BehaviourRules.STATE_BEHAVE, now, seconds);
                WorldBlocks blocks = new WorldBlocks(world);
                s.spot = blocks.shaded(p.x, p.y, p.z, c.ShadeHeight) ? new Vector3d(p) : besideDoor(house);
            }
            case TROUGH -> {
                CareApi.Trough best = null;
                double bestD = Double.MAX_VALUE;
                for (CareApi.Trough t : CareApi.troughsNear(world.getName(), house.doorX, house.doorY, house.doorZ, CareApi.troughRange())) {
                    double d = horizontal(p, t.x() + 0.5, t.z() + 0.5);
                    if (d < bestD) {
                        bestD = d;
                        best = t;
                    }
                }
                if (best == null) return "no_trough";
                s = new Session(id, def, clip, BehaviourRules.STATE_BEHAVE, now, seconds);
                s.spot = beside(p, best.x() + 0.5, best.y(), best.z() + 0.5, TROUGH_GAP);
            }
            case TOY -> {
                ToyIndex.Toy toy = toys.nearest(house.houseId, id, p.x, p.z);
                if (toy == null) return "no_toy";
                s = new Session(id, def, clip, BehaviourRules.STATE_BEHAVE, now, seconds);
                s.spot = beside(p, toy.x() + 0.5, toy.y(), toy.z() + 0.5, 1.1);
            }
            case PARENT -> {
                Ref<EntityStore> parent = parentOf(world, store, r, p, inHouse);
                if (parent == null) return "no_parent";
                s = new Session(id, def, clip, BehaviourRules.STATE_TAG, now, seconds);
                s.target = parent;
            }
            case SOCIAL -> {
                AnimalRecord other = partnerFor(world, store, c, r, p, inHouse);
                Ref<EntityStore> otherRef = other != null ? live(world, other) : null;
                if (other == null || otherRef == null) return "no_partner";
                s = new Session(id, def, clip, BehaviourRules.STATE_MEET, now, seconds);
                s.target = otherRef;
                s.partnerId = other.animalId;
                s.arriveDistance = 2.6;
                String otherClip = BehaviourRules.animationFor(c.behavioursOf(other.speciesId, other.baby).getOrDefault(id, List.of()), animations(store, otherRef));
                Session o = new Session(id, def, otherClip, BehaviourRules.STATE_MEET, now, seconds);
                o.target = ref;
                o.partnerId = r.animalId;
                o.arriveDistance = 2.6;
                begin(store, otherRef, other, o, now);
            }
            case PLAYER, OWNER -> {
                Seen who = nearestPlayer(players, p, c.PlayerRange, def.kind() == Kind.OWNER ? r.ownerId : null);
                if (who == null) return "no_player";
                s = new Session(id, def, clip, BehaviourRules.STATE_TAG, now, seconds);
                s.target = who.ref();
                s.arriveDistance = 3.5;
            }
            case RETREAT -> {
                return "reaction";
            }
            default -> {
                return "unknown";
            }
        }
        if (s.spot != null && horizontal(s.spot, house.doorX, house.doorZ) > range) return "spot_out_of_range";
        begin(store, ref, r, s, now);
        return null;
    }

    /** Hands the movement to the role and remembers the session. Starts acting at once for in-place kinds. */
    private void begin(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull AnimalRecord r, @Nonnull Session s, long now) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        if (npc == null || states == null) return;
        if (s.spot != null) npc.setLeashPoint(s.spot);
        if (s.target != null) {
            MarkedEntitySupport marked = store.getComponent(ref, MarkedEntitySupport.getComponentType());
            if (marked != null) marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, s.target);
        }
        states.setState(ref, s.state, null, store);
        sessions.put(r.animalId, s);
        lastStart.computeIfAbsent(r.animalId, k -> new ConcurrentHashMap<>()).put(s.id, now);
        started.merge(s.id, 1, Integer::sum);
        debug("Ranch animal " + r.animalId + " (" + r.speciesId + (r.baby ? " baby" : "") + ", " + r.personality + ") starts " + s.id
            + (s.clip != null ? " with " + s.clip : "") + (s.spot != null ? String.format(java.util.Locale.ROOT, " at %.1f %.1f %.1f", s.spot.x, s.spot.y, s.spot.z) : ""));
        if (s.kind == Kind.HERE || s.kind == Kind.PARENT) act(store, ref, r, s, now);
    }

    private void advance(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull BehaviourConfig c, @Nonnull HouseRecord house,
                         @Nonnull AnimalRecord r, @Nonnull Ref<EntityStore> ref, @Nonnull Session s, @Nonnull List<Seen> players, long now) {
        if (s.target != null && !s.target.isValid()) {
            stop(store, ref, house, s, true);
            sessions.remove(r.animalId);
            return;
        }
        if (s.partnerId != null && !sessions.containsKey(s.partnerId)) {
            stop(store, ref, house, s, true);
            sessions.remove(r.animalId);
            return;
        }
        if (!s.acting) {
            double d = distanceToGoal(store, ref, s);
            double arrive = s.arriveDistance > 0 ? s.arriveDistance : c.ArriveDistance;
            if (now >= s.nextTraceMs && BehaviourFeature.logging()) {
                s.nextTraceMs = now + 3000;
                Vector3d p = position(store, ref);
                StateSupport st = store.getComponent(ref, StateSupport.getComponentType());
                debug(String.format(java.util.Locale.ROOT, "Ranch animal %s walking to %s: %.1f to go, at %.1f %.1f %.1f, state %s",
                    r.animalId, s.id, d, p.x, p.y, p.z, st != null ? st.getStateName() : "?"));
            }
            if (d <= arrive) {
                act(store, ref, r, s, now);
            } else if (now - s.startedMs > (long) (c.MaxWalkSeconds * 1000)) {
                if (d <= Math.max(c.ActAnywayDistance, arrive)) {
                    act(store, ref, r, s, now);
                } else {
                    debug("Ranch animal " + r.animalId + " gives up " + s.id + String.format(java.util.Locale.ROOT, " (%.1f blocks short)", d));
                    stop(store, ref, house, s, true);
                    sessions.remove(r.animalId);
                }
            }
            return;
        }
        if (now >= s.actUntilMs) {
            stop(store, ref, house, s, true);
            sessions.remove(r.animalId);
            return;
        }
        if (s.clip != null && s.def.RepeatSeconds > 0 && now >= s.nextRepeatMs) {
            play(store, ref, s.clip, true);
            s.nextRepeatMs = now + (long) (s.def.RepeatSeconds * 1000);
        }
    }

    /** Arrived (or in place): the clip, the sound, the event. */
    private void act(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull AnimalRecord r, @Nonnull Session s, long now) {
        s.acting = true;
        s.actUntilMs = now + (long) (s.seconds * 1000);
        s.nextRepeatMs = now + (long) (Math.max(1, s.def.RepeatSeconds) * 1000);
        if (s.kind == Kind.HERE || s.kind == Kind.REST || s.kind == Kind.TROUGH || s.kind == Kind.TOY || s.kind == Kind.RETREAT) {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) npc.setLeashPoint(position(store, ref));
        }
        if (s.clip != null) play(store, ref, s.clip, true);
        Vector3d p = position(store, ref);
        if (s.kind == Kind.TOY) RanchSounds.play(RanchSounds.TOY_PLAY, p.x, p.y, p.z, store);
        if (s.kind == Kind.OWNER) ParticleUtil.spawnParticleEffect(HEARTS_SMALL, new Vector3d(p).add(0, 1.0, 0), store);
        if (s.def.Event) fireEvent(store, r, s.id, p, now);
    }

    private void fireEvent(@Nonnull Store<EntityStore> store, @Nonnull AnimalRecord r, @Nonnull String id, @Nonnull Vector3d p, long now) {
        BehaviourConfig c = BehaviourFeature.config();
        Map<String, Long> mine = lastEvent.computeIfAbsent(r.animalId, k -> new ConcurrentHashMap<>());
        if (!BehaviourRules.eventAllowed(mine.get(id), c, now)) return;
        mine.put(id, now);
        UUID witness = null;
        World world = store.getExternalData().getWorld();
        Seen near = nearestPlayer(players(world, store), p, c.WitnessRange, null);
        if (near != null) witness = near.uuid();
        RanchEvents.fire(new AnimalBehaviourEvent(r.view(), id, witness));
        eventsFired.merge(id, 1, Integer::sum);
        debug("Ranch animal " + r.animalId + " fired AnimalBehaviourEvent " + id + " (witness " + witness + ")");
    }

    /**
     * Ends a session. The clip slot is cleared; the animal goes back to Idle with its leash at the door only if it
     * is still in one of our states (someone else's state is left alone).
     */
    private void stop(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull HouseRecord house, @Nonnull Session s, boolean toIdle) {
        if (s.clip != null && s.acting) play(store, ref, null, false);
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        boolean ours = states != null && BehaviourRules.isOwnState(baseState(states.getStateName()));
        if (toIdle && ours) backToIdle(store, ref, house, s.target != null);
    }

    /** Idle again, wandering around the door (the house's leash point). */
    private static void backToIdle(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull HouseRecord house, boolean clearTarget) {
        toIdle(store, ref, new Vector3d(house.doorX, house.doorY, house.doorZ), clearTarget);
    }

    private static void toIdle(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull Vector3d leash, boolean clearTarget) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        if (npc == null || states == null || npc.getRole() == null || npc.getRole().isRoleChangeRequested()) return;
        if (clearTarget) {
            MarkedEntitySupport marked = store.getComponent(ref, MarkedEntitySupport.getComponentType());
            if (marked != null) marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, null);
        }
        npc.setLeashPoint(leash);
        states.setState(ref, BehaviourRules.STATE_IDLE, null, store);
    }

    /** A shy resident steps away from a player who is not its owner. True if it reacted. */
    private boolean shyReaction(@Nonnull Store<EntityStore> store, @Nonnull BehaviourConfig c, @Nonnull HouseRecord house, @Nonnull AnimalRecord r,
                                @Nonnull Ref<EntityStore> ref, @Nonnull List<Seen> players, long now, double range) {
        Long last = lastShy.get(r.animalId);
        if (last != null && now - last < (long) (c.ShyCooldownSeconds * 1000)) return false;
        Vector3d p = position(store, ref);
        Seen stranger = null;
        double best = c.ShyDistance;
        for (Seen s : players) {
            if (s.uuid().equals(r.ownerId)) continue;
            double d = p.distance(s.pos());
            if (d < best) {
                best = d;
                stranger = s;
            }
        }
        if (stranger == null) return false;
        Def def = BehaviourFeature.config().def("retreat");
        if (def == null || !c.behavioursOf(r.speciesId, r.baby).containsKey("retreat")) return false;
        double dx = p.x - stranger.pos().x, dz = p.z - stranger.pos().z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            double a = rand() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
            len = 1;
        }
        Vector3d spot = new Vector3d(p.x + dx / len * (c.ShyDistance - best + 2), p.y, p.z + dz / len * (c.ShyDistance - best + 2));
        double fromDoor = horizontal(spot, house.doorX, house.doorZ);
        double max = range * 0.8;
        if (fromDoor > max) {
            double fx = spot.x - house.doorX, fz = spot.z - house.doorZ;
            spot.x = house.doorX + fx / fromDoor * max;
            spot.z = house.doorZ + fz / fromDoor * max;
        }
        lastShy.put(r.animalId, now);
        Session s = new Session("retreat", def, null, BehaviourRules.STATE_BEHAVE, now, BehaviourRules.durationSeconds(def, rand()));
        s.spot = spot;
        begin(store, ref, r, s, now);
        return true;
    }

    /** The baby's parent if it is out nearby, else the nearest adult of its species living in the same house. */
    @Nullable
    private Ref<EntityStore> parentOf(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull AnimalRecord r, @Nonnull Vector3d p,
                                      @Nonnull List<AnimalRecord> inHouse) {
        for (UUID id : new UUID[] {r.parentA, r.parentB}) {
            if (id == null) continue;
            AnimalRecord parent = Ranch.animals().get(id).orElse(null);
            Ref<EntityStore> ref = parent != null ? usableCompanion(world, store, parent, p, 16) : null;
            if (ref != null) return ref;
        }
        Ref<EntityStore> best = null;
        double bestD = Double.MAX_VALUE;
        for (AnimalRecord o : inHouse) {
            if (o.baby || o.animalId.equals(r.animalId) || !o.speciesId.equals(r.speciesId)) continue;
            Ref<EntityStore> ref = usableCompanion(world, store, o, p, 16);
            if (ref == null) continue;
            double d = position(store, ref).distance(p);
            if (d < bestD) {
                bestD = d;
                best = ref;
            }
        }
        return best;
    }

    /** Another resident of the house, idle, not busy, close enough to meet. */
    @Nullable
    private AnimalRecord partnerFor(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull BehaviourConfig c, @Nonnull AnimalRecord r,
                                    @Nonnull Vector3d p, @Nonnull List<AnimalRecord> inHouse) {
        AnimalRecord best = null;
        double bestD = Double.MAX_VALUE;
        for (AnimalRecord o : inHouse) {
            if (o.animalId.equals(r.animalId) || sessions.containsKey(o.animalId) || o.following) continue;
            if (!c.behavioursOf(o.speciesId, o.baby).containsKey("socialise")) continue;
            Ref<EntityStore> ref = usableCompanion(world, store, o, p, c.SocialRange);
            if (ref == null) continue;
            StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
            if (states == null || !BehaviourRules.STATE_IDLE.equals(baseState(states.getStateName()))) continue;
            double d = position(store, ref).distance(p);
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        return best;
    }

    /** A loaded, active, not-going-home ranch animal within {@code within} blocks, or null. */
    @Nullable
    private static Ref<EntityStore> usableCompanion(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull AnimalRecord o,
                                                    @Nonnull Vector3d p, double within) {
        if (o.state != AnimalRecord.State.ACTIVE || !world.getName().equals(o.worldName)) return null;
        Ref<EntityStore> ref = live(world, o);
        if (ref == null) return null;
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        if (states == null || "GoHome".equals(baseState(states.getStateName()))) return null;
        return position(store, ref).distance(p) <= within ? ref : null;
    }

    private boolean coolingDown(@Nonnull BehaviourConfig c, @Nonnull UUID animalId, @Nonnull String id, long now, double dayLength) {
        Def def = c.def(id);
        if (def == null) return true;
        Map<String, Long> mine = lastStart.get(animalId);
        return BehaviourRules.coolingDown(mine == null ? null : mine.get(id), BehaviourRules.cooldownMs(def, dayLength), now);
    }

    /** Rolls a personality for every owned record in this world that has none, and saves it through the registry. */
    private static void rollPersonalities(@Nonnull World world) {
        for (AnimalRecord r : Ranch.animals().inWorld(world.getName())) {
            if (r.ownerId == null || r.personality != null) continue;
            r.personality = BehaviourRules.rollPersonality(rand());
            Ranch.animals().markDirty(r);
            LOG.info("Ranch animal " + r.animalId + " (" + r.speciesId + ") has personality " + r.personality);
        }
    }

    /** Forgets cooldowns and decisions of animals that no longer have a record. */
    private void cleanup() {
        for (Map<UUID, ?> m : List.<Map<UUID, ?>>of(nextDecision, lastStart, lastEvent, lastShy)) {
            m.keySet().removeIf(id -> Ranch.animals().get(id).isEmpty());
        }
    }

    // ---- small helpers ----

    @Nonnull
    private static List<Seen> players(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        List<Seen> out = new ArrayList<>();
        for (PlayerRef pr : world.getPlayerRefs()) {
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) continue;
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t != null) out.add(new Seen(pr.getUuid(), ref, new Vector3d(t.getPosition())));
        }
        return out;
    }

    @Nullable
    private static Seen nearestPlayer(@Nonnull List<Seen> players, @Nonnull Vector3d p, double within, @Nullable UUID only) {
        Seen best = null;
        double bestD = within;
        for (Seen s : players) {
            if (only != null && !only.equals(s.uuid())) continue;
            double d = s.pos().distance(p);
            if (d <= bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    private static double distanceToGoal(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull Session s) {
        Vector3d p = position(store, ref);
        if (s.target != null) return position(store, s.target).distance(p);
        if (s.spot != null) {
            double h = horizontal(p, s.spot.x, s.spot.z), dy = Math.abs(p.y - s.spot.y);
            return dy > 2.5 ? Math.sqrt(h * h + dy * dy) : h;
        }
        return 0;
    }

    /** A point {@code gap} blocks from a block centre, on the side the animal comes from. */
    @Nonnull
    private static Vector3d beside(@Nonnull Vector3d from, double cx, double y, double cz, double gap) {
        double dx = from.x - cx, dz = from.z - cz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            double a = rand() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
            len = 1;
        }
        return new Vector3d(cx + dx / len * gap, y, cz + dz / len * gap);
    }

    /** A spot in front of the house: out from the door, away from the house, a little to one side. */
    @Nonnull
    private static Vector3d besideDoor(@Nonnull HouseRecord house) {
        double ox = house.doorX - (house.x + 0.5), oz = house.doorZ - (house.z + 0.5);
        double len = Math.sqrt(ox * ox + oz * oz);
        if (len < 0.01) {
            ox = 0;
            oz = 1;
            len = 1;
        }
        ox /= len;
        oz /= len;
        double out = 1.0 + rand() * 1.5, side = (rand() - 0.5) * 3.0;
        return new Vector3d(house.doorX + ox * out - oz * side, house.doorY, house.doorZ + oz * out + ox * side);
    }

    @Nonnull
    static Set<String> animations(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ModelComponent mc = store.getComponent(ref, ModelComponent.getComponentType());
        Model model = mc != null ? mc.getModel() : null;
        return model != null ? model.getAnimationSetMap().keySet() : Set.of();
    }

    private static void play(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nullable String clip, boolean force) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return;
        if (clip != null && !animations(store, ref).contains(clip)) return;
        npc.playAnimation(ref, AnimationSlot.Emote, clip, force, store);
    }

    @Nonnull
    static Vector3d position(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        return t != null ? new Vector3d(t.getPosition()) : new Vector3d();
    }

    @Nullable
    static Ref<EntityStore> live(@Nonnull World world, @Nonnull AnimalRecord r) {
        if (r.entityUuid == null) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(r.entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    @Nonnull
    static String baseState(@Nonnull String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static double horizontal(@Nonnull Vector3d p, double x, double z) {
        double dx = p.x - x, dz = p.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double rand() {
        return ThreadLocalRandom.current().nextDouble();
    }

    static void debug(@Nonnull String msg) {
        if (BehaviourFeature.logging()) LOG.info(msg);
    }

    /**
     * {@code /ranch behaviour force}: starts a behaviour now, skipping the decision timer, weights, cooldowns and the
     * ground check, but never the rules that protect the animal (owned, housed, idle, by day, in range).
     * World thread. Returns null on success or the reason it did not start.
     */
    @Nullable
    String force(@Nonnull World world, @Nonnull AnimalRecord r, @Nonnull String id) {
        BehaviourConfig c = BehaviourFeature.config();
        Def def = c.def(id);
        if (def == null) return "unknown";
        List<String> clips = c.behavioursOf(r.speciesId, r.baby).get(id);
        if (clips == null) return "species";
        HouseRecord house = r.houseId != null ? Ranch.houses().get(r.houseId).orElse(null) : null;
        if (house == null) return "not_resident";
        Ref<EntityStore> ref = live(world, r);
        if (ref == null) return "not_loaded";
        Store<EntityStore> store = world.getEntityStore().getStore();
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        boolean day = time != null && BehaviourRules.isDaytime(time.getDayProgress());
        long now = System.currentTimeMillis();
        double range = RanchConfig.get().homeRangeRadius();
        Session running = sessions.remove(r.animalId);
        if (running != null) stop(store, ref, house, running, true);
        Snapshot snap = snapshot(store, ref, r, house, day, now, range);
        Verdict v = BehaviourRules.canStart(snap, c);
        if (v != Verdict.OK && v != Verdict.RECENT_INTERACTION && !(running != null && v == Verdict.NOT_IDLE)) return "verdict." + v.name().toLowerCase(java.util.Locale.ROOT);
        String clip = BehaviourRules.animationFor(clips, animations(store, ref));
        List<AnimalRecord> inHouse = Ranch.animals().inHouse(house.houseId);
        return start(world, store, c, house, r, ref, id, clip, inHouse, players(world, store), now, range);
    }

    /** For the status command: per-animal start times. */
    @Nonnull
    Map<String, Long> starts(@Nonnull UUID animalId) {
        Map<String, Long> m = lastStart.get(animalId);
        return m == null ? Map.of() : new HashMap<>(m);
    }

    long nextDecisionAt(@Nonnull UUID animalId) {
        return nextDecision.getOrDefault(animalId, 0L);
    }

    /** Stops every session (on reload) and lets the animals idle again. World thread of each animal. */
    void stopAll(@Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Map.Entry<UUID, Session> e : new ArrayList<>(sessions.entrySet())) {
            AnimalRecord r = Ranch.animals().get(e.getKey()).orElse(null);
            if (r == null || !world.getName().equals(r.worldName) || r.houseId == null) continue;
            HouseRecord house = Ranch.houses().get(r.houseId).orElse(null);
            Ref<EntityStore> ref = live(world, r);
            if (house != null && ref != null) stop(store, ref, house, e.getValue(), true);
            sessions.remove(e.getKey());
        }
    }
}
