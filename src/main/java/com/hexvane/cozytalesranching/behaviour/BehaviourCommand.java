package com.hexvane.cozytalesranching.behaviour;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * {@code /ranch behaviour status [--animal=<a>] | force <animal> <behaviourId> | toy <blockId> [--behaviour=<id>] | log <on|off> | gentle <on|off>}:
 * admin tools for the behaviour planner, usable by the headless test player. An animal is {@code nearest}, the
 * start of its animal id, or its name.
 */
public final class BehaviourCommand extends AbstractCommandCollection {
    /** Lang file prefix, for keys built from ids (personality, behaviour and reason ids). */
    private static final String LANG = "cozyranch_behaviour";

    public BehaviourCommand() {
        super("behaviour", "cozyranch_behaviour.commands.behaviour.desc");
        this.requireNoPermission();
        this.addAliases("behavior");
        this.addSubCommand(new Status());
        this.addSubCommand(new Force());
        this.addSubCommand(new Toy());
        this.addSubCommand(new Log());
        this.addSubCommand(new Gentle());
    }

    /**
     * Resolves an animal of this world: {@code nearest}, {@code baby} or {@code adult} (the nearest such resident that
     * is out), the start of its animal id, or its name.
     */
    @Nullable
    static AnimalRecord resolve(@Nonnull World world, @Nonnull Vector3d from, @Nonnull String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        AnimalRecord best = null;
        double bestD = Double.MAX_VALUE;
        for (AnimalRecord r : Ranch.animals().inWorld(world.getName())) {
            if (q.equals("nearest") || q.equals("baby") || q.equals("adult")) {
                if (r.houseId == null || r.state != AnimalRecord.State.ACTIVE) continue;
                if (q.equals("baby") != r.baby && !q.equals("nearest")) continue;
                Ref<EntityStore> ref = BehaviourPlanner.live(world, r);
                if (ref == null) continue;
                double d = BehaviourPlanner.position(world.getEntityStore().getStore(), ref).distance(from);
                if (d < bestD) {
                    bestD = d;
                    best = r;
                }
            } else if (r.animalId.toString().startsWith(q) || (r.name != null && r.name.toLowerCase(Locale.ROOT).equals(q))) {
                return r;
            }
        }
        return best;
    }

    @Nonnull
    private static Vector3d where(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        return t != null ? new Vector3d(t.getPosition()) : new Vector3d();
    }

    @Nonnull
    private static String counts(@Nonnull Map<String, Integer> m) {
        if (m.isEmpty()) return "-";
        StringBuilder b = new StringBuilder();
        new TreeMap<>(m).forEach((k, v) -> b.append(b.isEmpty() ? "" : ", ").append(k).append(' ').append(v));
        return b.toString();
    }

    @Nonnull
    private static Message personality(@Nullable String p) {
        return p == null ? Message.translation("cozyranch_behaviour.personality.none") : Message.translation(LANG + ".personality." + p);
    }

    /** The planner's counters, or one animal's personality, state and what it is doing. */
    static final class Status extends AbstractPlayerCommand {
        private final OptionalArg<String> animalArg;

