package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.predator.CalmBook.Entry;
import com.hexvane.cozytalesranching.predator.CalmRules.Phase;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Once per world tick, before the NPC behaviour tick, for the few predators in the {@link CalmBook} of this world only
 * (no entity scan): keeps each one in a state its phase allows (a calm one never sits in Combat, Alerted or Search; a
 * spooked one flees instead of fighting) and notices a lured one reaching its bowl. Field writes only, no store writes;
 * anything that changes blocks or components is queued with {@link World#execute}.
 *
 * <p>Slower passes: every 0.25 s a sprinting player bumping a calm predator startles it; every second timers end;
 * every 2 s each baited bowl of this world whose chunk is loaded looks for a matching wild predator within
 * {@link CalmRules#LURE_RADIUS} blocks through the engine's NPC spatial index (a k-d tree query, not a scan).
 */
final class PredatorSystem extends TickingSystem<EntityStore> {
    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER = com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    private final Set<Dependency<EntityStore>> dependencies = Set.of(new SystemDependency<>(Order.BEFORE, RoleSystems.BehaviourTickSystem.class));
    /** Per world: every world ticks this one instance on its own thread. */
    private final PassClocks clocks = new PassClocks();

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        Predators p = Predators.get();
        if (p == null) return;
        World world = store.getExternalData().getWorld();
        PassClocks.Clock clock = clocks.of(world.getName());
        clock.advance(dt);
        if (clock.takeLure()) lurePass(p, world, store);
        if (p.book.isEmpty()) return;
        List<Entry> here = p.book.inWorld(world.getName());
        if (here.isEmpty()) return;
        boolean sprintCheck = clock.takeSprint();
        boolean timers = clock.takeTimer();
        long now = System.currentTimeMillis();
        EntityStore entities = store.getExternalData();
        for (Entry e : here) {
            Ref<EntityStore> ref = entities.getRefFromUUID(e.entity);
            if (ref == null || !ref.isValid()) {
                // unloaded: the timer keeps running; it is dropped when it runs out
                if (timers && CalmRules.expired(e.untilMs, now)) world.execute(() -> p.end(world, e.entity, false));
                continue;
            }
            guard(p, world, store, ref, e, now);
            if (sprintCheck && e.phase != Phase.SPOOKED) sprintCheck(p, world, store, ref, e);
            if (timers) timers(p, world, store, ref, e, now);
        }
    }

    private static void guard(@Nonnull Predators p, @Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull Entry e, long now) {
        StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
        if (states == null) return;
        String name = states.getStateName();
        int dot = name.indexOf('.');
        String main = dot >= 0 ? name.substring(0, dot) : name;
        if (e.phase == Phase.LURED) {
            if (e.eatQueued) return;
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t != null && CalmRules.closeEnoughToEat(t.getPosition().x - (e.bowlX + 0.5), t.getPosition().z - (e.bowlZ + 0.5))) {
                e.eatQueued = true;
                world.execute(() -> p.eat(world, e, false));
                return;
            }
            if (!CalmRules.stateAllowed(Phase.LURED, main) && !Predators.walkTo(ref, e, store)) {
                e.eatQueued = true;
                world.execute(() -> p.eat(world, e, true));
            }
            return;
        }
        if (CalmRules.stateAllowed(e.phase, main)) return;
        if (e.phase == Phase.CALM) {
            Predators.settle(ref, store);
        } else {
            Ref<EntityStore> from = e.startler == null ? null : store.getExternalData().getRefFromUUID(e.startler);
            Predators.flee(ref, from, store);
        }
    }

    private static void sprintCheck(@Nonnull Predators p, @Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull Entry e) {
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t == null) return;
        Vector3d at = t.getPosition();
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> pr = player.getReference();
            if (pr == null || !pr.isValid()) continue;
            TransformComponent pt = store.getComponent(pr, TransformComponent.getComponentType());
            MovementStatesComponent mc = store.getComponent(pr, MovementStatesComponent.getComponentType());
            if (pt == null || mc == null) continue;
            MovementStates m = mc.getMovementStates();
            if (CalmRules.sprintStartles(e.phase, m.sprinting, pt.getPosition().distance(at))) {
                UUID who = player.getUuid();
                world.execute(() -> p.startle(world, e.entity, who));
                return;
            }
        }
    }

    private static void timers(@Nonnull Predators p, @Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull Entry e, long now) {
        if (CalmRules.expired(e.untilMs, now)) {
            if (e.phase == Phase.LURED) {
                if (e.eatQueued) return;
                TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
                if (t != null && CalmRules.closeEnoughToEatInPlace(t.getPosition().x - (e.bowlX + 0.5), t.getPosition().z - (e.bowlZ + 0.5))) {
                    // the walk stalled (a step or a fence in the way) but it is close: it settles with the bait where it is
                    e.eatQueued = true;
                    world.execute(() -> p.eat(world, e, true));
                } else {
                    p.book.gaveUp(e.entity, now);
                    Predators.settle(ref, store);
                    LOGGER.atInfo().log("Predator %s %s gave up on the bowl at %d %d %d", e.speciesId, e.entity, e.bowlX, e.bowlY, e.bowlZ);
                }
            } else {
                world.execute(() -> p.end(world, e.entity, true));
            }
            return;
        }
        if (e.phase == Phase.LURED && !e.eatQueued) {
            // a walk that stopped short (the path finder gave up part way) is started again from where it stands
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t != null) {
                double x = t.getPosition().x, z = t.getPosition().z;
                boolean stalled = !Double.isNaN(e.lastX) && Math.hypot(x - e.lastX, z - e.lastZ) < 0.3;
                e.lastX = x;
                e.lastZ = z;
                if (stalled && e.kicks >= CalmRules.STALL_KICKS
                    && CalmRules.closeEnoughToEatInPlace(x - (e.bowlX + 0.5), z - (e.bowlZ + 0.5))) {
                    // the walk will not get closer (the vanilla path finder stops short on rough ground): eat here
                    e.eatQueued = true;
                    world.execute(() -> p.eat(world, e, true));
                } else if (stalled) {
                    e.kicks++;
                    StateSupport states = store.getComponent(ref, StateSupport.getComponentType());
                    if (states != null) states.setState(ref, "Idle", null, store);
                    Predators.walkTo(ref, e, store);
                }
            }
        }
        SpeciesDefinition species = Predators.species(e).orElse(null);
        if (species != null && CalmRules.warnRestless(e.phase, e.untilMs, now, e.warnedRestless, CalmRules.calmMillis(species))) {
            e.warnedRestless = true;
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t != null) {
                Vector3d at = new Vector3d(t.getPosition());
                Message line = Message.translation("cozyranch_predator.calm.restless").param("animal", Predators.lookName(ref, species, store));
                world.execute(() -> Predators.tellNear(world, at.x, at.y, at.z, line));
            }
        }
    }

    /** Baited bowls in loaded chunks call the nearest matching wild predator within the lure radius. */
    private static void lurePass(@Nonnull Predators p, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        List<BowlStore.Bowl> bowls = p.bowls.inWorld(world.getName());
        if (bowls.isEmpty() || world.getPlayerRefs().isEmpty()) return;
        ChunkStore chunks = world.getChunkStore();
        long now = System.currentTimeMillis();
        List<Ref<EntityStore>> near = new ArrayList<>();
        for (BowlStore.Bowl bowl : bowls) {
            Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(bowl.x(), bowl.y(), bowl.z());
            if (section == null || !section.isValid()) continue;
            if (p.book.bowlTaken(world.getName(), bowl.x(), bowl.y(), bowl.z())) continue;
            Vector3d centre = new Vector3d(bowl.x() + 0.5, bowl.y(), bowl.z() + 0.5);
            near.clear();
            store.getResource(NPCPlugin.get().getNpcSpatialResource()).getSpatialStructure().collect(centre, CalmRules.LURE_RADIUS, near);
            Ref<EntityStore> best = null;
            SpeciesDefinition bestSpecies = null;
            UUID bestId = null;
            double bestD = Double.MAX_VALUE;
            for (Ref<EntityStore> ref : near) {
                if (!ref.isValid()) continue;
                SpeciesDefinition species = Predators.wildPredator(ref, store);
                if (species == null || !CalmRules.isBait(species, bowl.item())) continue;
                UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
                TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
                if (id == null || t == null || !p.book.canLure(id.getUuid(), now)) continue;
                double d = t.getPosition().distance(centre);
                if (d < bestD) {
                    bestD = d;
                    best = ref;
                    bestSpecies = species;
                    bestId = id.getUuid();
                }
            }
            if (best == null) continue;
            Ref<EntityStore> ref = best;
            SpeciesDefinition species = bestSpecies;
            UUID id = bestId;
            world.execute(() -> p.lure(world, ref, id, species, bowl));
        }
    }
}
