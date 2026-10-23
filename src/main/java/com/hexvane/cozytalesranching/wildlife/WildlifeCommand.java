package com.hexvane.cozytalesranching.wildlife;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.wildlife.VisitorStore.HouseState;
import com.hexvane.cozytalesranching.wildlife.VisitorStore.Visitor;
import com.hexvane.cozytalesranching.wildlife.WildlifeConfig.SpeciesWildlife;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.Candidate;
import com.hexvane.cozytalesranching.wildlife.WildlifeRules.HouseFacts;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * {@code /ranch wildlife status|spawn|clear|pass}: admin tools for visitors, also usable by the headless test player.
 * A house is {@code near} (the nearest house), its name, or the start of its id.
 */
public final class WildlifeCommand extends AbstractCommandCollection {
    public WildlifeCommand() {
        super("wildlife", "cozyranch_wildlife.commands.wildlife.desc");
        this.requireNoPermission();
        this.addSubCommand(new Status());
        this.addSubCommand(new Spawn());
        this.addSubCommand(new Clear());
        this.addSubCommand(new Pass());
    }

    @Nullable
    static HouseRecord findHouse(@Nonnull String wanted, @Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        List<HouseRecord> houses = Ranch.houses().inWorld(world.getName());
        if (wanted.equalsIgnoreCase("near")) {
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t == null) return null;
            Vector3d p = t.getPosition();
            return houses.stream().min(Comparator.comparingDouble(h -> p.distance(h.x + 0.5, h.y, h.z + 0.5))).orElse(null);
        }
        for (HouseRecord h : houses) if (h.name != null && h.name.equalsIgnoreCase(wanted)) return h;
        String prefix = wanted.toLowerCase(Locale.ROOT);
        for (HouseRecord h : houses) if (h.houseId.toString().startsWith(prefix)) return h;
        return null;
    }

    @Nonnull
    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Houses of this world with their chances, rare conditions and timing; then every visitor here. */
    static final class Status extends AbstractPlayerCommand {
        Status() {
            super("status", "cozyranch_wildlife.commands.wildlife.status.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            WildlifeService service = WildlifeService.get();
            if (service == null) return;
            WildlifeConfig config = service.config();
            long now = System.currentTimeMillis();
            List<Vector3d> players = WildlifeWorld.playerPositions(world, store);
            List<HouseRecord> houses = Ranch.houses().inWorld(world.getName());
            player.sendMessage(Message.translation("cozyranch_wildlife.status.houses").param("count", houses.size()).param("world", world.getName()));
            for (HouseRecord h : houses) {
                HouseState state = service.store().house(h.houseId, world.getName());
                HouseFacts facts = WildlifeWorld.facts(h);
                List<Candidate> candidates = WildlifeRules.candidates(config, facts, state.lastVisitBySpecies, id -> true, now);
                String chances = candidates.isEmpty() ? "-" : candidates.stream()
                    .map(c -> c.speciesId() + " " + num(c.score())).collect(Collectors.joining(", "));
                SpeciesWildlife own = config.species(h.speciesId);
                String rare = own == null || own.Rare == null ? "-"
                    : WildlifeRules.rareConditionsMet(h.speciesId, own.Rare, facts) ? "ready"
                    : String.join(", ", WildlifeRules.rareMissing(own.Rare, facts));
                long next = state.nextVisitAtMs <= 0 ? -1 : Math.max(0, (state.nextVisitAtMs - now) / 1000);
                player.sendMessage(Message.translation("cozyranch_wildlife.status.house")
                    .param("house", WildlifeWorld.houseName(h))
                    .param("id", h.houseId.toString().substring(0, 8))
                    .param("species", h.speciesId)
                    .param("eligible", WildlifeWorld.eligible(world, h, players, config))
                    .param("next", next)
                    .param("chances", chances)
                    .param("rare", rare));
            }
            List<Visitor> visitors = service.store().inWorld(world.getName());
            player.sendMessage(Message.translation("cozyranch_wildlife.status.visitors").param("count", visitors.size()));
            for (Visitor v : visitors) {
                boolean loaded = WildlifeWorld.liveRef(world, v.entityUuid) != null;
                player.sendMessage(Message.translation("cozyranch_wildlife.status.visitor")
                    .param("role", v.roleId)
                    .param("id", v.entityUuid.toString().substring(0, 8))
                    .param("house", v.houseId == null ? "-" : v.houseId.toString().substring(0, 8))
                    .param("rare", v.rare)
                    .param("loaded", loaded)
                    .param("distance", (int) Math.round(WildlifeWorld.distanceToHouse(v)))
                    .param("state", v.leaving() ? "leaving " + (now - v.leavingSinceMs) / 1000 + " s"
                        : "leaves in " + Math.max(0, (v.leaveAtMs - now) / 1000) + " s"));
            }
        }
    }

    /** Brings a visitor to a house now; caps still hold. */
    static final class Spawn extends AbstractPlayerCommand {
        private final RequiredArg<String> house;
        private final FlagArg rare;
        private final OptionalArg<String> species;
        private final OptionalArg<Integer> stay;

        Spawn() {
            super("spawn", "cozyranch_wildlife.commands.wildlife.spawn.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.house = this.withRequiredArg("house", "cozyranch_wildlife.commands.wildlife.spawn.arg.house", ArgTypes.STRING);
            this.rare = this.withFlagArg("rare", "cozyranch_wildlife.commands.wildlife.spawn.arg.rare");
            this.species = this.withOptionalArg("species", "cozyranch_wildlife.commands.wildlife.spawn.arg.species", ArgTypes.STRING);
            this.stay = this.withOptionalArg("stay", "cozyranch_wildlife.commands.wildlife.spawn.arg.stay", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            WildlifeService service = WildlifeService.get();
            if (service == null) return;
            HouseRecord h = findHouse(context.get(house), world, store, ref);
            if (h == null) {
                player.sendMessage(Message.translation("cozyranch_wildlife.spawn.no_house").param("house", context.get(house)));
                return;
            }
            String speciesId = context.provided(species) ? context.get(species).toLowerCase(Locale.ROOT) : h.speciesId;
            long stayMs = context.provided(stay) ? Math.max(0, context.get(stay)) * 1000L : -1;
            boolean wantRare = context.provided(rare) && Boolean.TRUE.equals(context.get(rare));
            WildlifeWorld.Arrival a = WildlifeWorld.bring(world, store, service, h, speciesId, wantRare, stayMs, false, System.currentTimeMillis());
            if (a.visitor() != null) {
                player.sendMessage(Message.translation("cozyranch_wildlife.spawn.ok")
                    .param("role", a.visitor().roleId)
                    .param("house", WildlifeWorld.houseName(h))
                    .param("x", (int) Math.floor(a.visitor().x)).param("y", (int) Math.floor(a.visitor().y)).param("z", (int) Math.floor(a.visitor().z))
                    .param("seconds", (a.visitor().leaveAtMs - a.visitor().arrivedAtMs) / 1000));
            } else {
                player.sendMessage(Message.translation("cozyranch_wildlife.spawn.failed")
                    .param("reason", a.outcome().name()).param("detail", a.detail()));
            }
        }
    }

    /** Removes every visitor of this world that is still ours and forgets them all. */
    static final class Clear extends AbstractPlayerCommand {
        Clear() {
            super("clear", "cozyranch_wildlife.commands.wildlife.clear.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            WildlifeService service = WildlifeService.get();
            if (service == null) return;
            int forgotten = service.store().inWorld(world.getName()).size();
            int removed = WildlifeWorld.clear(world, service);
            player.sendMessage(Message.translation("cozyranch_wildlife.clear.done").param("removed", removed).param("forgotten", forgotten));
        }
    }

    /** Runs this world's pass now, every eligible house treated as due (and chance raised to certain). */
    static final class Pass extends AbstractPlayerCommand {
        Pass() {
            super("pass", "cozyranch_wildlife.commands.wildlife.pass.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            WildlifeService service = WildlifeService.get();
            if (service == null) return;
            int arrived = WildlifeWorld.pass(world, service, System.currentTimeMillis(), true);
            player.sendMessage(Message.translation("cozyranch_wildlife.pass.done").param("arrived", arrived));
        }
    }
}
