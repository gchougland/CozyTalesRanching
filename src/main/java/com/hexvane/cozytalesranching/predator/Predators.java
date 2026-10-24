package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalMarker;
import com.hexvane.cozytalesranching.behaviour.AnimalClips;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.predator.CalmBook.Entry;
import com.hexvane.cozytalesranching.predator.CalmRules.Phase;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleMatch;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * The predator feature's world-side actions: luring a predator to a bowl, eating, Calm, startling, ending. Every method
 * here runs on the world thread outside system ticks (through {@link World#execute}) unless it says otherwise.
 *
 * <p>Engine pieces used, all on the vanilla predator role (Template_Predator), which this mod never edits:
 * <ul>
 * <li>Walking to the bowl: the role's own "investigate a disturbed spot" walk. Its only stored position slot
 * (SteppedBlock, slot 0) is set to the bowl and the state set to {@code Search.Investigate}, which seeks the spot with
 * the pathfinder-free Seek motion and stops about 2 blocks out.</li>
 * <li>Calm: an attitude provider (see {@link PredatorAttitude}) answers IGNORE for players and ranch animals, so the
 * role's detection never picks them; {@link PredatorSystem} keeps it out of Combat, Alerted and Search; and
 * {@link PredatorDamage} cancels any damage it would still deal.</li>
 * <li>Flee: the role's {@code Flee} state with the startling player in its LockedTarget slot.</li>
 * </ul>
 */
public final class Predators {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /**
     * Template_Predator has two stored position slots, SteppedBlock (the investigate walk) and LastSeen (the combat
     * chase's memory). Their indexes are assigned while the role is built and are not exposed by name, so the walk
     * target goes into every slot the role has (at most this many); LastSeen is unused while the predator is lured.
     */
    static final int MAX_POSITION_SLOTS = 4;
    static final String[] EAT_CLIPS = {"Eat", "Track", "SniffAir", "Dig", "Sit"};
    static final double EAT_CLIP_SECONDS = 3.0;
    static final String EAT_PARTICLES = "Hearts_Subtle";

    private static volatile Predators instance;

    final CalmBook book = new CalmBook();
    final BowlStore bowls;

    Predators(@Nonnull BowlStore bowls) {
        this.bowls = bowls;
    }

    static void install(@Nonnull Predators p) {
        instance = p;
    }

    @Nullable
    static Predators get() {
        return instance;
    }

    // ---- which entities are wild predators ----

    /** The species of a wild (not ranch, not owned) predator the mod may manage, or null. Any thread with an accessor. */
    @Nullable
    static SpeciesDefinition wildPredator(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        NPCEntity npc = accessor.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) return null;
        RoleMatch m = SpeciesRegistry.byRole(npc.getRoleName()).orElse(null);
        if (m == null || !m.species().Predator) return null;
        if (m.kind() != RoleKind.WILD && m.kind() != RoleKind.WILD_BABY) return null;
        ComponentType<EntityStore, AnimalMarker> marker = AnimalMarker.type();
        if (marker != null && accessor.getComponent(ref, marker) != null) return null;
        if (!RanchConfig.get().isSpeciesEnabled(m.species().Id)) return null;
        if (!CompatGuard.isManageable(ref, accessor, npc.getRoleName())) return null;
        return m.species();
    }

    // ---- luring ----

    /**
     * Sends the predator to the bowl. If its role has no investigate walk, it is calmed where it stands instead (the bait
     * is still eaten). World thread.
     */
    void lure(@Nonnull World world, @Nonnull Ref<EntityStore> ref, @Nonnull UUID id, @Nonnull SpeciesDefinition species, @Nonnull BowlStore.Bowl bowl) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (!ref.isValid() || !book.canLure(id, System.currentTimeMillis())) return;
        if (!bowl.item().equals(bowls.bait(world.getName(), bowl.x(), bowl.y(), bowl.z()))) return;
        BaitBowls.Presence presence = BaitBowls.presence(world, bowl.x(), bowl.y(), bowl.z());
        if (presence == BaitBowls.Presence.GONE) {
            BowlBreakSystem.dropIfGone(this, world, new org.joml.Vector3i(bowl.x(), bowl.y(), bowl.z()));
            return;
        }
        if (presence != BaitBowls.Presence.BOWL) return;
        Entry e = book.lure(id, species.Id, world.getName(), bowl.x(), bowl.y(), bowl.z(), bowl.item(), System.currentTimeMillis());
        if (!walkTo(ref, e, store)) {
            LOGGER.atInfo().log("Predator %s %s cannot walk to the bowl at %d %d %d; it eats where it stands", species.Id, id, bowl.x(), bowl.y(), bowl.z());
            eat(world, e, true);
            return;
        }
        LOGGER.atInfo().log("Predator %s %s lured to the bait bowl at %d %d %d (%s)", species.Id, id, bowl.x(), bowl.y(), bowl.z(), bowl.item());
    }

    /** Points the role's investigate walk at the bowl. False when the role has no such walk. Safe inside a tick (fields only). */
    static boolean walkTo(@Nonnull Ref<EntityStore> ref, @Nonnull Entry e, @Nonnull ComponentAccessor<EntityStore> accessor) {
        StateSupport states = accessor.getComponent(ref, StateSupport.getComponentType());
        MarkedEntitySupport marked = accessor.getComponent(ref, MarkedEntitySupport.getComponentType());
        NPCEntity npc = accessor.getComponent(ref, NPCEntity.getComponentType());
        if (states == null || marked == null || npc == null) return false;
        java.util.List<Vector3d> slots = positionSlots(marked);
        if (slots.isEmpty()) return false;
        // The bowl's own cell is a solid block the path finder cannot end in, so the walk aims at the open cell beside it,
        // on the side the predator comes from.
        double bx = e.bowlX + 0.5, bz = e.bowlZ + 0.5;
        TransformComponent t = accessor.getComponent(ref, TransformComponent.getComponentType());
        double dx = 0, dz = 1;
        if (t != null) {
            double len = Math.hypot(t.getPosition().x - bx, t.getPosition().z - bz);
            if (len > 0.01) {
                dx = (t.getPosition().x - bx) / len;
                dz = (t.getPosition().z - bz) / len;
            }
        }
        for (Vector3d slot : slots) slot.set(bx + dx * 1.2, e.bowlY, bz + dz * 1.2);
        marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, null);
        npc.setLeashPoint(new Vector3d(e.bowlX + 0.5, e.bowlY, e.bowlZ + 0.5));
        states.setState(ref, "Search", "Investigate", accessor);
        return states.getStateName().startsWith("Search");
    }

    /** The role's stored position slots (a role with none has no investigate walk). */
    @Nonnull
    static java.util.List<Vector3d> positionSlots(@Nonnull MarkedEntitySupport marked) {
        java.util.List<Vector3d> out = new java.util.ArrayList<>();
        for (int i = 0; i < MAX_POSITION_SLOTS; i++) {
            Vector3d v;
            try {
                v = marked.getStoredPosition(i);
            } catch (RuntimeException outOfRange) {
                break;
            }
            if (v == null) break;
            out.add(v);
        }
        return out;
    }

    /** The predator reached the bowl (or cannot walk): the bait goes, Calm starts. World thread. */
    void eat(@Nonnull World world, @Nonnull Entry e, boolean inPlace) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(e.entity);
        if (ref == null || !ref.isValid()) {
            book.remove(e.entity);
            return;
        }
        String bait = bowls.bait(world.getName(), e.bowlX, e.bowlY, e.bowlZ);
        if (bait == null || !bait.equals(e.baitItem)) {
            // someone took the bait back first: the predator just gives up
            book.gaveUp(e.entity, System.currentTimeMillis());
            settle(ref, store);
            return;
        }
        bowls.take(world.getName(), e.bowlX, e.bowlY, e.bowlZ);
        BaitBowls.show(world, e.bowlX, e.bowlY, e.bowlZ, null);
        SpeciesDefinition species = SpeciesRegistry.byId(e.speciesId).orElse(null);
        if (species == null) {
            book.remove(e.entity);
            return;
        }
        book.calm(e.entity, e.speciesId, world.getName(), CalmRules.calmUntil(System.currentTimeMillis(), species));
        settle(ref, store);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) npc.setLeashPoint(new Vector3d(e.bowlX + 0.5, e.bowlY, e.bowlZ + 0.5));
        store.ensureComponent(ref, Interactable.getComponentType());
        for (String clip : EAT_CLIPS) {
            if (AnimalClips.has(store, ref, clip)) {
                AnimalClips.playFor(store, ref, clip, EAT_CLIP_SECONDS);
                break;
            }
        }
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t != null) {
            ParticleUtil.spawnParticleEffect(EAT_PARTICLES, new Vector3d(t.getPosition()).add(0, 1.0, 0), store);
            RanchSounds.play(RanchSounds.ANIMAL_HAPPY, t.getPosition().x, t.getPosition().y + 0.5, t.getPosition().z, store);
        }
        tellNear(world, e.bowlX + 0.5, e.bowlY, e.bowlZ + 0.5, Message.translation(inPlace ? "cozyranch_predator.eat.in_place" : "cozyranch_predator.eat.start")
            .param("animal", Message.translation(species.NameKey)));
        LOGGER.atInfo().log("Predator %s %s ate %s at the bowl %d %d %d and is calm for %.1f min%s", species.Id, e.entity, bait, e.bowlX, e.bowlY, e.bowlZ,
            CalmRules.calmMillis(species) / 60000.0, inPlace ? " (calmed in place)" : "");
    }

    /** Back to plain Idle with no target and no stored walk. Safe inside a tick (fields only). */
    static void settle(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        StateSupport states = accessor.getComponent(ref, StateSupport.getComponentType());
        MarkedEntitySupport marked = accessor.getComponent(ref, MarkedEntitySupport.getComponentType());
        if (marked != null) {
            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, null);
            for (Vector3d slot : positionSlots(marked)) slot.set(Vector3dUtil.MIN);
        }
        if (states != null && !states.getStateName().startsWith("Idle")) states.setState(ref, "Idle", null, accessor);
    }

    // ---- startle and end ----

    /** A hit or a sprinting player: Calm ends and it runs from that player, never at them. World thread. */
    void startle(@Nonnull World world, @Nonnull UUID entity, @Nullable UUID player) {
        Entry e = book.get(entity);
        if (e == null || e.phase == Phase.SPOOKED) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(entity);
        book.spook(entity, player, System.currentTimeMillis());
        if (ref == null || !ref.isValid()) return;
        store.removeComponentIfExists(ref, Interactable.getComponentType());
        flee(ref, player == null ? null : world.getEntityStore().getRefFromUUID(player), store);
        SpeciesDefinition species = SpeciesRegistry.byId(e.speciesId).orElse(null);
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (species != null && t != null) {
            tellNear(world, t.getPosition().x, t.getPosition().y, t.getPosition().z, Message.translation("cozyranch_predator.startled").param("animal", Message.translation(species.NameKey)));
        }
        LOGGER.atInfo().log("Predator %s %s startled by %s; it flees", e.speciesId, entity, player);
    }

    /** Runs from the player in the role's Flee state, or panics when nobody is there to run from. Safe inside a tick. */
    static void flee(@Nonnull Ref<EntityStore> ref, @Nullable Ref<EntityStore> from, @Nonnull ComponentAccessor<EntityStore> accessor) {
        StateSupport states = accessor.getComponent(ref, StateSupport.getComponentType());
        MarkedEntitySupport marked = accessor.getComponent(ref, MarkedEntitySupport.getComponentType());
        if (states == null) return;
        if (from != null && from.isValid() && marked != null) {
            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, from);
            states.setState(ref, "Flee", null, accessor);
        } else {
            if (marked != null) marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT, null);
            states.setState(ref, "Panic", null, accessor);
        }
    }

    /** Calm (or a spook, or a lure) is over: it is an ordinary wild animal again. World thread. */
    void end(@Nonnull World world, @Nonnull UUID entity, boolean tellRestless) {
        Entry e = book.remove(entity);
        if (e == null) return;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(entity);
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (wildPredator(ref, store) != null) store.removeComponentIfExists(ref, Interactable.getComponentType());
        if (tellRestless && e.phase == Phase.CALM) {
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            SpeciesDefinition species = SpeciesRegistry.byId(e.speciesId).orElse(null);
            if (t != null && species != null) {
                tellNear(world, t.getPosition().x, t.getPosition().y, t.getPosition().z, Message.translation("cozyranch_predator.calm.over").param("animal", Message.translation(species.NameKey)));
            }
        }
        LOGGER.atInfo().log("Predator %s %s is wild again (%s ended)", e.speciesId, entity, e.phase);
    }

    /** Tamed: forget it without touching the entity (its new ranch role owns its prompt). Any thread. */
    void tamed(@Nonnull UUID entity) {
        book.remove(entity);
    }

    // ---- chat ----

    /** A feedback line to every player within {@link CalmRules#TELL_RADIUS} blocks. World thread. */
    static void tellNear(@Nonnull World world, double x, double y, double z, @Nonnull Message message) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        double r2 = CalmRules.TELL_RADIUS * CalmRules.TELL_RADIUS;
        for (PlayerRef p : world.getPlayerRefs()) {
            Ref<EntityStore> pr = p.getReference();
            if (pr == null || !pr.isValid()) continue;
            TransformComponent t = store.getComponent(pr, TransformComponent.getComponentType());
            if (t == null) continue;
            Vector3d pos = t.getPosition();
            double dx = pos.x - x, dy = pos.y - y, dz = pos.z - z;
            if (dx * dx + dy * dy + dz * dz <= r2) RanchChat.tell(p, message);
        }
    }

    @Nonnull
    static Optional<SpeciesDefinition> species(@Nonnull Entry e) {
        return SpeciesRegistry.byId(e.speciesId);
    }
}