        Status() {
            super("status", "cozyranch_behaviour.commands.behaviour.status.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.animalArg = withOptionalArg("animal", "cozyranch_behaviour.commands.behaviour.arg.animal", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            BehaviourPlanner planner = BehaviourFeature.planner();
            if (!context.provided(animalArg)) {
                player.sendMessage(Message.translation("cozyranch_behaviour.status.header")
                    .param("residents", planner.lastPassResidents)
                    .param("busy", planner.sessions().size())
                    .param("micros", planner.lastPassNanos / 1000)
                    .param("reads", planner.lastPassReads)
                    .param("gentle", GentleStepSystem.APPLIED.get()));
                player.sendMessage(Message.translation("cozyranch_behaviour.status.started").param("list", counts(planner.started)));
                player.sendMessage(Message.translation("cozyranch_behaviour.status.events")
                    .param("fired", counts(planner.eventsFired)).param("received", counts(BehaviourFeature.RECEIVED)));
                for (HouseRecord h : Ranch.houses().inWorld(world.getName())) {
                    StringBuilder toys = new StringBuilder();
                    for (ToyIndex.Toy t : planner.toys.toys(h.houseId)) {
                        toys.append(toys.isEmpty() ? "" : ", ").append(t.behaviourId()).append(" @ ").append(t.x()).append(' ').append(t.y()).append(' ').append(t.z());
                    }
                    player.sendMessage(Message.translation("cozyranch_behaviour.status.house")
                        .param("species", h.speciesId).param("x", h.x).param("y", h.y).param("z", h.z)
                        .param("toys", toys.isEmpty() ? "-" : toys.toString()));
                }
                return;
            }
            AnimalRecord r = resolve(world, where(store, ref), context.get(animalArg));
            if (r == null) {
                player.sendMessage(Message.translation("cozyranch_behaviour.animal.notFound").param("query", context.get(animalArg)));
                return;
            }
            Ref<EntityStore> animal = BehaviourPlanner.live(world, r);
            StateSupport states = animal != null ? store.getComponent(animal, StateSupport.getComponentType()) : null;
            BehaviourPlanner.Session s = planner.sessions().get(r.animalId);
            long next = planner.nextDecisionAt(r.animalId);
            player.sendMessage(Message.translation("cozyranch_behaviour.status.animal")
                .param("animal", TamingApi.displayName(r))
                .param("id", r.animalId.toString())
                .param("species", r.speciesId)
                .param("age", Message.translation(r.baby ? "cozyranch_behaviour.status.baby" : "cozyranch_behaviour.status.adult"))
                .param("personality", personality(r.personality))
                .param("state", states != null ? states.getStateName() : "-")
                .param("doing", s == null ? Message.translation("cozyranch_behaviour.status.nothing") : Message.translation(LANG + ".name." + s.id))
                .param("next", next <= 0 ? -1 : Math.max(0, (next - System.currentTimeMillis()) / 1000)));
            if (animal != null) location(player, store, ref, animal);
        }

        /** Where it is, how far from you, its leash point and what its LockedTarget holds (follow and tag debugging). */
        private static void location(@Nonnull PlayerRef player, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> me, @Nonnull Ref<EntityStore> animal) {
            Vector3d p = BehaviourPlanner.position(store, animal);
            Vector3d mine = BehaviourPlanner.position(store, me);
            com.hypixel.hytale.server.npc.entities.NPCEntity npc = store.getComponent(animal, com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
            Vector3d leash = npc != null ? npc.getLeashPoint() : new Vector3d();
            com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport marked = store.getComponent(animal,
                com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport.getComponentType());
            Ref<EntityStore> locked = marked != null ? marked.getMarkedEntityRef(com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport.DEFAULT_TARGET_SLOT) : null;
            String target = locked == null ? "none" : locked.equals(me) ? "you" : "other";
            player.sendMessage(Message.translation("cozyranch_behaviour.status.where")
                .param("pos", String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", p.x, p.y, p.z))
                .param("distance", String.format(java.util.Locale.ROOT, "%.1f", p.distance(mine)))
                .param("leash", String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", leash.x, leash.y, leash.z))
                .param("target", target));
        }
    }

    /** Starts a behaviour now (cooldowns, weights and the ground check skipped; the safety rules are not). */
    static final class Force extends AbstractPlayerCommand {
        private final RequiredArg<String> animalArg;
        private final RequiredArg<String> behaviourArg;

        Force() {
            super("force", "cozyranch_behaviour.commands.behaviour.force.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.animalArg = withRequiredArg("animal", "cozyranch_behaviour.commands.behaviour.arg.animal", ArgTypes.STRING);
            this.behaviourArg = withRequiredArg("behaviourId", "cozyranch_behaviour.commands.behaviour.arg.behaviour", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            AnimalRecord r = resolve(world, where(store, ref), context.get(animalArg));
            if (r == null) {
                player.sendMessage(Message.translation("cozyranch_behaviour.animal.notFound").param("query", context.get(animalArg)));
                return;
            }
            String id = context.get(behaviourArg).trim().toLowerCase(Locale.ROOT);
            String why = BehaviourFeature.planner().force(world, r, id);
            if (why == null) {
                player.sendMessage(Message.translation("cozyranch_behaviour.force.done").param("animal", TamingApi.displayName(r)).param("behaviour", Message.translation(LANG + ".name." + id)));
            } else {
                player.sendMessage(Message.translation("cozyranch_behaviour.force.failed").param("animal", TamingApi.displayName(r)).param("behaviour", id)
                    .param("reason", Message.translation(LANG + ".force.reason." + why)));
            }
        }
    }

    /** Treats a block id as a toy until the server stops, so toys can be tested before the toy blocks exist. */
    static final class Toy extends AbstractPlayerCommand {
        private final RequiredArg<String> blockArg;
        private final OptionalArg<String> behaviourArg;

        Toy() {
            super("toy", "cozyranch_behaviour.commands.behaviour.toy.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.blockArg = withRequiredArg("blockId", "cozyranch_behaviour.commands.behaviour.arg.block", ArgTypes.STRING);
            this.behaviourArg = withOptionalArg("behaviour", "cozyranch_behaviour.commands.behaviour.arg.behaviour", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            String block = context.get(blockArg).trim();
            String behaviour = context.provided(behaviourArg) ? context.get(behaviourArg).trim().toLowerCase(Locale.ROOT) : "toy_ball";
            BehaviourConfig.Def def = BehaviourFeature.config().def(behaviour);
            if (def == null || def.kind() != BehaviourConfig.Kind.TOY) {
                player.sendMessage(Message.translation("cozyranch_behaviour.toy.notToy").param("behaviour", behaviour));
                return;
            }
            BehaviourFeature.planner().addTestToy(block, behaviour);
            player.sendMessage(Message.translation("cozyranch_behaviour.toy.done").param("block", BehaviourConfig.baseBlockId(block)).param("behaviour", Message.translation(LANG + ".name." + behaviour)));
        }
    }

    /** Logs every behaviour start, stop and event at INFO until the server stops (or {@code off}). */
    static final class Log extends AbstractPlayerCommand {
        private final RequiredArg<String> onArg;

        Log() {
            super("log", "cozyranch_behaviour.commands.behaviour.log.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.onArg = withRequiredArg("on|off", "cozyranch_behaviour.commands.behaviour.arg.on", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            boolean on = !context.get(onArg).trim().equalsIgnoreCase("off");
            BehaviourFeature.setLogging(on);
            player.sendMessage(Message.translation(on ? "cozyranch_behaviour.log.on" : "cozyranch_behaviour.log.off"));
        }
    }

    /** Test switch: whether ranch animals added from now on keep off vanilla block triggers (water lilies). Default on. */
    static final class Gentle extends AbstractPlayerCommand {
        private final RequiredArg<String> onArg;

        Gentle() {
            super("gentle", "cozyranch_behaviour.commands.behaviour.gentle.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.onArg = withRequiredArg("on|off", "cozyranch_behaviour.commands.behaviour.arg.on", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            boolean on = !context.get(onArg).trim().equalsIgnoreCase("off");
            GentleStepSystem.setEnabled(on);
            player.sendMessage(Message.translation(on ? "cozyranch_behaviour.gentle.on" : "cozyranch_behaviour.gentle.off"));
        }
    }
}
